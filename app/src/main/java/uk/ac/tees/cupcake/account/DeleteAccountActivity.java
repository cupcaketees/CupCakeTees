package uk.ac.tees.cupcake.account;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import uk.ac.tees.cupcake.R;
import uk.ac.tees.cupcake.login.LoginActivity;

/**
 * Delete Account Activity
 * @author Bradley Hunter <s6263464@live.tees.ac.uk>
 */
public class DeleteAccountActivity extends AppCompatActivity {

    private EditText mPasswordEditText;
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;
    private FirebaseUser mCurrentUser;
    private String mProvider;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delete_account);
        setTitle("Delete My Account");

        mPasswordEditText = findViewById(R.id.delete_account_password_edit_text);
        mAuth = FirebaseAuth.getInstance();
        mCurrentUser = mAuth.getCurrentUser();

        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                if(firebaseAuth.getCurrentUser() == null){
                    Intent intent = new Intent(DeleteAccountActivity.this, LoginActivity.class);
                    startActivity(intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    finish();
                }
            }
        };

        mProvider = mCurrentUser.getProviders().get(0);

        if(mProvider.equalsIgnoreCase("password")){
            mPasswordEditText.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Deletes users
     * - Profile pictures, cover photos from cloud storage todo/ images used on posts from storage.
     * - All stored information associated with the account
     * - all posts made by user ; including all record of likes from each post
     * - Account from all users followers and following colletions.
     * - Deletes user account.
     * On success sends user to login activity, On failure prompts user with appropriate message.
     */
    public void deleteAccount(View view){

        // Reference to current users document.
        DocumentReference documentReference = FirebaseFirestore.getInstance()
                                                               .collection("Users")
                                                               .document(mCurrentUser.getUid());
        AuthCredential credential = null;

        switch(mProvider){
            case "google.com":
                GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
                if(account != null){
                    credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
                }else{
                    //todo
                    return;
                }
                break;

            case "password":
                String userInputCurrentPassword = mPasswordEditText.getText().toString().trim();

                if(TextUtils.isEmpty(userInputCurrentPassword)) {
                    Toast.makeText(DeleteAccountActivity.this, "You must enter your current password.", Toast.LENGTH_SHORT).show();
                    return;
                }

                credential = EmailAuthProvider.getCredential(mCurrentUser.getEmail(), userInputCurrentPassword);
                break;
            default:
                break;
        }

        // Authenticate user
        mCurrentUser.reauthenticate(credential)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {

                        // Deletes users posts including each posts like collection.

                        documentReference.collection("User Posts")
                                         .get()
                                         .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                                             @Override
                                             public void onSuccess(QuerySnapshot documentSnapshots) {

                                                 for(DocumentSnapshot documentSnapshot : documentSnapshots){
                                                     // Iterate through Likes collection in each user post document and deletes each document.
                                                     documentSnapshot.getReference()
                                                                     .collection("Likes")
                                                                     .get()
                                                                     .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                                                                         @Override
                                                                         public void onSuccess(QuerySnapshot documentSnapshots) {
                                                                             for(DocumentSnapshot documentSnapshot : documentSnapshots){
                                                                                 documentSnapshot.getReference().delete();
                                                                             }
                                                                         }
                                                                     });

                                                     // Deletes each document in the current user posts collection.
                                                     documentSnapshot.getReference().delete();
                                                 }
                                             }
                                         });

                        // Storage ref
                        StorageReference storageReference = FirebaseStorage.getInstance().getReference();

                        // Delete user profile picture from storage
                        storageReference.child("profile pictures/" + mCurrentUser.getUid())
                                        .delete();

                        // Delete cover photo image from storage
                        storageReference.child("cover photos/" + mCurrentUser.getUid())
                                        .delete();

                        // Delete current user from users follower collections
                        documentReference.collection("Followers")
                                         .get()
                                         .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                                             @Override
                                             public void onSuccess(QuerySnapshot documentSnapshots) {

                                                 for(DocumentSnapshot documentSnapshot : documentSnapshots) {

                                                     // Deletes current user from all users following collections.
                                                     FirebaseFirestore.getInstance()
                                                                      .collection("Users/" + documentSnapshot.getId() + "/Following/")
                                                                      .document(mCurrentUser.getUid())
                                                                      .get()
                                                                      .addOnSuccessListener(documentSnapshot12 -> documentSnapshot12.getReference().delete());

                                                     documentSnapshot.getReference().delete();
                                                 }
                                             }
                                         });

                        // Delete current user from users following collections
                        documentReference.collection("Following")
                                         .get()
                                         .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                                             @Override
                                             public void onSuccess(QuerySnapshot documentSnapshots) {

                                                 for(DocumentSnapshot documentSnapshot : documentSnapshots) {

                                                     // Deletes current user from all users followers collections.
                                                     FirebaseFirestore.getInstance()
                                                                      .collection("Users/" + documentSnapshot.getId() + "/Followers/")
                                                                      .document(mCurrentUser.getUid())
                                                                      .get()
                                                                      .addOnSuccessListener(documentSnapshot12 -> documentSnapshot12.getReference().delete());

                                                     documentSnapshot.getReference().delete();
                                                 }
                                             }
                                         });

                        // Deletes user document from user collection
                        documentReference.delete().addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {
                                Toast.makeText(DeleteAccountActivity.this, "User information has been deleted successfully", Toast.LENGTH_SHORT).show();
                            }
                        }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Toast.makeText(DeleteAccountActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });

                        mCurrentUser.delete()
                                .addOnSuccessListener(aVoid1 -> Toast.makeText(DeleteAccountActivity.this, "Your account has been deleted.", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e -> Toast.makeText(DeleteAccountActivity.this, e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(DeleteAccountActivity.this, e.getMessage(), Toast.LENGTH_LONG).show());
    }

    /**
     * Adds listener on activity
     */
    @Override
    protected void onStart(){
        super.onStart();
        mAuth.addAuthStateListener(mAuthListener);
    }

    /**
     * Removes listener on activity
     */
    @Override
    protected void onStop() {
        super.onStop();
        mAuth.removeAuthStateListener(mAuthListener);
    }
}