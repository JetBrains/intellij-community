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
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ui.EdtInvocationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

import java.util.concurrent.Executor;

/**
 * Methods in this class are used to equip long background processes which take read actions with a special listener
 * that fires when a write action is about to begin, and cancels corresponding progress indicators to avoid blocking the UI.
 * These processes should be ready to get {@link ProcessCanceledException} at any moment.
 * Processes may want to react on cancellation event by restarting the activity, see
 * {@link ReadTask#onCanceled(ProgressIndicator)} for that.
 *
 * @author gregsh
 */
public class ProgressIndicatorUtils {
  private ProgressIndicatorUtils() {
  }

  @NotNull
  public static ProgressIndicator forceWriteActionPriority(@NotNull final ProgressIndicator progress, @NotNull final Disposable builder) {
    ApplicationManager.getApplication().addApplicationListener(new ApplicationAdapter() {
        @Override
        public void beforeWriteActionStart(Object action) {
          if (progress.isRunning()) {
            progress.cancel();
          }
        }
      }, builder);
    return progress;
  }

  public static void scheduleWithWriteActionPriority(@NotNull ReadTask task) {
    scheduleWithWriteActionPriority(new ProgressIndicatorBase(), task);
  }

  public static void scheduleWithWriteActionPriority(@NotNull ProgressIndicator progressIndicator, @NotNull ReadTask readTask) {
    scheduleWithWriteActionPriority(progressIndicator, PooledThreadExecutor.INSTANCE, readTask);
  }

  /**
   * Same as {@link #runInReadActionWithWriteActionPriority(Runnable)}, optionally allowing to pass a {@link ProgressIndicatorUtils}
   * instance, which can be used to cancel action externally.
   */
  public static boolean runInReadActionWithWriteActionPriority(@NotNull final Runnable action, 
                                                               @Nullable ProgressIndicator progressIndicator) {
    final Ref<Boolean> result = new Ref<Boolean>(Boolean.FALSE);
    runWithWriteActionPriority(new Runnable() {
      @Override
      public void run() {
        result.set(ApplicationManagerEx.getApplicationEx().tryRunReadAction(action));
      }
    }, progressIndicator == null ? new ProgressIndicatorBase() : progressIndicator);
    return result.get();
  }

  /**
   * This method attempts to run provided action synchronously in a read action, so that, if possible, it wouldn't impact any pending, 
   * executing or future write actions (for this to work effectively the action should invoke {@link ProgressManager#checkCanceled()} or 
   * {@link ProgressIndicator#checkCanceled()} often enough). 
   * It returns <code>true</code> if action was executed successfully. It returns <code>false</code> if the action was not
   * executed successfully, i.e. if:
   * <ul>
   * <li>write action was in progress when the method was called</li>
   * <li>write action was pending when the method was called</li>
   * <li>action started to execute, but was aborted using {@link ProcessCanceledException} when some other thread initiated 
   * write action</li>
   * </ul>
   * If caller needs to retry the invocation of this method in a loop, it should consider pausing between attempts, to avoid potential
   * 100% CPU usage.
   */
  public static boolean runInReadActionWithWriteActionPriority(@NotNull final Runnable action) {
    return runInReadActionWithWriteActionPriority(action, null);
  }

  public static boolean runWithWriteActionPriority(@NotNull final Runnable action,
                                                @NotNull final ProgressIndicator progressIndicator) {
    final ApplicationEx application = (ApplicationEx)ApplicationManager.getApplication();

    if (application.isWriteActionPending()) {
      // first catch: check if write action acquisition started: especially important when current thread has read action, because
      // tryRunReadAction below would just run without really checking if a write action is pending
      if (!progressIndicator.isCanceled()) progressIndicator.cancel();
      return false;
    }

    final ApplicationAdapter listener = new ApplicationAdapter() {
      @Override
      public void beforeWriteActionStart(Object action) {
        if (!progressIndicator.isCanceled()) progressIndicator.cancel();
      }
    };

    boolean succeededWithAddingListener = application.tryRunReadAction(new Runnable() {
      @Override
      public void run() {
        // Even if writeLock.lock() acquisition is in progress at this point then runProcess will block wanting read action which is
        // also ok as last resort.
        application.addApplicationListener(listener);
      }
    });
    if (!succeededWithAddingListener) { // second catch: writeLock.lock() acquisition is in progress or already acquired
      if (!progressIndicator.isCanceled()) progressIndicator.cancel();
      return false;
    }
    final Ref<Boolean> wasCancelled = new Ref<Boolean>();
    try {
      ProgressManager.getInstance().runProcess(new Runnable() {
        @Override
        public void run() {
          try {
            action.run();
          }
          catch (ProcessCanceledException ignore) {
            wasCancelled.set(Boolean.TRUE);
          }
        }
      }, progressIndicator);
    }
    finally {
      application.removeApplicationListener(listener);
    }
    return wasCancelled.get() != Boolean.TRUE;
  }

  public static void scheduleWithWriteActionPriority(@NotNull final ProgressIndicator progressIndicator,
                                                     @NotNull final Executor executor,
                                                     @NotNull final ReadTask readTask) {
    final Application application = ApplicationManager.getApplication();
    // invoke later even if on EDT
    // to avoid tasks eagerly restarting immediately, allocating many pooled threads
    // which get cancelled too soon when a next write action arrives in the same EDT batch
    // (can happen when processing multiple VFS events or writing multiple files on save)

    // use SwingUtilities instead of application.invokeLater
    // to tolerate any immediate modality changes (e.g. https://youtrack.jetbrains.com/issue/IDEA-135180)

    //noinspection SSBasedInspection
    EdtInvocationManager.getInstance().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (application.isDisposed()) return;
        final ApplicationAdapter listener = new ApplicationAdapter() {
          @Override
          public void beforeWriteActionStart(Object action) {
            if (!progressIndicator.isCanceled()) {
              progressIndicator.cancel();
              readTask.onCanceled(progressIndicator);
            }
          }
        };
        application.addApplicationListener(listener);
        try {
          executor.execute(new Runnable() {
            @Override
            public void run() {
              boolean continued = false;
              try {
                final ReadTask.Continuation continuation = runUnderProgress(progressIndicator, readTask);
                continued = continuation != null;
                if (continuation != null) {
                  application.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                      application.removeApplicationListener(listener);
                      if (!progressIndicator.isCanceled()) {
                        continuation.getAction().run();
                      }
                    }
                  }, continuation.getModalityState());
                }
              }
              finally {
                if (!continued) {
                  application.removeApplicationListener(listener);
                }
              }
            }
          });
        }
        catch (RuntimeException e) {
          application.removeApplicationListener(listener);
          throw e;
        }
        catch (Error e) {
          application.removeApplicationListener(listener);
          throw e;
        }
      }
    });
  }

  private static ReadTask.Continuation runUnderProgress(@NotNull final ProgressIndicator progressIndicator, @NotNull final ReadTask task) {
    return ProgressManager.getInstance().runProcess(new Computable<ReadTask.Continuation>() {
      @Override
      public ReadTask.Continuation compute() {
        try {
          return task.runBackgroundProcess(progressIndicator);
        }
        catch (ProcessCanceledException ignore) {
          return null;
        }
      }
    }, progressIndicator);
  }
}
