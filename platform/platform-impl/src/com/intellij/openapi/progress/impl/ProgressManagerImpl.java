/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.progress.util.SmoothProgressAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.psi.PsiLock;
import com.intellij.ui.SystemNotifications;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ProgressManagerImpl extends ProgressManager implements Disposable{
  private final AtomicInteger myCurrentUnsafeProgressCount = new AtomicInteger(0);
  private final AtomicInteger myCurrentModalProgressCount = new AtomicInteger(0);

  private static volatile int ourLockedCheckCounter = 0;
  private static final boolean DISABLED = "disabled".equals(System.getProperty("idea.ProcessCanceledException"));
  private final ScheduledFuture<?> myCheckCancelledFuture;

  public ProgressManagerImpl(Application application) {
    if (/*!application.isUnitTestMode() && */!DISABLED) {
      myCheckCancelledFuture = JobScheduler.getScheduler().scheduleWithFixedDelay(new Runnable() {
        @Override
        public void run() {
          ourNeedToCheckCancel = true;
          ProgressIndicatorProvider.ourNeedToCheckCancel = true;
        }
      }, 0, 10, TimeUnit.MILLISECONDS);
    }
    else {
      myCheckCancelledFuture = null;
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
            ProgressIndicatorProvider.ourNeedToCheckCancel = true;
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
    ProgressIndicatorProvider.ourNeedToCheckCancel = true;
  }

  private static class NonCancelableIndicator extends EmptyProgressIndicator implements NonCancelableSection {
    private final ProgressIndicator myOld;

    private NonCancelableIndicator(ProgressIndicator old) {
      myOld = old;
    }

    @Override
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

  @Override
  public final NonCancelableSection startNonCancelableSection() {
    NonCancelableIndicator nonCancelor = new NonCancelableIndicator(myThreadIndicator.get());
    myThreadIndicator.set(nonCancelor);
    return nonCancelor;
  }

  @Override
  public void executeNonCancelableSection(@NotNull Runnable runnable) {
    executeProcessUnderProgress(runnable, new NonCancelableIndicator(getProgressIndicator()));
  }

  @Override
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

  @Override
  public boolean hasProgressIndicator() {
    return getProgressIndicator() != null;
  }

  @Override
  public boolean hasUnsafeProgressIndicator() {
    return myCurrentUnsafeProgressCount.get() > 0;
  }

  @Override
  public boolean hasModalProgressIndicator() {
    return myCurrentModalProgressCount.get() > 0;
  }

  @Override
  public void runProcess(@NotNull final Runnable process, final ProgressIndicator progress) {
    executeProcessUnderProgress(new Runnable(){
      @Override
      public void run() {
        try {
          if (progress != null && !progress.isRunning()) {
            progress.start();
          }
          process.run();
          maybeSleep();
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

  @Override
  public <T> T runProcess(@NotNull final Computable<T> process, ProgressIndicator progress) throws ProcessCanceledException {
    final Ref<T> ref = new Ref<T>();
    runProcess(new Runnable() {
      @Override
      public void run() {
        ref.set(process.compute());
      }
    }, progress);
    return ref.get();
  }

  @Override
  public void executeProcessUnderProgress(@NotNull Runnable process, ProgressIndicator progress) throws ProcessCanceledException {
    boolean modal = progress != null && progress.isModal();
    if (modal) myCurrentModalProgressCount.incrementAndGet();
    if (progress == null || progress instanceof ProgressWindow) myCurrentUnsafeProgressCount.incrementAndGet();

    try {
      super.executeProcessUnderProgress(process, progress);
    }
    finally {
      if (progress == null || progress instanceof ProgressWindow) myCurrentUnsafeProgressCount.decrementAndGet();
      if (modal) myCurrentModalProgressCount.decrementAndGet();
    }
  }

  @Override
  public boolean runProcessWithProgressSynchronously(@NotNull final Runnable process,
                                                     @NotNull String progressTitle,
                                                     boolean canBeCanceled,
                                                     @Nullable Project project) {
    return runProcessWithProgressSynchronously(process, progressTitle, canBeCanceled, project, null);
  }

  @Override
  public <T, E extends Exception> T runProcessWithProgressSynchronously(@NotNull final ThrowableComputable<T, E> process,
                                                   @NotNull @Nls String progressTitle,
                                                   boolean canBeCanceled,
                                                   @Nullable Project project) throws E {

    final Ref<T> result = new Ref<T>();
    final Ref<E> exceptionRef = new Ref<E>();
    Task.Modal task = new Task.Modal(project, progressTitle, canBeCanceled) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          T compute = process.compute();
          result.set(compute);
        }
        catch (Exception e) {
          exceptionRef.set((E)e);
        }
      }
    };
    runProcessWithProgressSynchronously(task, null);
    if (!exceptionRef.isNull()) throw exceptionRef.get();
    return result.get();
  }

  @Override
  public boolean runProcessWithProgressSynchronously(@NotNull final Runnable process,
                                                     @NotNull String progressTitle,
                                                     boolean canBeCanceled,
                                                     @Nullable Project project,
                                                     @Nullable JComponent parentComponent) {
    Task.Modal task = new Task.Modal(project, progressTitle, canBeCanceled) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        process.run();
      }
    };
    return runProcessWithProgressSynchronously(task, parentComponent);
  }

  private static boolean runProcessWithProgressSynchronously(@NotNull final Task task, @Nullable final JComponent parentComponent) {
    final long start = System.currentTimeMillis();
    final boolean result = ((ApplicationEx)ApplicationManager.getApplication())
        .runProcessWithProgressSynchronously(new TaskContainer(task) {
          @Override
          public void run() {
            new TaskRunnable(task, ProgressManager.getInstance().getProgressIndicator()).run();
          }
        }, task.getTitle(), task.isCancellable(), task.getProject(), parentComponent, task.getCancelText());
    if (result) {
      final long end = System.currentTimeMillis();
      final Task.NotificationInfo notificationInfo = task.notifyFinished();
      long time = end - start;
      if (notificationInfo != null && time > 5000) { // show notification only if process took more than 5 secs
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
    SystemNotifications.getInstance().notify(notificationInfo.getNotificationName(),
                                             notificationInfo.getNotificationTitle(),
                                             notificationInfo.getNotificationText());
  }

  @Override
  public void runProcessWithProgressAsynchronously(@NotNull Project project,
                                                   @NotNull String progressTitle,
                                                   @NotNull final Runnable process,
                                                   @Nullable final Runnable successRunnable,
                                                   @Nullable final Runnable canceledRunnable) {
    runProcessWithProgressAsynchronously(project, progressTitle, process, successRunnable, canceledRunnable, PerformInBackgroundOption.DEAF);
  }

  @Override
  public void runProcessWithProgressAsynchronously(@NotNull final Project project,
                                                   @Nls @NotNull final String progressTitle,
                                                   @NotNull final Runnable process,
                                                   @Nullable final Runnable successRunnable,
                                                   @Nullable final Runnable canceledRunnable,
                                                   @NotNull final PerformInBackgroundOption option) {

    runProcessWithProgressAsynchronously(new Task.Backgroundable(project, progressTitle, true, option) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        process.run();
      }


      @Override
      public void onCancel() {
        if (canceledRunnable != null) {
          canceledRunnable.run();
        }
      }

      @Override
      public void onSuccess() {
        if (successRunnable != null) {
          successRunnable.run();
        }
      }
    });
  }

  private static void runProcessWithProgressAsynchronously(@NotNull Task.Backgroundable task) {
    final ProgressIndicator progressIndicator;
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      progressIndicator = new EmptyProgressIndicator();
    }
    else {
      progressIndicator = new BackgroundableProcessIndicator(task);
    }
    runProcessWithProgressAsynchronously(task, progressIndicator, null);
  }

  @Override
  public void runProcessWithProgressAsynchronously(@NotNull Task.Backgroundable task, @NotNull ProgressIndicator progressIndicator) {
    runProcessWithProgressAsynchronously(task, progressIndicator, null);
  }

  @NotNull
  public static Future<?> runProcessWithProgressAsynchronously(@NotNull final Task.Backgroundable task,
                                                               @NotNull final ProgressIndicator progressIndicator,
                                                               @Nullable final Runnable continuation) {
    if (progressIndicator instanceof Disposable) {
      Disposer.register(ApplicationManager.getApplication(), (Disposable)progressIndicator);
    }

    final Runnable process = new TaskRunnable(task, progressIndicator, continuation);

    TaskContainer action = new TaskContainer(task) {
      @Override
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
        final long time = end - start;

        if (canceled || progressIndicator.isCanceled()) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              task.onCancel();
            }
          }, ModalityState.NON_MODAL);
        }
        else {
          final Task.NotificationInfo notificationInfo = task.notifyFinished();
          if (notificationInfo != null && time > 5000) { // snow notification if process took more than 5 secs
            final Component window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
            if (window == null || notificationInfo.isShowWhenFocused()) {
              systemNotify(notificationInfo);
            }
          }
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              task.onSuccess();
            }
          }, ModalityState.NON_MODAL);
        }
      }
    };

    return ApplicationManager.getApplication().executeOnPooledThread(action);
  }

  @Override
  public void run(@NotNull final Task task) {
    if (task.isHeadless()) {
      if (ApplicationManager.getApplication().isDispatchThread()) {
        runProcessWithProgressSynchronously(task, null);
      }
      else {
        new TaskRunnable(task, new EmptyProgressIndicator()).run();
      }
    }
    else if (task.isModal()) {
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

    protected TaskContainer(@NotNull Task task) {
      myTask = task;
    }

    @NotNull
    public Task getTask() {
      return myTask;
    }
  }

  private static class TaskRunnable extends TaskContainer {
    private final ProgressIndicator myIndicator;
    private final Runnable myContinuation;

    private TaskRunnable(@NotNull Task task, @NotNull ProgressIndicator indicator) {
      this(task, indicator, null);
    }

    private TaskRunnable(@NotNull Task task, @NotNull ProgressIndicator indicator, @Nullable Runnable continuation) {
      super(task);
      myIndicator = indicator;
      myContinuation = continuation;
    }

    @Override
    public void run() {
      try {
        getTask().run(myIndicator);
      }
      finally {
        try {
          if (myIndicator instanceof ProgressIndicatorEx) {
            ((ProgressIndicatorEx)myIndicator).finish(getTask());
          }
        }
        finally {
          if (myContinuation != null) {
            myContinuation.run();
          }
        }
      }
    }
  }

  @Override
  public void dispose() {
    stopCheckCanceled();
  }

  private void stopCheckCanceled() {
    if (myCheckCancelledFuture != null) myCheckCancelledFuture.cancel(false);
  }

  @TestOnly
  @SuppressWarnings({"UnusedDeclaration"})
  public static String isCanceledThread(@NotNull Thread thread) {
    try {
      Field th = Thread.class.getDeclaredField("threadLocals");
      th.setAccessible(true);
      Object tLocalMap = th.get(thread);
      if (tLocalMap == null) return null;
      Method getEntry = tLocalMap.getClass().getDeclaredMethod("getEntry", ThreadLocal.class);
      getEntry.setAccessible(true);
      Object entry = getEntry.invoke(tLocalMap, myThreadIndicator);
      if (entry == null) return null;
      Field value = entry.getClass().getDeclaredField("value");
      value.setAccessible(true);
      ProgressIndicator indicator = (ProgressIndicator)value.get(entry);
      if (indicator ==null) return null;
      return String.valueOf(indicator.isCanceled());
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void maybeSleep() {
    final int debugProgressTime = Registry.intValue("ide.debug.minProgressTime");
    if (debugProgressTime > 0) {
      try {
        Thread.sleep(debugProgressTime);
      }
      catch (InterruptedException e) {
        //ignore
      }
    }
  }
}
