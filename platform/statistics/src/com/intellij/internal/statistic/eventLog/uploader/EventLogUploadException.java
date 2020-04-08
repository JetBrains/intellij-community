// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.uploader;

import org.jetbrains.annotations.NotNull;

public class EventLogUploadException extends Exception {
  private final EventLogUploadErrorType myErrorType;

  public EventLogUploadException(@NotNull String message, @NotNull EventLogUploadErrorType errorType) {
    super(message);
    myErrorType = errorType;
  }

  @NotNull
  public EventLogUploadErrorType getErrorType() {
    return myErrorType;
  }

  public enum EventLogUploadErrorType {
    NO_LOGS, NO_UPLOADER, NO_LIBRARIES, NO_TEMP_FOLDER
  }
}
