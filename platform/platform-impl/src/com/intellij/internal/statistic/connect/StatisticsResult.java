/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.internal.statistic.connect;

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
