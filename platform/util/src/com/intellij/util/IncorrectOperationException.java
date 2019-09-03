// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  /** @deprecated use {@link #IncorrectOperationException(String, Throwable)} (to be removed in IDEA 2020.1) */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  public IncorrectOperationException(String message, Exception e) {
    super(message, e);
  }
}