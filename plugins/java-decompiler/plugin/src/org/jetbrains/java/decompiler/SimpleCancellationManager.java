// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.java.decompiler.main.CancellationManager;

@ApiStatus.Experimental
public class SimpleCancellationManager implements CancellationManager {

  private volatile ProcessCanceledException exception = null;

  @Override
  public void checkCanceled() throws CanceledException {
    try {
      ProgressManager.checkCanceled();
    }
    catch (ProcessCanceledException e) {
      exception = e;
      throw new CanceledException(e);
    }
  }

  @Override
  public void saveCancelled() {
    if (exception != null) {
      return;
    }
    try {
      checkCanceled();
    }
    catch (CanceledException e) {
      if (e.getCause() instanceof ProcessCanceledException) {
        exception = (ProcessCanceledException)e.getCause();
      }
    }
  }

  @Override
  public void checkSavedCancelled() throws CanceledException {
    ProcessCanceledException currentException = exception;
    if (currentException != null) {
      throw new CanceledException(currentException);
    }
  }
}
