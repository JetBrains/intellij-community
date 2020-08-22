// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus;

public class EventLogMetadataParseException extends Exception implements EventLogMetadataUpdateError {
  public EventLogMetadataParseException(EventLogMetadataParseErrorType type) {
    super(type.name());
  }

  public EventLogMetadataParseException(EventLogMetadataParseErrorType type, Throwable throwable) {
    super(type.name(), throwable);
  }

  @Override
  public String getErrorType() {
    return getMessage();
  }

  @Override
  public int getErrorCode() {
    return -1;
  }

  @Override
  public EventLogMetadataUpdateStage getUpdateStage() {
    return EventLogMetadataUpdateStage.PARSING;
  }

  public enum EventLogMetadataParseErrorType {
    EMPTY_CONTENT, INVALID_JSON, UNKNOWN
  }
}
