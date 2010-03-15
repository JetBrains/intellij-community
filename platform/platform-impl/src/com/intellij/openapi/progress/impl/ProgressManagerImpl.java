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
package com.intellij.openapi.progress.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.progress.util.SmoothProgressAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.psi.PsiLock;
import com.intellij.ui.SystemNotifications;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ProgressManagerImpl extends ProgressManager {
  @NonNls private static final String PROCESS_CANCELED_EXCEPTION = "idea.ProcessCanceledException";

  private static final ThreadLocal<ProgressIndicator> myThreadIndicator = new ThreadLocal<ProgressIndicator>();
  private final AtomicInteger myCurrentProgressCount = new AtomicInteger(0);
  private final AtomicInteger myCurrentUnsafeProgressCount = new AtomicInteger(0);
  private final AtomicInteger myCurrentModalProgressCount = new AtomicInteger(0);

  private static volatile int ourLockedCheckCounter = 0;
  private final List<ProgressFunComponentProvider> myFunComponentProviders = new ArrayList<ProgressFunComponentProvider>();
  @NonNls private static final String NAME = "Progress Cancel Checker";
  private static final boolean DISABLED = Comparing.equal(System.getProperty(PROCESS_CANCELED_EXCEPTION), "disabled");

  public ProgressManagerImpl(Application application) {
    if (!application.isUnitTestMode() && !DISABLED) {
      final Thread thread = new Thread(NAME) {
        public void run() {
          while (true) {
            try {
              sleep(10);
            }
            catch (InterruptedException ignored) {
            }
            ourNeedToCheckCancel = true;
          }
        }
      };
      thread.setPriority(Thread.MAX_PRIORITY - 1);
      thread.start();
    }
  }

  @Override
  protected void doCheckCanceled() throws ProcessCanceledException {
    final ProgressIndicator progress = getProgressIndicator();
    if (progress != null) {
      try {
        progress.checkCanceled();
      }
      catch (ProcessCanceledException e) {
        if (DISABLED) {
          return;
        }
        if (Thread.holdsLock(PsiLock.LOCK)) {
          ourLockedCheckCounter++;
          if (ourLockedCheckCounter > 10) {
            ourLockedCheckCounter = 0;
            ourNeedToCheckCancel = true;
          }
        }
        else {
          ourLockedCheckCounter = 0;
          throw e;
        }
      }
    }
  }

  public static void canceled() {
    ourNeedToCheckCancel = true;
  }

  private static class NonCancelableIndicator extends EmptyProgressIndicator implements NonCancelableSection {
    private final ProgressIndicator myOld;

    private NonCancelableIndicator(ProgressIndicator old) {
      myOld = old;
    }

    public void done() {
      ProgressIndicator currentIndicator = myThreadIndicator.get();
      if (currentIndicator != this) {
        throw new AssertionError("Trying do .done() NonCancelableSection, which is already done");
      }

      myThreadIndicator.set(myOld);
    }

    @Override
    public void checkCanceled() {
    }
  }

  public NonCancelableSection startNonCancelableSection() {
    NonCancelableIndicator nonCancelor = new NonCancelableIndicator(myThreadIndicator.get());
    myThreadIndicator.set(nonCancelor);
    return nonCancelor;
  }

  public void executeNonCancelableSection(Runnable r) {
    NonCancelableSection nonCancelor = startNonCancelableSection();
    try {
      r.run();
    }
    finally {
      nonCancelor.done();
    }
  }

  public JComponent getProvidedFunComponent(Project project, String processId) {
    for(ProgressFunComponentProvider provider: Extensions.getExtensions(ProgressFunComponentProvider.EP_NAME)) {
      JComponent cmp = provider.getProgressFunComponent(project, processId);
      if (cmp != null) return cmp;
    }
    
    for (ProgressFunComponentProvider provider : myFunComponentProviders) {
      JComponent cmp = provider.getProgressFunComponent(project, processId);
      if (cmp != null) return cmp;
    }
    return null;
  }

  public void setCancelButtonText(String cancelButtonText) {
    ProgressIndicator progressIndicator = getProgressIndicator();
    if (progressIndicator != null) {
      if (progressIndicator instanceof SmoothProgressAdapter && cancelButtonText != null) {
        ProgressIndicator original = ((SmoothProgressAdapter)progressIndicator).getOriginal();
        if (original instanceof ProgressWindow) {
          ((ProgressWindow)original).setCancelButtonText(cancelButtonText);
        }
      }
    }

  }

  public void registerFunComponentProvider(ProgressFunComponentProvider provider) {
    myFunComponentProviders.add(provider);
  }

  public void removeFunComponentProvider(ProgressFunComponentProvider provider) {
    myFunComponentProviders.remove(provider);
  }

  public boolean hasProgressIndicator() {
    return myCurrentProgressCount.get() > 0;
  }

  public boolean hasUnsafeProgressIndicator() {
    return myCurrentUnsafeProgressCount.get() > 0;
  }

  public boolean hasModalProgressIndicator() {
    return myCurrentModalProgressCount.get() > 0;
  }

  public void runProcess(@NotNull final Runnable process, final ProgressIndicator progress) {
    executeProcessUnderProgress(new Runnable(){
      public void run() {
        synchronized (process) {
          process.notify();
        }
        try {
          if (progress != null && !progress.isRunning()) {
            progress.start();
          }
          process.run();
        }
        finally {
          if (progress != null && progress.isRunning()) {
            progress.stop();
            if (progress instanceof ProgressIndicatorEx) {
              ((ProgressIndicatorEx)progress).processFinish();
            }
          }
        }
      }
    },progress);
  }

  public void executeProcessUnderProgress(@NotNull Runnable process, ProgressIndicator progress) throws ProcessCanceledException {
    ProgressIndicator oldIndicator = myThreadIndicator.get();

    if (progress != null) myThreadIndicator.set(progress);
    myCurrentProgressCount.incrementAndGet();

    final boolean modal = progress != null && progress.isModal();
    if (modal) myCurrentModalProgressCount.incrementAndGet();
    if (progress == null || progress instanceof ProgressWindow) myCurrentUnsafeProgressCount.incrementAndGet();

    try {
      process.run();
    }
    finally {
      myThreadIndicator.set(oldIndicator);

      myCurrentProgressCount.decrementAndGet();
      if (modal) myCurrentModalProgressCount.decrementAndGet();
      if (progress == null || progress instanceof ProgressWindow) myCurrentUnsafeProgressCount.decrementAndGet();
    }
  }

  public ProgressIndicator getProgressIndicator() {
    return myThreadIndicator.get();
  }

  public boolean runProcessWithProgressSynchronously(@NotNull final Runnable process, String progressTitle, boolean canBeCanceled, Project project) {
    return runProcessWithProgressSynchronously(process, progressTitle, canBeCanceled, project, null);
  }

  public boolean runProcessWithProgressSynchronously(@NotNull final Runnable process, String progressTitle, boolean canBeCanceled, Project project, JComponent parentComponent) {
    Task.Modal task = new Task.Modal(project, progressTitle, canBeCanceled) {
      public void run(@NotNull ProgressIndicator indicator) {
        process.run();
      }
    };
    return runProcessWithProgressSynchronously(task, parentComponent);
  }

  private static boolean runProcessWithProgressSynchronously(final Task task, final JComponent parentComponent) {
    final long start = System.currentTimeMillis();
    final boolean result = ((ApplicationEx)ApplicationManager.getApplication())
        .runProcessWithProgressSynchronously(new TaskContainer(task) {
          public void run() {
            new TaskRunnable(task, ProgressManager.getInstance().getProgressIndicator()).run();
          }
        }, task.getTitle(), task.isCancellable(), task.getProject(), parentComponent, task.getCancelText());
    if (result) {
      final long end = System.currentTimeMillis();
      final Task.NotificationInfo notificationInfo = task.getNotificationInfo();
      if (notificationInfo != null && end - start > 5000) { // show notification only if process took more than 5 secs
        final JFrame frame = WindowManager.getInstance().getFrame(task.getProject());
        if (!frame.hasFocus()) {
          systemNotify(notificationInfo);
        }
      }

      task.onSuccess();
    }
    else {
      task.onCancel();
    }
    return result;
  }

  private static void systemNotify(final Task.NotificationInfo notificationInfo) {
    final SystemNotifications notifications = ServiceManager.getService(SystemNotifications.class);
    if (notifications == null) return;
    notifications.notify(notificationInfo.getNotificationName(), notificationInfo.getNotificationTitle(), notificationInfo.getNotificationText());
  }

  public void runProcessWithProgressAsynchronously(@NotNull Project project,
                                                   @NotNull String progressTitle,
                                                   @NotNull final Runnable process,
                                                   @Nullable final Runnable successRunnable,
                                                   @Nullable final Runnable canceledRunnable) {
    runProcessWithProgressAsynchronously(project, progressTitle, process, successRunnable, canceledRunnable, PerformInBackgroundOption.DEAF);
  }

  public void runProcessWithProgressAsynchronously(@NotNull final Project project,
                                                   @Nls @NotNull final String progressTitle,
                                                   @NotNull final Runnable process,
                                                   @Nullable final Runnable successRunnable,
                                                   @Nullable final Runnable canceledRunnable,
                                                   @NotNull final PerformInBackgroundOption option) {

    runProcessWithProgressAsynchronously(new Task.Backgroundable(project, progressTitle, true, option) {
      public void run(@NotNull final ProgressIndicator indicator) {
        process.run();
      }


      public void onCancel() {
        if (canceledRunnable != null) {
          canceledRunnable.run();
        }
      }

      public void onSuccess() {
        if (successRunnable != null) {
          successRunnable.run();
        }
      }
    });
  }

  public static void runProcessWithProgressAsynchronously(final Task.Backgroundable task) {
    final ProgressIndicator progressIndicator;
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      progressIndicator = new EmptyProgressIndicator();
    }
    else {
      final BackgroundableProcessIndicator indicator = new BackgroundableProcessIndicator(task);
      final Project project = task.getProject();
      Disposer.register(ApplicationManager.getApplication(), indicator);
      progressIndicator = indicator;
    }


    final Runnable process = new TaskRunnable(task, progressIndicator);

    TaskContainer action = new TaskContainer(task) {
      public void run() {
        boolean canceled = false;
        final long start = System.currentTimeMillis();
        try {
          ProgressManager.getInstance().runProcess(process, progressIndicator);
        }
        catch (ProcessCanceledException e) {
          canceled = true;
        }
        final long end = System.currentTimeMillis();

        if (canceled || progressIndicator.isCanceled()) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              task.onCancel();
            }
          }, ModalityState.NON_MODAL);
        }
        else if (!canceled) {
          final Task.NotificationInfo notificationInfo = task.getNotificationInfo();
          if (notificationInfo != null && end - start > 5000) { // snow notification if process took more than 5 secs
            final Component window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
            if (window == null || notificationInfo.isShowWhenFocused()) {
              systemNotify(notificationInfo);
            }
          }

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              task.onSuccess();
            }
          }, ModalityState.NON_MODAL);
        }
      }
    };

    synchronized (process) {
      ApplicationManager.getApplication().executeOnPooledThread(action);
      try {
        process.wait();
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public void run(@NotNull final Task task) {
    if (task.isHeadless()) {
      new TaskRunnable(task, new EmptyProgressIndicator()).run();
      return;
    }

    if (task.isModal()) {
      runProcessWithProgressSynchronously(task.asModal(), null);
    }
    else {
      final Task.Backgroundable backgroundable = task.asBackgroundable();
      if (backgroundable.isConditionalModal() && !backgroundable.shouldStartInBackground()) {
        runProcessWithProgressSynchronously(task, null);
      }
      else {
        runProcessWithProgressAsynchronously(backgroundable);
      }
    }
  }

  private abstract static class TaskContainer implements Runnable {
    private final Task myTask;

    protected TaskContainer(final Task task) {
      myTask = task;
    }

    public Task getTask() {
      return myTask;
    }
  }

  private static class TaskRunnable extends TaskContainer {
    private final ProgressIndicator myIndicator;

    private TaskRunnable(@NotNull Task task, @NotNull ProgressIndicator indicator) {
      super(task);
      myIndicator = indicator;
    }

    public void run() {
      try {
        getTask().run(myIndicator);
      }
      finally {
        if (myIndicator instanceof ProgressIndicatorEx) {
          ((ProgressIndicatorEx)myIndicator).finish(getTask());
        }
      }
    }
  }

  //for debugging
  @SuppressWarnings({"UnusedDeclaration"})
  private static void stopCheckCanceled() {
    Thread[] threads = new Thread[500];
    Thread.enumerate(threads);
    for (Thread thread : threads) {
      if (thread == null) continue;
      if (NAME.equals(thread.getName())) {
        Thread.State oldState = thread.getState();
        thread.suspend();
        System.out.println(thread +" suspended ("+oldState+ "->"+thread.getState()+")");
      }
    }
  }

  @TestOnly
  public static void setNeedToCheckCancel(boolean needToCheckCancel) {
    ourNeedToCheckCancel = needToCheckCancel;
  }
}
