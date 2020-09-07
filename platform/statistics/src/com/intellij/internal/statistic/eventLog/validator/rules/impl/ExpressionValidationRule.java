// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.impl;

import com.intellij.internal.statistic.eventLog.StatisticsEventEscaper;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.FUSRule;
import com.intellij.internal.statistic.eventLog.validator.rules.PerformanceCareRule;
import org.jetbrains.annotations.NotNull;

public class ExpressionValidationRule extends PerformanceCareRule implements FUSRule {
  @NotNull private final FUSRule myRule;
  @NotNull private final String myPrefix;
  @NotNull private final String mySuffix;

  public ExpressionValidationRule(@NotNull FUSRule rule, @NotNull String prefix, @NotNull String suffix) {
    myRule = rule;
    myPrefix = prefix;
    mySuffix = suffix;
  }

  @NotNull
  @Override
  public ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
    String escaped = StatisticsEventEscaper.escapeEventIdOrFieldValue(data);
    ValidationResultType result = validateEscaped(escaped, context);
    if (result != ValidationResultType.ACCEPTED) {
      // for backward compatibility with rules created before allowed symbols were changed
      String legacyData = StatisticsEventEscaper.cleanupForLegacyRulesIfNeeded(escaped);
      if (legacyData != null) {
        return validateEscaped(legacyData, context);
      }
    }
    return result;
  }

  @NotNull
  private ValidationResultType validateEscaped(@NotNull String escapedData, @NotNull EventContext context) {
    if (acceptPrefix(escapedData) && acceptSuffix(escapedData)) {
      return myRule.validate(escapedData.substring(myPrefix.length(), escapedData.length() - mySuffix.length()), context);
    }
    return ValidationResultType.REJECTED;
  }

  private boolean acceptPrefix(@NotNull String data) {
    return myPrefix.isEmpty() || data.startsWith(myPrefix);
  }

  private boolean acceptSuffix(@NotNull String data) {
    return mySuffix.isEmpty() || data.endsWith(mySuffix);
  }

  @Override
  public String toString() {
    return "UtilExpressionValidationRule: myPrefix=" + myPrefix +",mySuffix=" + mySuffix+",myRule=" + myRule;
  }
}
