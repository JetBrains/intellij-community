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
package com.intellij.openapi.progress.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.ui.AppUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;

import java.util.concurrent.Executor;

/**
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

  public static void scheduleWithWriteActionPriority(@NotNull final ProgressIndicator progressIndicator,
                                                     @NotNull final Executor executor,
                                                     @NotNull final ReadTask readTask) {
    AppUIUtil.invokeOnEdt(new Runnable() {
      @Override
      public void run() {
        final Application application = ApplicationManager.getApplication();
        application.assertIsDispatchThread();
        final ApplicationAdapter listener = new ApplicationAdapter() {
          @Override
          public void beforeWriteActionStart(Object action) {
            progressIndicator.cancel();
          }
        };
        application.addApplicationListener(listener);
        try {
          executor.execute(new Runnable() {
            @Override
            public void run() {
              try {
                runUnderProgress(progressIndicator, readTask);
              }
              finally {
                application.removeApplicationListener(listener);
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

  private static void runUnderProgress(@NotNull final ProgressIndicator progressIndicator, @NotNull final ReadTask task) {
    ProgressManager.getInstance().runProcess(new Runnable() {
      @Override
      public void run() {
        // This read action can possible last for a long time, we want it to stop immediately on the first write access.
        // For this purpose we launch it under empty progress and invoke progressIndicator#cancel on write access to avoid possible write lock delays.
        try {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            @Override
            public void run() {
              task.computeInReadAction(progressIndicator);
            }
          });
        }
        catch (ProcessCanceledException ignore) {
        }
        finally {
          if (progressIndicator.isCanceled()) {
            task.onCanceled(progressIndicator);
          }
        }
      }
    }, progressIndicator);
  }
}
