// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus;

public class EventLogWhitelistLoadException extends Exception implements EventLogWhitelistUpdateError {
  private int myErrorCode = -1;

  public EventLogWhitelistLoadException(EventLogWhitelistLoadErrorType type) {
    super(type.name());
  }

  public EventLogWhitelistLoadException(EventLogWhitelistLoadErrorType type, Throwable throwable) {
    super(type.name(), throwable);
  }

  public EventLogWhitelistLoadException(EventLogWhitelistLoadErrorType type, int errorCode) {
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
  public EventLogWhitelistUpdateStage getUpdateStage() {
    return EventLogWhitelistUpdateStage.LOADING;
  }

  public enum EventLogWhitelistLoadErrorType {
    EMPTY_SERVICE_URL, UNREACHABLE_SERVICE, EMPTY_RESPONSE_BODY, ERROR_ON_LOAD
  }
}
