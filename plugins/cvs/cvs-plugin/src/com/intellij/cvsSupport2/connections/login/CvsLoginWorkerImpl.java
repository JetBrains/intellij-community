package com.intellij.cvsSupport2.connections.login;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.CalledInAwt;
import com.intellij.openapi.vcs.CalledInBackground;
import com.intellij.openapi.vcs.changes.ui.ChangesViewBalloonProblemNotifier;
import com.intellij.util.ThreeState;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public abstract class CvsLoginWorkerImpl<T extends CvsConnectionSettings> implements CvsLoginWorker {
  protected final Project myProject;
  protected final ModalityContext myExecutor;
  protected final T mySettings;

  protected CvsLoginWorkerImpl(final Project project, final T settings, final ModalityContext executor) {
    myProject = project;
    mySettings = settings;
    myExecutor = executor;
  }

  @CalledInBackground
  protected abstract void silentLoginImpl(boolean forceCheck) throws AuthenticationException;
  /**
   * @return <code>true</code> if login attempt should be repeated after prompting user
   */
  @CalledInAwt
  public abstract boolean promptForPassword();

  protected abstract void clearOldCredentials();

  public ThreeState silentLogin(boolean forceCheck) {
    if (mySettings.isOffline())  return ThreeState.NO;

    try {
      silentLoginImpl(forceCheck);
    }
    catch (AuthenticationException e) {
      return reportException(e);
    }
    return ThreeState.YES;
  }

  public void goOffline() {
    mySettings.setOffline(true);
    ChangesViewBalloonProblemNotifier.showMe(myProject, CvsBundle.message("set.offline.notification.text"), MessageType.WARNING);
  }

  private ThreeState reportException(final AuthenticationException e) {
    if (e.isSolveable()) {
      clearOldCredentials();
      return ThreeState.UNSURE;
    }

    Throwable cause = e.getCause();
    if (cause instanceof SocketTimeoutException) {
      showConnectionErrorMessage(myProject, CvsBundle.message("error.message.timeout.error"));
    }
    else if (cause instanceof UnknownHostException) {
      showConnectionErrorMessage(myProject, CvsBundle.message("error.message.unknown.host", mySettings.HOST));
    }
    else if (cause instanceof ConnectException || cause instanceof NoRouteToHostException) {
      showConnectionErrorMessage(myProject, CvsBundle.message("error.message.connection.error", mySettings.HOST));
    } else {
      String localizedMessage = e.getLocalizedMessage();
      localizedMessage = (localizedMessage == null) ? e.getMessage() : localizedMessage;
      localizedMessage = (localizedMessage == null) ? CvsBundle.message("error.dialog.title.cannot.connect.to.cvs") : localizedMessage;
      showConnectionErrorMessage(myProject, localizedMessage);
    }

    return ThreeState.NO;
  }

  public static void showConnectionErrorMessage(final Project project, final String message) {
    ChangesViewBalloonProblemNotifier.showMe(project, message, MessageType.ERROR);
  }
}
