// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.NonExtendable
public final class InvalidDataException extends RuntimeException {
  public InvalidDataException() {
    super();
  }

  public InvalidDataException(@NotNull String s) {
    super(s);
  }

  public InvalidDataException(@NotNull String message, @NotNull Throwable cause) {
    super(message, cause);
  }

  public InvalidDataException(@NotNull Throwable cause) {
    super(cause);
  }
}
