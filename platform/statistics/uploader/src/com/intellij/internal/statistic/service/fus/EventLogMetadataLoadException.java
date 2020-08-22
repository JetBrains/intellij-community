// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus;

public class EventLogMetadataLoadException extends Exception implements EventLogMetadataUpdateError {
  private int myErrorCode = -1;

  public EventLogMetadataLoadException(EventLogMetadataLoadErrorType type) {
    super(type.name());
  }

  public EventLogMetadataLoadException(EventLogMetadataLoadErrorType type, Throwable throwable) {
    super(type.name(), throwable);
  }

  public EventLogMetadataLoadException(EventLogMetadataLoadErrorType type, int errorCode) {
    super(type.name());
    myErrorCode = errorCode;
  }

  @Override
  public String getErrorType() {
    return getMessage();
  }

  @Override
  public int getErrorCode() {
    return myErrorCode;
  }

  @Override
  public EventLogMetadataUpdateStage getUpdateStage() {
    return EventLogMetadataUpdateStage.LOADING;
  }

  public enum EventLogMetadataLoadErrorType {
    EMPTY_SERVICE_URL, UNREACHABLE_SERVICE, EMPTY_RESPONSE_BODY, ERROR_ON_LOAD
  }
}
