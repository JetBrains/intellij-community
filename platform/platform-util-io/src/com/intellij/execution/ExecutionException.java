// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;

public class ExecutionException extends Exception {
  public ExecutionException(final @NlsContexts.DialogMessage String s) {
    super(s);
  }

  public ExecutionException(final Throwable cause) {
    super(cause == null ? null : cause.getMessage(), cause);
  }

  public ExecutionException(final @NlsContexts.DialogMessage String s, Throwable cause) {
    super(s, cause);
  }

  /**
   * @deprecated Quite a trivial method for the API. Just inline it.
   */
  @ApiStatus.Internal
  @Deprecated
  public IOException toIOException() {
    final Throwable cause = getCause();
    return cause instanceof IOException ? (IOException)cause : new IOException(this);
  }
}