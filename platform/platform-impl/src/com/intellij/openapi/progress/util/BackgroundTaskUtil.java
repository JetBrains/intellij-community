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
import com.intellij.openapi.util.EmptyRunnable;
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
  private static final Runnable TOO_SLOW_OPERATION = new EmptyRunnable();

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
  @CalledInAwt
  @NotNull
  public static ProgressIndicator executeAndTryWait(@NotNull final Function<ProgressIndicator, Runnable> backgroundTask,
                                                    @Nullable final Runnable onSlowAction,
                                                    final int waitMillis) {
    return executeAndTryWait(backgroundTask, onSlowAction, waitMillis, false);
  }

  @CalledInAwt
  @NotNull
  public static ProgressIndicator executeAndTryWait(@NotNull final Function<ProgressIndicator, Runnable> backgroundTask,
                                                    @Nullable final Runnable onSlowAction,
                                                    final int waitMillis,
                                                    final boolean forceEDT) {
    final ModalityState modality = ModalityState.current();
    final ProgressIndicator indicator = new EmptyProgressIndicator(modality);

    final Semaphore semaphore = new Semaphore(0);
    final AtomicReference<Runnable> resultRef = new AtomicReference<>();

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
      ApplicationManager.getApplication().executeOnPooledThread(() -> ProgressManager.getInstance().executeProcessUnderProgress(() -> {
        final Runnable callback = backgroundTask.fun(indicator);

        if (indicator.isCanceled()) {
          semaphore.release();
          return;
        }

        if (!resultRef.compareAndSet(null, callback)) {
          ApplicationManager.getApplication().invokeLater(() -> finish(callback, indicator), modality);
        }
        semaphore.release();
      }, indicator));

      try {
        semaphore.tryAcquire(waitMillis, TimeUnit.MILLISECONDS);
      }
      catch (InterruptedException ignore) {
      }
      if (!resultRef.compareAndSet(null, TOO_SLOW_OPERATION)) {
        // update presentation in the same thread to reduce blinking, caused by 'invokeLater' and fast background operation
        finish(resultRef.get(), indicator);
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

  @CalledInAwt
  @Nullable
  public static <T> T tryComputeFast(@NotNull final Function<ProgressIndicator, T> backgroundTask,
                                     final int waitMillis) {
    final Ref<T> resultRef = new Ref<>();
    ProgressIndicator indicator = executeAndTryWait(indicator1 -> {
      final T result = backgroundTask.fun(indicator1);
      return () -> resultRef.set(result);
    }, null, waitMillis, false);
    indicator.cancel();

    return resultRef.get();
  }

  @CalledInAwt
  @NotNull
  public static ProgressIndicator executeOnPooledThread(@NotNull Consumer<ProgressIndicator> task, @NotNull Disposable parent) {
    final ModalityState modalityState = ModalityState.current();
    return executeOnPooledThread(task, parent, modalityState);
  }

  @NotNull
  @CalledInAny
  public static ProgressIndicator executeOnPooledThread(@NotNull final Runnable runnable, @NotNull Disposable parent) {
    return executeOnPooledThread(indicator -> runnable.run(), parent, ModalityState.NON_MODAL);
  }

  @NotNull
  @CalledInAny
  public static ProgressIndicator executeOnPooledThread(@NotNull final Consumer<ProgressIndicator> task,
                                                        @NotNull Disposable parent,
                                                        final ModalityState modalityState) {
    final ProgressIndicator indicator = new EmptyProgressIndicator(modalityState);

    final Disposable disposable = new Disposable() {
      @Override
      public void dispose() {
        if (indicator.isRunning()) indicator.cancel();
      }
    };
    Disposer.register(parent, disposable);
    indicator.start();

    ApplicationManager.getApplication().executeOnPooledThread(() -> ProgressManager.getInstance().executeProcessUnderProgress(() -> {
      try {
        task.consume(indicator);
      }
      finally {
        indicator.stop();
        Disposer.dispose(disposable);
      }
    }, indicator));

    return indicator;
  }
}
