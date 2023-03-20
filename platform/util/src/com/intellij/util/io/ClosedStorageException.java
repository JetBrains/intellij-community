// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * A special exception which tells that storage accessed after it has been closed.
 * In this case no need to mark it as corrupted,
 * just propagate this exception or catch it somewhere and rethrow PCE.
 */
@ApiStatus.Internal
public final class ClosedStorageException extends IOException {
  public ClosedStorageException(@NotNull String message) {
    super(message);
  }
}
