// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.appendonlylog;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;

/**
 * Thrown by {@link AppendOnlyLog} then supplied recordId is not a valid recordId -- i.e.,
 * the storage doesn't have a record with such an id.
 */
public class InvalidRecordIdException extends IOException {
  private final long invalidRecordId;

  public InvalidRecordIdException(long invalidRecordId,
                                  @NotNull String message) {
    super(message);
    this.invalidRecordId = invalidRecordId;
  }

  public InvalidRecordIdException(long invalidRecordId,
                                  @NotNull String message,
                                  @Nullable Throwable cause ) {
    super(message, cause);
    this.invalidRecordId = invalidRecordId;
  }

  public long getInvalidRecordId() {
    return invalidRecordId;
  }
}
