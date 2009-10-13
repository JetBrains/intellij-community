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

import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author peter
 */
public class DumbServiceImpl extends DumbService {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.project.DumbServiceImpl");
  private final AtomicBoolean myDumb = new AtomicBoolean();
  private final DumbModeListener myPublisher;
  private final Queue<IndexUpdateRunnable> myUpdateQueue = new Queue<IndexUpdateRunnable>(5);
  /* to be accessed from EDT only! */
  private IndexUpdateRunnable myCurrentUpdateRunnable = null;
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
    }
    else {
      synchronized (myAfterUpdateQueue) {
        myAfterUpdateQueue.addLast(runnable);
      }
    }
  }

  public void queueIndexUpdate(@NotNull final Consumer<ProgressIndicator> action, final int itemsCount) {
    final IndexUpdateRunnable update = new IndexUpdateRunnable(action, itemsCount);

    //todo always go dumb immediately after those who request indices in startup activity are gone (e.g. JS indices)
    final boolean goDumbImmediately = ((StartupManagerEx)StartupManager.getInstance(myProject)).startupActivityPassed();
    final boolean wasDumb = goDumbImmediately ? myDumb.getAndSet(true) : false;

    invokeOnEDT(new DumbAwareRunnable() {
      public void run() {
        if (myProject.isDisposed()) {
          return;
        }

        boolean wasDumbEx = wasDumb;
        if (!goDumbImmediately) {
          wasDumbEx = myDumb.getAndSet(true);
        }

        if (!wasDumbEx) {
          myPublisher.enteredDumbMode();
          update.run();
        }
        else {
          final IndexUpdateRunnable currentUpdateRunnable = myCurrentUpdateRunnable;
          if (currentUpdateRunnable != null) {
            currentUpdateRunnable.myTotalItems += itemsCount;
          }
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
    myCurrentUpdateRunnable = null;
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

  private static final Consumer<ProgressIndicator> NULL_ACTION = new Consumer<ProgressIndicator>() {
    public void consume(ProgressIndicator progressIndicator) {
    }
  };

  private class IndexUpdateRunnable implements Runnable {
    private final Consumer<ProgressIndicator> myAction;
    private double myProcessedItems;
    private volatile int myTotalItems;
    private double myCurrentBaseTotal;

    public IndexUpdateRunnable(Consumer<ProgressIndicator> action, int itemsCount) {
      myAction = action;
      myTotalItems = itemsCount;
      myCurrentBaseTotal = itemsCount;
    }

    public void run() {
      if (myProject.isDisposed()) {
        return;
      }
      myCurrentUpdateRunnable = this;
      
      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Updating indices", false) {

        private final ArrayBlockingQueue<Consumer<ProgressIndicator>> myActionQueue = new ArrayBlockingQueue<Consumer<ProgressIndicator>>(1);

        @Override
        public void run(@NotNull final ProgressIndicator indicator) {
          final ProgressIndicator proxy =
            (ProgressIndicator)Proxy.newProxyInstance(indicator.getClass().getClassLoader(), new Class[]{ProgressIndicator.class}, new InvocationHandler() {
              public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if ("setFraction".equals(method.getName())) {
                  final double fraction = (Double)args[0];
                  args[0] = new Double((myProcessedItems + fraction * myCurrentBaseTotal) / myTotalItems);
                }
                try {
                  return method.invoke(indicator, args);
                }
                catch (InvocationTargetException e) {
                  final Throwable cause = e.getCause();
                  if (cause instanceof ProcessCanceledException) {
                    throw cause;
                  }
                  throw e;
                }
              }
            });
          runAction(proxy, myAction);
        }

        private void runAction(ProgressIndicator indicator, Consumer<ProgressIndicator> action) {
          indicator.setIndeterminate(false);
          do {
            indicator.setText("Indexing...");
            try {
              action.consume(indicator);
            }
            finally {
              myProcessedItems += myCurrentBaseTotal;
              ApplicationManager.getApplication().invokeLater(new DumbAwareRunnable() {
                public void run() {
                  if (myProject.isDisposed()) {
                    return;
                  }
                  if (myUpdateQueue.isEmpty()) {
                    // really terminate the tesk
                    myActionQueue.offer(NULL_ACTION);
                    updateFinished();
                  }
                  else {
                    //run next dumb action
                    final IndexUpdateRunnable nextUpdateRunnable = myUpdateQueue.pullFirst();
                    myCurrentBaseTotal = nextUpdateRunnable.myTotalItems;
                    // run next action under already existing progress indicator
                    if (!myActionQueue.offer(nextUpdateRunnable.myAction)) {
                      nextUpdateRunnable.run();
                    }
                  }
                }
              });

              // try to obtain the next action or terminate if no actions left
              try {
                do {
                  action = myActionQueue.poll(500, TimeUnit.MILLISECONDS);
                  if (myProject.isDisposed()) {
                    // just terminate the progress task
                    action = NULL_ACTION;
                  }
                }
                while (action == null);
              }
              catch (InterruptedException ignored) {
                LOG.info(ignored);
                break;
              }
            }
          }
          while (action != NULL_ACTION);
          // make it impossible to add actions to the queue anymore
          myActionQueue.offer(NULL_ACTION);
        }

      });
    }
  }
}
