// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CancellationException;

/**
 * {@link CancellationException} to {@link ProcessCanceledException} adapter.
 *
 * @see PceCancellationException
 */
@Internal
public final class CeProcessCanceledException extends ProcessCanceledException {

  public CeProcessCanceledException(@NotNull CancellationException e) {
    super(e);
  }

  @Override
  public synchronized @NotNull CancellationException getCause() {
    return (CancellationException)super.getCause();
  }
}
