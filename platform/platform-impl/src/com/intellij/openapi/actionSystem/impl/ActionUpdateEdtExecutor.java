// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.util.Ref;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public final class ActionUpdateEdtExecutor {
  /**
   * Compute the supplied value on Swing thread, but try to avoid deadlocks by periodically performing {@link ProgressManager#checkCanceled()} in the current thread.
   * Makes sense to be used in background read actions running with a progress indicator that's canceled when a write action is about to occur.
   * @see com.intellij.openapi.application.ReadAction#nonBlocking(Runnable)
   */
  public static <T> T computeOnEdt(@NotNull Supplier<T> supplier) {
    Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      return supplier.get();
    }

    Semaphore semaphore = new Semaphore(1);
    ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    Ref<T> result = Ref.create();
    ApplicationManager.getApplication().invokeLater(() -> {
      try {
        if (indicator == null || !indicator.isCanceled()) {
          result.set(supplier.get());
        }
      }
      finally {
        semaphore.up();
      }
    });

    ProgressIndicatorUtils.awaitWithCheckCanceled(semaphore, indicator);

    // check cancellation one last time, to ensure the EDT action wasn't no-op due to cancellation
    ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(indicator);
    return result.get();
  }
}
