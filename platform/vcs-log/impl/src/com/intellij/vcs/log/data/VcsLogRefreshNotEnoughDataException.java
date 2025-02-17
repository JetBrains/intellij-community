// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;

@ApiStatus.Internal
public final class VcsLogRefreshNotEnoughDataException extends RuntimeException {
  private static final @NonNls String NOT_ENOUGH_FIRST_BLOCK = "Not enough first block";

  VcsLogRefreshNotEnoughDataException() {
    super(NOT_ENOUGH_FIRST_BLOCK);
  }
}
