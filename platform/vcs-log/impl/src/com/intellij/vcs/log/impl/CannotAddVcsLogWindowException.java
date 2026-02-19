// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class CannotAddVcsLogWindowException extends RuntimeException {
  public CannotAddVcsLogWindowException(@NotNull String message, @Nullable Throwable cause) {
    super(message, cause);
  }
}
