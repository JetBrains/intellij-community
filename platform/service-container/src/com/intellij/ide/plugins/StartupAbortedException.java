// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

@Internal
public final class StartupAbortedException extends RuntimeException {
  public StartupAbortedException(@NotNull String message, @NotNull Throwable cause) {
    super(message, cause);
  }
}
