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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class BackgroundTaskUtil {
  private static final Logger LOG = Logger.getInstance(BackgroundTaskUtil.class);

  /*
   * Executor to perform <possibly> long operations on pooled thread
   * It can be used to reduce blinking if background task completed fast. In this case callback will be called without invokeLater().
   *
   * Simple approach:
   *
   * onSlowAction.run() // show "Loading..."
   * executeOnPooledThread({
   *     Runnable callback = backgroundTask(); // some background computations
   *     invokeLater(callback); // apply changes
   *   });
   *
   * will lead to "Loading..." visible between current moment and execution of invokeLater() event.
   * This period can be very short and looks like 'jumping' if background operation is fast.
   */
  @NotNull
  @CalledInAwt
  public static ProgressIndicator executeAndTryWait(@NotNull Function<ProgressIndicator, /*@NotNull*/ Runnable> backgroundTask,
                                                    @Nullable Runnable onSlowAction,
                                                    int waitMillis) {
    return executeAndTryWait(backgroundTask, onSlowAction, waitMillis, false);
  }

  @NotNull
  @CalledInAwt
  public static ProgressIndicator executeAndTryWait(@NotNull Function<ProgressIndicator, /*@NotNull*/ Runnable> backgroundTask,
                                                    @Nullable Runnable onSlowAction,
                                                    int waitMillis,
                                                    boolean forceEDT) {
    ModalityState modality = ModalityState.current();
    ProgressIndicator indicator = new EmptyProgressIndicator(modality);

    if (forceEDT) {
      try {
        Runnable callback = backgroundTask.fun(indicator);
        finish(callback, indicator);
      }
      catch (ProcessCanceledException ignore) {
      }
      catch (Throwable t) {
        LOG.error(t);
      }
    }
    else {
      Helper<Runnable> helper = new Helper<>();

      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        ProgressManager.getInstance().executeProcessUnderProgress(() -> {
          Runnable callback = backgroundTask.fun(indicator);

          if (!helper.setResult(callback)) {
            ApplicationManager.getApplication().invokeLater(() -> {
              finish(callback, indicator);
            }, modality);
          }
        }, indicator);
      });

      if (helper.await(waitMillis)) {
        finish(helper.getResult(), indicator);
      }
      else {
        if (onSlowAction != null) onSlowAction.run();
      }
    }

    return indicator;
  }

  @CalledInAwt
  private static void finish(@NotNull Runnable result, @NotNull ProgressIndicator indicator) {
    if (indicator.isCanceled()) return;
    result.run();
    indicator.stop();
  }

  @Nullable
  @CalledInAwt
  public static <T> T tryComputeFast(@NotNull Function<ProgressIndicator, T> backgroundTask,
                                     int waitMillis) {
    Ref<T> resultRef = new Ref<>();
    ProgressIndicator indicator = executeAndTryWait(indicator1 -> {
      T result = backgroundTask.fun(indicator1);
      return () -> resultRef.set(result);
    }, null, waitMillis, false);
    indicator.cancel();

    return resultRef.get();
  }


  @NotNull
  @CalledInAny
  public static ProgressIndicator executeOnPooledThread(@NotNull Runnable runnable, @NotNull Disposable parent) {
    return executeOnPooledThread(indicator -> runnable.run(), parent);
  }

  @NotNull
  @CalledInAny
  public static ProgressIndicator executeOnPooledThread(@NotNull Consumer<ProgressIndicator> task, @NotNull Disposable parent) {
    ModalityState modalityState = ModalityState.defaultModalityState();
    return executeOnPooledThread(task, parent, modalityState);
  }

  @NotNull
  @CalledInAny
  public static ProgressIndicator executeOnPooledThread(@NotNull Consumer<ProgressIndicator> task,
                                                        @NotNull Disposable parent,
                                                        @NotNull ModalityState modalityState) {
    ProgressIndicator indicator = new EmptyProgressIndicator(modalityState);

    Disposable disposable = new Disposable() {
      @Override
      public void dispose() {
        if (indicator.isRunning()) indicator.cancel();
      }
    };
    Disposer.register(parent, disposable);

    indicator.start();
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      ProgressManager.getInstance().executeProcessUnderProgress(() -> {
        try {
          task.consume(indicator);
        }
        finally {
          indicator.stop();
          Disposer.dispose(disposable);
        }
      }, indicator);
    });

    return indicator;
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
    public boolean await(int waitMillis) {
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
