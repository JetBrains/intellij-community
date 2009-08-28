package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManagerGate;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.changes.ChangelistBuilder;
import com.intellij.openapi.vcs.changes.VcsDirtyScope;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

public class MockDelayingChangeProvider implements ChangeProvider {
  private final Object myLock;
  private boolean myLocked;
  private Thread myExecuteInsideUpdate;

  public MockDelayingChangeProvider() {
    myLock = new Object();
  }

  public void getChanges(final VcsDirtyScope dirtyScope, final ChangelistBuilder builder, final ProgressIndicator progress,
                         final ChangeListManagerGate addGate)
    throws VcsException {
    synchronized (myLock) {
      if (myExecuteInsideUpdate == null) {
        System.out.println("MockDelayingChangeProvider: getChanges, no test set");
        return;
      }

      myLocked = true;
      System.out.println("MockDelayingChangeProvider: getChanges, starting test thread...");
      myExecuteInsideUpdate.start();

      while (myLocked) {
        try {
          myLock.wait();
        }
        catch (InterruptedException e) {
          //
        }
      }
      System.out.println("MockDelayingChangeProvider: unlocked");
    }
  }

  public void setTest(final Runnable runnable) {
    System.out.println("MockDelayingChangeProvider: setTest " + (runnable == null ? "(null)" : "(not null)"));
    synchronized (myLock) {
      if (runnable == null) {
        myExecuteInsideUpdate = null;
      } else {
        myExecuteInsideUpdate = new Thread(new Runnable() {
          public void run() {
            // wait until starter sleeps
            synchronized (myLock) {
              runnable.run();
            }
          }
        });
      }
    }
  }

  public void unlock() {
    synchronized (myLock) {
      System.out.println("MockDelayingChangeProvider: unlocking");
      myLocked = false;
      myLock.notifyAll();
    }
  }

  public boolean isModifiedDocumentTrackingRequired() {
    return false;
  }

  public void doCleanup(final List<VirtualFile> files) {
  }
}
