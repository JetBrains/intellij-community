// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.ApiStatus;

/**
 * Signals the requested operation has failed.
 */
public class IncorrectOperationException extends RuntimeException {
  public IncorrectOperationException() {
    super();
  }

  public IncorrectOperationException(String message) {
    super(message);
  }

  public IncorrectOperationException(Throwable t) {
    super(t);
  }

  public IncorrectOperationException(String message, Throwable t) {
    super(message, t);
  }

  /** @deprecated use {@link #IncorrectOperationException(String, Throwable)} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public IncorrectOperationException(String message, Exception e) {
    super(message, e);
  }
}