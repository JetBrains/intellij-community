// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator;

import org.jetbrains.annotations.NonNls;

public enum ValidationResultType {
  ACCEPTED("accepted"),
  REJECTED("validation.unmatched_rule"),
  INCORRECT_RULE("validation.incorrect_rule"),
  UNDEFINED_RULE("validation.undefined_rule"),
  UNREACHABLE_WHITELIST("validation.unreachable.whitelist"),
  PERFORMANCE_ISSUE("validation.performance_issue");

  private final String value;

  ValidationResultType(@NonNls String value) {
    this.value = value;
  }

  public String getDescription() {
    return value;
  }
}
