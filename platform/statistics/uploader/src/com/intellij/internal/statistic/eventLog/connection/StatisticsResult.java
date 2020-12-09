// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.connection;

public class StatisticsResult {
  private final ResultCode code;
  private final String description;

  public StatisticsResult(ResultCode code, String description) {
    this.code = code;
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public ResultCode getCode() {
    return code;
  }

  public enum ResultCode {SEND, NOT_PERMITTED_SERVER, NOT_PERMITTED_USER, ERROR_IN_CONFIG, NOT_PERMITTED_TIMEOUT, NOTHING_TO_SEND, SENT_WITH_ERRORS}
}
