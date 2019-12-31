/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.execution.test.runner.events;

import com.intellij.openapi.externalSystem.model.task.event.FailureResult;
import com.intellij.openapi.externalSystem.model.task.event.OperationResult;
import com.intellij.openapi.externalSystem.model.task.event.SkippedResult;
import com.intellij.openapi.externalSystem.model.task.event.SuccessResult;

/**
 * @author Vladislav.Soroka
 */
public enum TestEventResult {
  SUCCESS("SUCCESS"),
  FAILURE("FAILURE"),
  SKIPPED("SKIPPED"),
  UNKNOWN_RESULT("unknown");
  private final String value;

  TestEventResult(String v) {
    value = v;
  }

  public String value() {
    return value;
  }

  public static TestEventResult fromValue(String v) {
    for (TestEventResult c : TestEventResult.values()) {
      if (c.value.equals(v)) {
        return c;
      }
    }
    return UNKNOWN_RESULT;
  }

  static TestEventResult fromOperationResult(OperationResult result) {
    if (result instanceof SuccessResult) {
      return SUCCESS;
    } else if (result instanceof FailureResult) {
      return FAILURE;
    } else if (result instanceof SkippedResult) {
      return SKIPPED;
    } else {
      return UNKNOWN_RESULT;
    }
  }
}
