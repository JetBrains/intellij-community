// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules;

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import org.jetbrains.annotations.NotNull;

public abstract class PerformanceCareRule implements FUSRule {
     private static final int EXPECTED_TIME_MSEC = 239; // TODO:  research this constant
     private static final int MAX_ATTEMPTS = 10;

     private int failed = 0;

  @NotNull
  @Override
  public final ValidationResultType validate(@NotNull String data, @NotNull EventContext context) {
    if (failed > MAX_ATTEMPTS) return ValidationResultType.PERFORMANCE_ISSUE;
    long startedAt = System.currentTimeMillis();

    ValidationResultType resultType = doValidate(data, context);

    if (System.currentTimeMillis() - startedAt > EXPECTED_TIME_MSEC) failed++;

    return resultType;
  }

  @NotNull
  protected abstract ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context);
}
