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

import com.intellij.ide.IdeBundle;
import com.intellij.ide.caches.CacheUpdater;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.BalloonHandler;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.Queue;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DumbServiceImpl extends DumbService {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.project.DumbServiceImpl");
  private final AtomicBoolean myDumb = new AtomicBoolean();
  private final DumbModeListener myPublisher;
  private final LinkedBlockingQueue<CacheUpdateRunner> myUpdatesQueue = new LinkedBlockingQueue<CacheUpdateRunner>();
  private final Queue<Runnable> myRunWhenSmartQueue = new Queue<Runnable>(5);
  private final Project myProject;


  public static DumbServiceImpl getInstance(@NotNull Project project) {
    return (DumbServiceImpl)DumbService.getInstance(project);
  }

  public DumbServiceImpl(Project project, MessageBus bus) {
    myProject = project;
    myPublisher = bus.syncPublisher(DUMB_MODE);

    new CacheUpdateProcessor().start();
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  public boolean isDumb() {
    return myDumb.get();
  }

  @Override
  public void runWhenSmart(Runnable runnable) {
    // todo: run is swing thread
    if (!isDumb()) {
      runnable.run();
    }
    else {
      synchronized (myRunWhenSmartQueue) {
        myRunWhenSmartQueue.addLast(runnable);
      }
    }
  }

  public void queueCacheUpdate(Collection<CacheUpdater> updaters) {
    // prevent concurrent modifications
    final Collection<CacheUpdater> updatersCopy = new ArrayList<CacheUpdater>(updaters);
    CacheUpdateRunner runner = new CacheUpdateRunner(myProject, updatersCopy);

    if (ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment()) {
      EmptyProgressIndicator i = new EmptyProgressIndicator();
      runner.queryNeededFiles(i);
      runner.processFiles(i, false);
      runner.updatingDone();
      return;
    }

    ProgressIndicator indicator = new EmptyProgressIndicator();

    if (ApplicationManager.getApplication().isDispatchThread() && !isDumb()) {
      int size = runner.queryNeededFiles(indicator);
      if (size < 10) {
        if (size > 0) {
          runner.processFiles(indicator, false);
        }
        runner.updatingDone();
        return;
      }

      updateStarted();
    }

    try {
      myUpdatesQueue.put(runner);
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private void updateStarted() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myDumb.set(true);
    myPublisher.enteredDumbMode();
  }

  private void updateFinished() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myDumb.set(false);
    myPublisher.exitDumbMode();
    while (true) {
      final Runnable runnable;
      synchronized (myRunWhenSmartQueue) {
        if (myRunWhenSmartQueue.isEmpty()) {
          break;
        }
        runnable = myRunWhenSmartQueue.pullFirst();
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
                                   ApplicationNamesInfo.getInstance().getFullProductName() +
                                   " is now indexing project sources and libraries to enable advanced features <br>" +
                                   "(refactorings, navigation, usage search, code analysis, formatting, etc.)<br>" +
                                   "During this process you can use code editor and VCS integrations,<br>" +
                                   "and adjust IDE and Run Configurations settings." +
                                   "</html>", "Don't panic!", null);
      }
    };
    return statusBar.notifyProgressByBalloon(MessageType.WARNING, message, null, listener);
  }

  private class CacheUpdateProcessor implements Runnable {
    private int myProcessedItems;
    private int myTotalItems;
    private int myCurrentUpdateTotal;

    public void start() {
      new Thread(this).start();
    }

    public void run() {
      while (true) {
        CacheUpdateRunner runner = null;
        while (runner == null) {
          if (myProject.isDisposed()) return;
          try {
            runner = myUpdatesQueue.poll(500, TimeUnit.MILLISECONDS);
          }
          catch (InterruptedException e) {
            LOG.info(e);
            return;
          }
        }
        if (myProject.isDisposed()) return;

        final Semaphore sema = new Semaphore();
        sema.down();

        final CacheUpdateRunner finalRunner = runner;
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            new Task.Backgroundable(myProject, IdeBundle.message("progress.indexing"), false) {
              @Override
              public void run(@NotNull final ProgressIndicator indicator) {
                try {
                  notifyStartedIfNotInDumbMode();
                  try {
                    processPendingUpdates(finalRunner, createIndicatorProxy(indicator));
                  }
                  finally {
                    notifyFinishedIfQueueEmpty();
                  }
                }
                catch (Throwable e) {
                  if (!(e instanceof ProcessCanceledException)) {
                    LOG.error(e);
                  }
                }
                finally {
                  sema.up();
                }
              }

            }.queue();
          }
        });

        sema.waitFor();
      }
    }

    private void notifyStartedIfNotInDumbMode() {
      ApplicationManager.getApplication().invokeAndWait(new Runnable() {
        public void run() {
          if (myProject.isDisposed()) return;
          if (!isDumb()) {
            updateStarted();
            myProcessedItems = 0;
            myTotalItems = 0;
            myCurrentUpdateTotal = 0;
          }
        }
      }, ModalityState.defaultModalityState());
    }

    private void notifyFinishedIfQueueEmpty() {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (myProject.isDisposed()) return;
          if (myUpdatesQueue.isEmpty()) updateFinished();
        }
      });
    }

    private void processPendingUpdates(CacheUpdateRunner runner, ProgressIndicator indicator) {
      while (true) {
        if (myProject.isDisposed()) break;

        queryNeededFiles(runner, indicator);
        processFiles(runner, indicator);
        updatingDone(runner);

        try {
          if (myProject.isDisposed()) break;
          runner = myUpdatesQueue.poll(100, TimeUnit.MILLISECONDS);
          if (runner == null) return;
        }
        catch (InterruptedException e) {
          LOG.info(e);
          break;
        }
      }
    }

    private void queryNeededFiles(CacheUpdateRunner runner, ProgressIndicator indicator) {
      indicator.setIndeterminate(true);
      indicator.setText(IdeBundle.message("progress.indexing.scanning"));
      myCurrentUpdateTotal = runner.queryNeededFiles(indicator);
      myTotalItems += myCurrentUpdateTotal;
    }

    private void processFiles(CacheUpdateRunner runner, ProgressIndicator indicator) {
      long before = System.currentTimeMillis();
      indicator.setIndeterminate(false);
      indicator.setText(IdeBundle.message("progress.indexing.updaing"));
      runner.processFiles(indicator, true);

      long after = System.currentTimeMillis();
    }

    private void updatingDone(CacheUpdateRunner runner) {
      runner.updatingDone();
      myProcessedItems += myCurrentUpdateTotal;
    }

    private ProgressIndicator createIndicatorProxy(final ProgressIndicator indicator) {
      return (ProgressIndicator)Proxy
        .newProxyInstance(indicator.getClass().getClassLoader(), new Class[]{ProgressIndicator.class}, new InvocationHandler() {
          public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("setFraction".equals(method.getName())) {
              double fraction = (Double)args[0];
              args[0] = new Double((myProcessedItems + fraction * myCurrentUpdateTotal) / myTotalItems);
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
    }
  }
}
