// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data;

import org.jetbrains.annotations.NonNls;

class VcsLogRefreshNotEnoughDataException extends RuntimeException {

  private static final @NonNls String NOT_ENOUGH_FIRST_BLOCK = "Not enough first block";

  VcsLogRefreshNotEnoughDataException() {
    super(NOT_ENOUGH_FIRST_BLOCK);
  }
}
