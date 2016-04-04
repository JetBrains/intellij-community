/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public static CallbackData create(@NotNull final Runnable afterUpdate, final String title, final ModalityState state,
                                    final InvokeAfterUpdateMode mode, @NotNull final Project project) {
    if (mode.isSilently()) {
      return new CallbackData(new Runnable() {
        public void run() {
          if (mode.isCallbackOnAwt()) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                LOG.debug("invokeAfterUpdate: silent wrapper called for project: " + project.getName());
                if (project.isDisposed()) return;
                afterUpdate.run();
              }
            });
          }
          else {
            ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
              @Override
              public void run() {
                if (!project.isDisposed()) afterUpdate.run();
              }
            });
          }
        }
      }, null);
    }
    else {
      if (mode.isSynchronous()) {
        final Waiter waiter = new Waiter(project, afterUpdate, state,
                                         VcsBundle.message("change.list.manager.wait.lists.synchronization", title), mode.isCancellable());
        return new CallbackData(
          new Runnable() {
            public void run() {
              LOG.debug("invokeAfterUpdate: NOT silent SYNCHRONOUS wrapper called for project: " + project.getName());
              waiter.done();
            }
          }, new Runnable() {
          public void run() {
            ProgressManager.getInstance().run(waiter);
          }
        }
        );
      }
      else {
        final FictiveBackgroundable fictiveBackgroundable =
          new FictiveBackgroundable(project, afterUpdate, mode.isCancellable(), title, state);
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
        }
        );
      }
    }
  }
}
