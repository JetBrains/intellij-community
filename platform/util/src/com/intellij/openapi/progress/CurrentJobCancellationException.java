// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CancellationException;

@Internal
public final class CurrentJobCancellationException extends CancellationException {

  CurrentJobCancellationException(@NotNull JobCanceledException e) {
    initCause(e);
  }

  @Override
  public @NotNull JobCanceledException getCause() {
    return (JobCanceledException)super.getCause();
  }

  public @NotNull CancellationException getOriginalCancellationException() {
    return getCause().getCause();
  }
}
