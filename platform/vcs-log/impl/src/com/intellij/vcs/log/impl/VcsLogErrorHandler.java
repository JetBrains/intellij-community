// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public interface VcsLogErrorHandler {
  void handleError(@NotNull Source source, @NotNull Throwable throwable);

  void displayMessage(@Nls @NotNull String message);

  enum Source {
    Storage, Index
  }
}
