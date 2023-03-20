// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.vcs.log.impl.VcsLogErrorHandler;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class LoggingErrorHandler implements VcsLogErrorHandler {
  private final @NotNull Logger myLogger;

  public LoggingErrorHandler(@NotNull Logger logger) {
    myLogger = logger;
  }

  @Override
  public void handleError(@NotNull VcsLogErrorHandler.Source source, @NotNull Throwable throwable) {
    myLogger.error(throwable);
  }

  @Override
  public void displayMessage(@Nls @NotNull String message) {
    myLogger.error(message);
  }
}
