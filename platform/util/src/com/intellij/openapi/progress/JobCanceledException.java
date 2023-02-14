// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CancellationException;

/**
 * This has to be an inheritor of PCE, so that the code expecting PCE from ProgressManager#checkCanceled will continue to work.
 */
@Internal
public final class JobCanceledException extends ProcessCanceledException {

  JobCanceledException(@NotNull CancellationException e) {
    super(e);
  }

  @Override
  public @NotNull CancellationException getCause() {
    return (CancellationException)super.getCause();
  }
}
