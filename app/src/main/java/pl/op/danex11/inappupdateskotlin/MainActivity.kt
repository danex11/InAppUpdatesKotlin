package pl.op.danex11.inappupdateskotlin

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.ActivityResult
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.tasks.Task


// as in this tutorial: https://www.raywenderlich.com/8034025-in-app-updates-getting-started


//ina app update type
private const val APP_UPDATE_TYPE_SUPPORTED = AppUpdateType.IMMEDIATE

// a request code after calling startUpdateFlowForResult which will launch an external activity.
// That way, when it finishes, you can check if the operation proved successful or not.
private const val REQUEST_UPDATE = 100


class MainActivity : AppCompatActivity() {
    private lateinit var updateListener: InstallStateUpdatedListener

    val btn_update = findViewById<Button>(R.id.btn_update)
    val tv_status = findViewById<TextView>(R.id.tv_status)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        //check for updates call
        checkForUpdates()
    }

    private fun checkForUpdates() {
        //1
        //To know that a new update is available, you need to create an instance of AppUpdateManager
        val appUpdateManager = AppUpdateManagerFactory.create(baseContext)
        val appUpdateInfo = appUpdateManager.appUpdateInfo
        //and then register a listener that will be triggered when the app communicates with the Play Store
        appUpdateInfo.addOnSuccessListener {
            //2
            //call handleUpdate which will check if the update can be done or not
            handleUpdate(appUpdateManager, appUpdateInfo)
        }
    }

    private fun handleUpdate(manager: AppUpdateManager, info: Task<AppUpdateInfo>) {
        if (APP_UPDATE_TYPE_SUPPORTED == AppUpdateType.IMMEDIATE) {
            handleImmediateUpdate(manager, info)
        } else if (APP_UPDATE_TYPE_SUPPORTED == AppUpdateType.FLEXIBLE) {
            handleFlexibleUpdate(manager, info)
        }
    }


    private fun handleImmediateUpdate(manager: AppUpdateManager, info: Task<AppUpdateInfo>) {
        //1
        //Before starting the update, it’s important to analyze the response from the Play Store.
        // The update availability can be one of the following values:
        //DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS: When there’s an ongoing update.
        //UPDATE_AVAILABLE: When a new update is available.
        //UPDATE_NOT_AVAILABLE: When there’s no update available.
        //UNKNOWN: When there was a problem connecting to the app store
        //2
        //Before starting this process, verify that there’s an update available or one already in progress.
        if ((info.result.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
                    info.result.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) &&
            //3
            //Verify if the immediate type is supported.
            info.result.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
        )
        //4
        // Start or resume the update with the startUpdateFlowForResult but only if the previous conditions are true.
        //After calling startUpdateFlowForResult with AppUpdateType.IMMEDIATE,
        //the Play Core activity will take care of the update and restart the app when it finishes.
        {
            manager.startUpdateFlowForResult(
                info.result,
                AppUpdateType.IMMEDIATE,
                this,
                REQUEST_UPDATE
            )
        }
    }

    private fun handleFlexibleUpdate(manager: AppUpdateManager, info: Task<AppUpdateInfo>) {
        // first check for available updates
        if ((info.result.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
                    info.result.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) &&
            info.result.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
        ) {
            //If one is found, the button to start the process becomes visible, and the app calls setUpdateAction to handle the flow.
            btn_update.visibility = View.VISIBLE
            setUpdateAction(manager, info)
        }
        //todo (there???): AppUpdateManager.completeUpdate()
    }

    private fun setUpdateAction(manager: AppUpdateManager, info: Task<AppUpdateInfo>) {
        //1
        //First, it sets the callback for when the user taps the button. This action will start the update action defined in step 7
        btn_update.setOnClickListener {
            //2
            // notifies the app of every step of the process.
            updateListener = InstallStateUpdatedListener {
                //3
                //tv_status displays visual information about the update. It’s hidden by default.
                btn_update.visibility = View.GONE
                tv_status.visibility = View.VISIBLE
                //4
                //when block defines all the possible states on the update flow.
                when (it.installStatus()) {
                    InstallStatus.FAILED, InstallStatus.UNKNOWN -> {
                        tv_status.text = getString(R.string.info_failed)
                        btn_update.visibility = View.VISIBLE
                    }
                    InstallStatus.PENDING -> {
                        tv_status.text = getString(R.string.info_pending)
                    }
                    InstallStatus.CANCELED -> {
                        tv_status.text = getString(R.string.info_canceled)
                    }
                    InstallStatus.DOWNLOADING -> {
                        tv_status.text = getString(R.string.info_downloading)
                    }
                    //When the system finishes downloading the .apk, the app can either be in one of two states:
                    //If on foreground, the user needs to confirm that the app can be relaunched. This avoids interrupting their current usage of the app.
                    //If on background, the user minimizes it after declining the installation. The system will automatically install the newest update and relaunch when the app returns to foreground.
                    //5
                    InstallStatus.DOWNLOADED -> {
                        tv_status.text = getString(R.string.info_installing)
                        launchRestartDialog(manager)
                    }
                    InstallStatus.INSTALLING -> {
                        tv_status.text = getString(R.string.info_installing)
                    }
                    //6
                    //After the update is successfully installed, there’s no need to keep the listener register in the app.
                    // There are no more actions to be made, so you can hide the Button and unregister the callback
                    InstallStatus.INSTALLED -> {
                        tv_status.text = getString(R.string.info_installed)
                        manager.unregisterListener(updateListener)
                    }
                    else -> {
                        tv_status.text = getString(R.string.info_restart)
                    }
                }
            }
            //7
            //Register the previous listener.
            manager.registerListener(updateListener)
            //8
            //Start the flexible update.
            manager.startUpdateFlowForResult(
                info.result,
                AppUpdateType.FLEXIBLE,
                this,
                REQUEST_UPDATE
            )
        }
    }



    private fun launchRestartDialog(manager: AppUpdateManager) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_title))
            .setMessage(getString(R.string.update_message))
            .setPositiveButton(getString(R.string.action_restart)) { _, _ ->
                manager.completeUpdate()
            }
            .create().show()

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        //1
        //Confirm that onActivityResult was called with requestCode from an in-app update.
        // It should be the same code as the one defined in startUpdateFlowForResult – REQUEST_UPDATE
        if (REQUEST_UPDATE == requestCode) {
            when (resultCode) {
                //2
                    //The update was successfully installed.
                Activity.RESULT_OK -> {
                    if (APP_UPDATE_TYPE_SUPPORTED == AppUpdateType.IMMEDIATE) {
                        Toast.makeText(baseContext, R.string.toast_updated, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(baseContext, R.string.toast_started, Toast.LENGTH_SHORT).show()
                    }
                }
                //3
                //The user canceled the update.
                // Although it’s showing a toast, as a challenge, you can show a dialog mentioning the importance of always installing the latest version.
                Activity.RESULT_CANCELED -> {
                    Toast.makeText(baseContext, R.string.toast_cancelled, Toast.LENGTH_SHORT).show()
                }
                //4
                //The update failed due to some unknown reason.
                // Typically, this is an error from the Play Store. Try to restart the update process
                ActivityResult.RESULT_IN_APP_UPDATE_FAILED -> {
                    Toast.makeText(baseContext, R.string.toast_failed, Toast.LENGTH_SHORT).show()
                }
            }
            super.onActivityResult(requestCode, resultCode, data)
        }
    }


}