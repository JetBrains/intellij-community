// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public interface XValueTextProvider {
  @Nullable
  String getValueText();

  @ApiStatus.Experimental
  boolean shouldShowTextValue();

  /**
   * Async version of the same provider, which is completed when data is ready to be provided.
   * Original methods might return conservative results.
   */
  @ApiStatus.Internal
  default @NotNull CompletableFuture<@NotNull XValueTextProvider> getValueTextProviderAsync() {
    return CompletableFuture.completedFuture(this);
  }

}