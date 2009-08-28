package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;

public class Waiter implements Runnable {
  private final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.Waiter");
  private final Project myProject;
  private final ModalityState myState;
  private final Runnable myRunnable;
  private boolean myStarted;
  private boolean myDone;
  private final Object myLock = new Object();

  public Waiter(final Project project, final Runnable runnable, final ModalityState state) {
    myRunnable = runnable;
    myProject = project;
    myState = state;
    myDone = false;
    myStarted = false;
  }

  public void run() {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setIndeterminate(true);
      indicator.setText2(VcsBundle.message("commit.wait.util.synched.text"));
    }
    synchronized (myLock) {
      if (myStarted) {
        LOG.error("Waiter running under progress being started again.");
        return;
      }
      myStarted = true;
      while (! myDone) {
        try {
          // every second check whether we are canceled
          myLock.wait(500);
        }
        catch (InterruptedException e) {
          // ok
        }
        if (indicator != null) {
          indicator.checkCanceled();
        }
      }
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        synchronized (myLock) {
          if (! myDone) {
            return;
          }
        }
        if (myProject.isDisposed()) return;
        myRunnable.run();
        ChangesViewManager.getInstance(myProject).refreshView();
      }
    }, (myState == null) ? ModalityState.NON_MODAL : myState);
  }

  public void done() {
    synchronized (myLock) {
      myDone = true;
      myLock.notifyAll();
    }
  }
}
