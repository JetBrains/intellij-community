// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.java.decompiler.main.CancellationManager;

@ApiStatus.Experimental
public class IdeaCancellationManager extends CancellationManager.TimeoutCancellationManager {

  public IdeaCancellationManager(int maxMethodTimeoutSec) {
    super(maxMethodTimeoutSec);
  }

  @Override
  public void checkCanceled() throws CanceledException {
    try {
      ProgressManager.checkCanceled();
    }
    catch (ProcessCanceledException e) {
      throw new CanceledException(e);
    }
    super.checkCanceled();
  }
}
