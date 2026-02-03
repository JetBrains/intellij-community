// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Analog of {@link com.intellij.openapi.util.io.FileTooBigException}, but unchecked.
 */
@ApiStatus.Internal
public class ContentTooBigException extends RuntimeException {
  public ContentTooBigException(@NotNull String message) {
    super(message);
  }
}
