// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator;

import org.jetbrains.annotations.NonNls;

public enum ValidationResultType {
  ACCEPTED("accepted", true),
  THIRD_PARTY("third.party", false),
  REJECTED("validation.unmatched_rule", false),
  INCORRECT_RULE("validation.incorrect_rule", false),
  UNDEFINED_RULE("validation.undefined_rule", false),
  UNREACHABLE_METADATA("validation.unreachable_metadata", true),
  PERFORMANCE_ISSUE("validation.performance_issue", true);

  private final String value;

  /**
   * Indicates if we should return the result or continue iterating through rules list,
   *
   * e.g. we want to check other rules if resultType==UNDEFINED_RULE
   * but we want to stop iterating if resultType==ACCEPTED
   */
  private final boolean isFinal;

  ValidationResultType(@NonNls String value, boolean isFinal) {
    this.value = value;
    this.isFinal = isFinal;
  }

  public String getDescription() {
    return value;
  }

  public boolean isFinal() {
    return isFinal;
  }
}
