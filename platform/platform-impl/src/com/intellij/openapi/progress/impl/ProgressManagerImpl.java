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
package com.intellij.openapi.progress.impl;

import com.google.common.collect.ConcurrentHashMultiset;
import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.progress.util.SmoothProgressAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.psi.PsiLock;
import com.intellij.ui.SystemNotifications;
import com.intellij.util.containers.*;
import gnu.trove.THashMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ProgressManagerImpl extends ProgressManager implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.progress.impl.ProgressManagerImpl");
  public static final int CHECK_CANCELED_DELAY_MILLIS = 10;
  private final AtomicInteger myCurrentUnsafeProgressCount = new AtomicInteger(0);
  private final AtomicInteger myCurrentModalProgressCount = new AtomicInteger(0);

  private static volatile int ourLockedCheckCounter = 0;
  private static final boolean DISABLED = "disabled".equals(System.getProperty("idea.ProcessCanceledException"));
  private final ScheduledFuture<?> myCheckCancelledFuture;

  // indicator -> threads which are running under this indicator. guarded by this.
  private static final Map<ProgressIndicator, Set<Thread>> threadsUnderIndicator = new THashMap<ProgressIndicator, Set<Thread>>();
  // the active indicator for the thread id
  private static final ConcurrentLongObjectMap<ProgressIndicator> currentIndicators = ContainerUtil.createConcurrentLongObjectMap();
  // threads which are running under canceled indicator
  static final Set<Thread> threadsUnderCanceledIndicator = new ConcurrentHashSet<Thread>();

  // active (i.e. which have executeProcessUnderProgress() method running) indicators which are not inherited from StandardProgressIndicator.
  // for them an extra processing thread (see myCheckCancelledFuture) has to be run to call their non-standard checkCanceled() method
  private static final Collection<ProgressIndicator> nonStandardIndicators = ConcurrentHashMultiset.create();

  public ProgressManagerImpl() {
    myCheckCancelledFuture = JobScheduler.getScheduler().scheduleWithFixedDelay(new Runnable() {
      @Override
      public void run() {
        for (ProgressIndicator indicator : nonStandardIndicators) {
          try {
            indicator.checkCanceled();
          }
          catch (ProcessCanceledException e) {
            indicatorCanceled(indicator);
          }
        }
      }
    }, 0, CHECK_CANCELED_DELAY_MILLIS, TimeUnit.MILLISECONDS);

  }

  @Override
  public void dispose() {
    myCheckCancelledFuture.cancel(true);
  }

  @Override
  protected void doCheckCanceled() throws ProcessCanceledException {
    boolean thereIsCanceledIndicator = !threadsUnderCanceledIndicator.isEmpty();
    if (thereIsCanceledIndicator) {
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
            }
          }
          else {
            ourLockedCheckCounter = 0;
            throw e;
          }
        }
      }
    }
  }

  @NotNull
  @Override
  public final NonCancelableSection startNonCancelableSection() {
    final ProgressIndicator myOld = ProgressManager.getInstance().getProgressIndicator();

    final Thread currentThread = Thread.currentThread();
    NonCancelableIndicator nonCancelor = new NonCancelableIndicator() {
      @Override
      public void done() {
        setCurrentIndicator(currentThread, myOld);
      }
    };
    setCurrentIndicator(currentThread, nonCancelor);
    return nonCancelor;
  }

  @Override
  public void executeNonCancelableSection(@NotNull Runnable runnable) {
    executeProcessUnderProgress(runnable, new NonCancelableIndicator());
  }

  @Override
  public void setCancelButtonText(String cancelButtonText) {
    ProgressIndicator progressIndicator = getProgressIndicator();
    if (progressIndicator != null) {
      if (progressIndicator instanceof SmoothProgressAdapter && cancelButtonText != null) {
        ProgressIndicator original = ((SmoothProgressAdapter)progressIndicator).getOriginalProgressIndicator();
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
          try {
            if (progress != null && !progress.isRunning()) {
              progress.start();
            }
          }
          catch (Throwable e) {
            LOG.info("Unexpected error when starting progress: ", e);
            throw new RuntimeException(e);
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
      ProgressIndicator oldIndicator = null;
      boolean set = progress != null && progress != (oldIndicator = getProgressIndicator());
      if (set) {
        Thread currentThread = Thread.currentThread();
        setCurrentIndicator(currentThread, progress);
        try {
          registerIndicatorAndRun(progress, currentThread, oldIndicator, process);
        }
        finally {
          setCurrentIndicator(currentThread, oldIndicator);
        }
      }
      else {
        process.run();
      }
    }
    finally {
      if (progress == null || progress instanceof ProgressWindow) myCurrentUnsafeProgressCount.decrementAndGet();
      if (modal) myCurrentModalProgressCount.decrementAndGet();
    }
  }

  private static void registerIndicatorAndRun(@NotNull ProgressIndicator indicator,
                                              @NotNull Thread currentThread,
                                              ProgressIndicator oldIndicator,
                                              @NotNull Runnable process) {
    Set<Thread> underIndicator;
    boolean alreadyUnder;
    boolean isStandard;
    synchronized (threadsUnderIndicator) {
      underIndicator = threadsUnderIndicator.get(indicator);
      if (underIndicator == null) {
        underIndicator = new SmartHashSet<Thread>();
        threadsUnderIndicator.put(indicator, underIndicator);
      }
      alreadyUnder = !underIndicator.add(currentThread);
      isStandard = indicator instanceof StandardProgressIndicator;
      if (!isStandard) {
        nonStandardIndicators.add(indicator);
      }

      if (indicator.isCanceled()) {
        threadsUnderCanceledIndicator.add(currentThread);
      }
      else {
        threadsUnderCanceledIndicator.remove(currentThread);
      }
    }

    try {
      if (indicator instanceof WrappedProgressIndicator) {
        registerIndicatorAndRun(((WrappedProgressIndicator)indicator).getOriginalProgressIndicator(), currentThread, oldIndicator, process);
      }
      else {
        process.run();
      }
    }
    finally {
      synchronized (threadsUnderIndicator) {
        boolean removed = alreadyUnder || underIndicator.remove(currentThread);
        if (removed && underIndicator.isEmpty()) {
          threadsUnderIndicator.remove(indicator);
        }
        if (!isStandard) {
          nonStandardIndicators.remove(indicator);
        }
        // by this time oldIndicator may have been canceled
        if (oldIndicator != null && oldIndicator.isCanceled()) {
          threadsUnderCanceledIndicator.add(currentThread);
        }
        else {
          threadsUnderCanceledIndicator.remove(currentThread);
        }
      }
    }
  }

  @TestOnly
  public static void runWithAlwaysCheckingCanceled(@NotNull Runnable runnable) {
    Thread fake = new Thread();
    try {
      threadsUnderCanceledIndicator.add(fake);
      runnable.run();
    }
    finally {
      threadsUnderCanceledIndicator.remove(fake);
    }
  }

  @Override
  protected void indicatorCanceled(@NotNull ProgressIndicator indicator) {
    // mark threads running under this indicator as canceled
    synchronized (threadsUnderIndicator) {
      Set<Thread> threads = threadsUnderIndicator.get(indicator);
      if (threads != null) {
        for (Thread thread : threads) {
          ProgressIndicator currentIndicator = getCurrentIndicator(thread);
          if (currentIndicator == indicator) {
            threadsUnderCanceledIndicator.add(thread);
          }
        }
      }
    }
  }

  private static void setCurrentIndicator(@NotNull Thread currentThread, ProgressIndicator indicator) {
    if (indicator == null) {
      currentIndicators.remove(currentThread.getId());
    }
    else {
      currentIndicators.put(currentThread.getId(), indicator);
    }
  }
  private static ProgressIndicator getCurrentIndicator(@NotNull Thread thread) {
    return currentIndicators.get(thread.getId());
  }

  @Override
  public ProgressIndicator getProgressIndicator() {
    return getCurrentIndicator(Thread.currentThread());
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
    final AtomicReference<T> result = new AtomicReference<T>();
    final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();

    runProcessWithProgressSynchronously(new Task.Modal(project, progressTitle, canBeCanceled) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          T compute = process.compute();
          result.set(compute);
        }
        catch (Throwable t) {
          exception.set(t);
        }
      }
    }, null);

    Throwable t = exception.get();
    if (t != null) {
      if (t instanceof Error) throw (Error)t;
      if (t instanceof RuntimeException) throw (RuntimeException)t;
      @SuppressWarnings("unchecked") E e = (E)t;
      throw e;
    }

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
        if (frame != null && !frame.hasFocus()) {
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

  @NotNull
  public static Future<?> runProcessWithProgressAsynchronously(@NotNull Task.Backgroundable task) {
    final ProgressIndicator progressIndicator;
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      progressIndicator = new EmptyProgressIndicator();
    }
    else {
      progressIndicator = new BackgroundableProcessIndicator(task);
    }
    return runProcessWithProgressAsynchronously(task, progressIndicator, null);
  }

  @Override
  public void runProcessWithProgressAsynchronously(@NotNull Task.Backgroundable task, @NotNull ProgressIndicator progressIndicator) {
    runProcessWithProgressAsynchronously(task, progressIndicator, null);
  }

  @NotNull
  public static Future<?> runProcessWithProgressAsynchronously(@NotNull final Task.Backgroundable task,
                                                               @NotNull final ProgressIndicator progressIndicator,
                                                               @Nullable final Runnable continuation) {
    return runProcessWithProgressAsynchronously(task, progressIndicator, continuation, ModalityState.NON_MODAL);
  }

  @NotNull
  public static Future<?> runProcessWithProgressAsynchronously(@NotNull final Task.Backgroundable task,
                                                               @NotNull final ProgressIndicator progressIndicator,
                                                               @Nullable final Runnable continuation,
                                                               @NotNull final ModalityState modalityState) {
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
          }, modalityState);
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
          }, modalityState);
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
}
