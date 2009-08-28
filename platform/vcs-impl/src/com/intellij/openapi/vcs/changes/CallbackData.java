package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class CallbackData {
  private final static Logger LOG = Logger.getInstance("com.intellij.openapi.vcs.changes.CallbackData");
  private final Runnable myCallback;
  private final Runnable myWrapperStarter;

  CallbackData(@NotNull final Runnable callback, @Nullable final Runnable wrapperStarter) {
    myCallback = callback;
    myWrapperStarter = wrapperStarter;
  }

  public Runnable getCallback() {
    return myCallback;
  }

  public Runnable getWrapperStarter() {
    return myWrapperStarter;
  }

  public static CallbackData create(final Runnable afterUpdate, final String title, final ModalityState state,
                                    final InvokeAfterUpdateMode mode, final Project project) {
    if (mode.isSilently()) {
      return new CallbackData(new Runnable() {
        public void run() {
          if (mode.isCallbackOnAwt()) {
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                LOG.debug("invokeAfterUpdate: silent wrapper called for project: " + project.getName());
                if (project.isDisposed()) return;
                afterUpdate.run();
                ChangesViewManager.getInstance(project).refreshView();
              }
            });
          } else {
            ApplicationManager.getApplication().executeOnPooledThread(afterUpdate);
          }
        }
      }, null);
    } else {
      if (mode.isSynchronous()) {
        final Waiter waiter = new Waiter(project, afterUpdate, state);
        return new CallbackData(
          new Runnable() {
            public void run() {
              LOG.debug("invokeAfterUpdate: NOT silent SYNCHRONOUS wrapper called for project: " + project.getName());
              waiter.done();
            }
          }, new Runnable() {
            public void run() {
              ProgressManager.getInstance().runProcessWithProgressSynchronously(waiter,
                      VcsBundle.message("change.list.manager.wait.lists.synchronization", title), mode.isCancellable(), project);
            }
          }
        );
      } else {
        final FictiveBackgroundable fictiveBackgroundable = new FictiveBackgroundable(project, afterUpdate, mode.isCancellable(), title, state);
        return new CallbackData(
          new Runnable() {
            public void run() {
              LOG.debug("invokeAfterUpdate: NOT silent wrapper called for project: " + project.getName());
              fictiveBackgroundable.done();
            }
          }, new Runnable() {
            public void run() {
              ProgressManager.getInstance().run(fictiveBackgroundable);
            }
          });
      }
    }
  }
}
