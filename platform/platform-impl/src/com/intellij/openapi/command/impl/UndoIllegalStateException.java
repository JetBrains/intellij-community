// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import org.jetbrains.annotations.NotNull;


final class UndoIllegalStateException extends RuntimeException {

  UndoIllegalStateException(@NotNull String message) {
    this(message, null);
  }

  UndoIllegalStateException(String message, Throwable cause) {
    super(message, cause);
  }
}
