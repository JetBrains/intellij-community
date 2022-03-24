// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.PingProgress;
import com.intellij.util.io.IOCancellationCallback;

public final class IdeIOCancellationCallback implements IOCancellationCallback {
  @Override
  public void checkCancelled() throws ProcessCanceledException {
    ProgressManager.checkCanceled();
  }

  @Override
  public void interactWithUI() {
    PingProgress.interactWithEdtProgress();
  }
}
