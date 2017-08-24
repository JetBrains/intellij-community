/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.progress.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PairConsumer;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class BackgroundTaskUtil {
  private static final Logger LOG = Logger.getInstance(BackgroundTaskUtil.class);

  /**
   * Executor to perform <i>possibly</i> long operation on pooled thread.
   * If computation was performed within given time frame,
   * the computed callback will be executed synchronously (avoiding unnecessary <tt>invokeLater()</tt>).
   * In this case, {@code onSlowAction} will not be executed at all.
   * <ul>
   * <li> If the computation is fast, execute callback synchronously.
   * <li> If the computation is slow, execute <tt>onSlowAction</tt> synchronously. When the computation is completed, execute callback in EDT.
   * </ul><p>
   * It can be used to reduce blinking when background task might be completed fast.<br>
   * A Simple approach:
   * <pre>
   * onSlowAction.run() // show "Loading..."
   * executeOnPooledThread({
   *   Runnable callback = backgroundTask(); // some background computations
   *   invokeLater(callback); // apply changes
   * });
   * </pre>
   * will lead to "Loading..." visible between current moment and execution of invokeLater() event.
   * This period can be very short and looks like 'jumping' if background operation is fast.
   */
  @NotNull
  @CalledInAwt
  public static ProgressIndicator executeAndTryWait(@NotNull Function<ProgressIndicator, /*@NotNull*/ Runnable> backgroundTask,
                                                    @Nullable Runnable onSlowAction,
                                                    long waitMillis) {
    return executeAndTryWait(backgroundTask, onSlowAction, waitMillis, false);
  }

  @NotNull
  @CalledInAwt
  public static ProgressIndicator executeAndTryWait(@NotNull Function<ProgressIndicator, /*@NotNull*/ Runnable> backgroundTask,
                                                    @Nullable Runnable onSlowAction,
                                                    long waitMillis,
                                                    boolean forceEDT) {
    ModalityState modality = ModalityState.current();

    if (forceEDT) {
      ProgressIndicator indicator = new EmptyProgressIndicator(modality);
      try {
        Runnable callback = backgroundTask.fun(indicator);
        finish(callback, indicator);
      }
      catch (ProcessCanceledException ignore) {
      }
      catch (Throwable t) {
        LOG.error(t);
      }
      return indicator;
    }
    else {
      Pair<Runnable, ProgressIndicator> pair = computeInBackgroundAndTryWait(
        backgroundTask,
        (callback, indicator) -> {
          ApplicationManager.getApplication().invokeLater(() -> {
            finish(callback, indicator);
          }, modality);
        },
        modality,
        waitMillis);

      Runnable callback = pair.first;
      ProgressIndicator indicator = pair.second;

      if (callback != null) {
        finish(callback, indicator);
      }
      else {
        if (onSlowAction != null) onSlowAction.run();
      }

      return indicator;
    }
  }

  @CalledInAwt
  private static void finish(@NotNull Runnable result, @NotNull ProgressIndicator indicator) {
    if (!indicator.isCanceled()) result.run();
  }

  /**
   * Try to compute value in background and abort computation if it takes too long.
   * <ul>
   * <li> If the computation is fast, return computed value.
   * <li> If the computation is slow, abort computation (cancel ProgressIndicator).
   * </ul>
   */
  @Nullable
  @CalledInAwt
  public static <T> T tryComputeFast(@NotNull Function<ProgressIndicator, T> backgroundTask,
                                     long waitMillis) {
    Pair<T, ProgressIndicator> pair = computeInBackgroundAndTryWait(
      backgroundTask,
      (result, indicator) -> {
      },
      ModalityState.defaultModalityState(),
      waitMillis);

    T result = pair.first;
    ProgressIndicator indicator = pair.second;

    indicator.cancel();
    return result;
  }

  @Nullable
  @CalledInAny
  public static <T> T computeInBackgroundAndTryWait(@NotNull Computable<T> computable,
                                                    @NotNull Consumer<T> asyncCallback,
                                                    long waitMillis) {
    Pair<T, ProgressIndicator> pair = computeInBackgroundAndTryWait(
      indicator -> computable.compute(),
      (result, indicator) -> asyncCallback.consume(result),
      ModalityState.defaultModalityState(),
      waitMillis
    );
    return pair.first;
  }

  /**
   * Compute value in background and try wait for its completion.
   * <ul>
   * <li> If the computation is fast, return computed value synchronously. Callback will not be called in this case.
   * <li> If the computation is slow, return <tt>null</tt>. When the computation is completed, pass the value to the callback.
   * </ul>
   * Callback will be executed on the same thread as the background task.
   */
  @NotNull
  @CalledInAny
  public static <T> Pair<T, ProgressIndicator> computeInBackgroundAndTryWait(@NotNull Function<ProgressIndicator, T> task,
                                                                             @NotNull PairConsumer<T, ProgressIndicator> asyncCallback,
                                                                             @NotNull ModalityState modality,
                                                                             long waitMillis) {
    ProgressIndicator indicator = new EmptyProgressIndicator(modality);

    Helper<T> helper = new Helper<>();

    indicator.start();
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      ProgressManager.getInstance().executeProcessUnderProgress(() -> {
        try {
          T result = task.fun(indicator);
          if (!helper.setResult(result)) {
            asyncCallback.consume(result, indicator);
          }
        }
        finally {
          indicator.stop();
        }
      }, indicator);
    });

    T result = null;
    if (helper.await(waitMillis)) {
      result = helper.getResult();
    }

    return Pair.create(result, indicator);
  }


  /**
   * An alternative to plain {@link Application#executeOnPooledThread(Runnable)} which wraps the task in a process with a
   * {@link ProgressIndicator} which gets cancelled when the given disposable is disposed. <br/><br/>
   *
   * This allows to stop a lengthy background activity by calling {@link ProgressManager#checkCanceled()}
   * and avoid Already Disposed exceptions (in particular, because checkCanceled() is called in {@link ServiceManager#getService(Class)}.
   */
  @NotNull
  @CalledInAny
  public static ProgressIndicator executeOnPooledThread(@NotNull Disposable parent, @NotNull Runnable runnable) {
    return executeOnPooledThread(parent, indicator -> runnable.run());
  }

  @NotNull
  @CalledInAny
  public static ProgressIndicator executeOnPooledThread(@NotNull Disposable parent, @NotNull Consumer<ProgressIndicator> task) {
    ModalityState modalityState = ModalityState.defaultModalityState();
    return executeOnPooledThread(parent, modalityState, task);
  }

  @NotNull
  @CalledInAny
  public static ProgressIndicator executeOnPooledThread(@NotNull Disposable parent,
                                                        @NotNull ModalityState modalityState,
                                                        @NotNull Consumer<ProgressIndicator> task) {
    return runUnderDisposeAwareIndicator(parent, modalityState, true, task);
  }

  @CalledInAny
  private static ProgressIndicator runUnderDisposeAwareIndicator(@NotNull Disposable parent,
                                                                 @NotNull ModalityState modalityState,
                                                                 boolean onPooledThread,
                                                                 @NotNull Consumer<ProgressIndicator> task) {
    ProgressIndicator indicator = new EmptyProgressIndicator(modalityState);

    Disposable disposable = new Disposable() {
      @Override
      public void dispose() {
        if (indicator.isRunning()) indicator.cancel();
      }
    };

    try {
      Disposer.register(parent, disposable);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      indicator.cancel();
      return indicator;
    }

    indicator.start();

    Runnable underProgress = () -> ProgressManager.getInstance().executeProcessUnderProgress(() -> {
      try {
        task.consume(indicator);
      }
      finally {
        indicator.stop();
        Disposer.dispose(disposable);
      }
    }, indicator);

    if (onPooledThread) ApplicationManager.getApplication().executeOnPooledThread(underProgress);
    else underProgress.run();

    return indicator;
  }

  @CalledInAny
  public static void runUnderDisposeAwareIndicator(@NotNull Disposable parent, @NotNull Runnable task) {
    runUnderDisposeAwareIndicator(parent, ModalityState.defaultModalityState(), false, indicator -> task.run());
  }

  /**
   * Wraps {@link MessageBus#syncPublisher(Topic)} in a dispose check,
   * and throws a {@link ProcessCanceledException} if the project is disposed,
   * instead of throwing an assertion which would happen otherwise.
   */
  @CalledInAny
  @NotNull
  public static <L> L syncPublisher(@NotNull Project project, @NotNull Topic<L> topic) throws ProcessCanceledException {
    return ReadAction.compute(() -> {
      if (project.isDisposed()) throw new ProcessCanceledException();
      return project.getMessageBus().syncPublisher(topic);
    });
  }


  private static class Helper<T> {
    private static final Object INITIAL_STATE = new Object();
    private static final Object SLOW_OPERATION_STATE = new Object();

    private final Semaphore mySemaphore = new Semaphore(0);
    private final AtomicReference<Object> myResultRef = new AtomicReference<>(INITIAL_STATE);

    /**
     * @return true if computation was fast, and callback should be handled by other thread
     */
    public boolean setResult(T result) {
      boolean isFast = myResultRef.compareAndSet(INITIAL_STATE, result);
      mySemaphore.release();
      return isFast;
    }

    /**
     * @return true if computation was fast, and callback should be handled by current thread
     */
    public boolean await(long waitMillis) {
      try {
        mySemaphore.tryAcquire(waitMillis, TimeUnit.MILLISECONDS);
      }
      catch (InterruptedException ignore) {
      }

      return !myResultRef.compareAndSet(INITIAL_STATE, SLOW_OPERATION_STATE);
    }

    public T getResult() {
      Object result = myResultRef.get();
      assert result != INITIAL_STATE && result != SLOW_OPERATION_STATE;
      //noinspection unchecked
      return (T)result;
    }
  }
}
