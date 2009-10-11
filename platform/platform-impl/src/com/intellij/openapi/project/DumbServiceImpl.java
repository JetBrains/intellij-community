/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.project;

import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.BalloonHandler;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.util.Consumer;
import com.intellij.util.containers.Queue;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author peter
 */
public class DumbServiceImpl extends DumbService {
  private final AtomicBoolean myDumb = new AtomicBoolean();
  private final DumbModeListener myPublisher;
  private final Queue<Runnable> myUpdateQueue = new Queue<Runnable>(5);
  private final Queue<Runnable> myAfterUpdateQueue = new Queue<Runnable>(5);
  @NonNls public static final String FILE_INDEX_BACKGROUND = "fileIndex.background";
  private final StartupManagerImpl myStartupManager;
  private final Project myProject;

  public DumbServiceImpl(MessageBus bus, StartupManager startupManager, Project project) {
    myProject = project;
    myStartupManager = (StartupManagerImpl)startupManager;
    myPublisher = bus.syncPublisher(DUMB_MODE);
  }

  public boolean isDumb() {
    return myDumb.get();
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass"})
  public static DumbServiceImpl getInstance(@NotNull Project project) {
    return (DumbServiceImpl)DumbService.getInstance(project);
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public void runWhenSmart(Runnable runnable) {
    if (!isDumb()) {
      runnable.run();
    } else {
      synchronized (myAfterUpdateQueue) {
        myAfterUpdateQueue.addLast(runnable);
      }
    }
  }

  public void queueIndexUpdate(@NotNull final Consumer<ProgressIndicator> action) {
    final Runnable update = new Runnable() {
      public void run() {
        if (myProject.isDisposed()) {
          updateFinished();
          return;
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Updating indices", false) {
          @Override
          public void run(@NotNull final ProgressIndicator indicator) {
            indicator.setIndeterminate(false);
            indicator.setText("Indexing...");
            try {
              action.consume(indicator);
            }
            finally {
              ApplicationManager.getApplication().invokeLater(new DumbAwareRunnable() {
                public void run() {
                  updateFinished();
                }
              });
            }
          }
        });
      }
    };

    //todo always go dumb immediately after those who request indices in startup activity are gone (e.g. JS indices)
    final boolean goDumbImmediately = ((StartupManagerEx)StartupManager.getInstance(myProject)).startupActivityPassed();
    final boolean wasDumb = goDumbImmediately ? myDumb.getAndSet(true) : false;

    invokeOnEDT(new DumbAwareRunnable() {
      public void run() {
        if (myProject.isDisposed()) return;

        boolean wasDumbEx = wasDumb;
        if (!goDumbImmediately) {
          wasDumbEx = myDumb.getAndSet(true);
        }

        if (!wasDumbEx) {
          myPublisher.enteredDumbMode();
          update.run();
        } else {
          myUpdateQueue.addLast(update);
        }
      }
    });
  }

  private void invokeOnEDT(DumbAwareRunnable runnable) {
    final Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      runnable.run();
      return;
    }

    if (!myStartupManager.startupActivityPassed()) {
      myStartupManager.setBackgroundIndexing(true);
      myStartupManager.registerPostStartupActivity(runnable);
      return;
    }

    application.invokeLater(runnable, ModalityState.NON_MODAL);
  }

  private void updateFinished() {
    if (myProject.isDisposed()) return;

    if (!myUpdateQueue.isEmpty()) {
      //run next dumb action
      myUpdateQueue.pullFirst().run();
      return;
    }

    myDumb.set(false);
    myPublisher.exitDumbMode();

    while (true) {
      final Runnable runnable;
      synchronized (myAfterUpdateQueue) {
        if (myAfterUpdateQueue.isEmpty()) {
          break;
        }

        runnable = myAfterUpdateQueue.pullFirst();
      }
      runnable.run();
    }
  }

  @Override
  public BalloonHandler showDumbModeNotification(final String message) {
    StatusBarEx statusBar = (StatusBarEx)WindowManager.getInstance().getIdeFrame(myProject).getStatusBar();
    HyperlinkListener listener = new HyperlinkListener() {
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;

        Messages.showMessageDialog("<html>" +
                                   ApplicationNamesInfo.getInstance().getFullProductName() + " is now indexing your source and library files. These indices are<br>" +
                                   "needed for most of the smart functionality to work properly." +
                                   "<p>" +
                                   "During this process some actions that require these indices won't be available,<br>" +
                                   "although you still can edit your files and work with VCS and file system.<br>" +
                                   "If you need smarter actions like Goto Declaration, Find Usages or refactorings,<br>" +
                                   "please wait until the update is finished. We appreciate your understanding." +
                                   "</html>", "Don't panic!", null);
      }
    };
    return statusBar.notifyProgressByBalloon(MessageType.WARNING, message, null, listener);
  }

}
