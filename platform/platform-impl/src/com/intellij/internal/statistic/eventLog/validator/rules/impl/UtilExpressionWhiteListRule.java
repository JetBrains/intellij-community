// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.impl;

import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.FUSRule;
import com.intellij.internal.statistic.eventLog.validator.rules.PerformanceCareRule;
import org.jetbrains.annotations.NotNull;

public class UtilExpressionWhiteListRule extends PerformanceCareRule implements FUSRule {
  @NotNull private final FUSRule myRule;
  @NotNull private final String myPrefix;
  @NotNull private final String mySuffix;

  public UtilExpressionWhiteListRule(@NotNull FUSRule rule, @NotNull String prefix, @NotNull String suffix) {
    myRule = rule;
    myPrefix = prefix;
    mySuffix = suffix;
  }

  @NotNull
  @Override
  public ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
    if (acceptPrefix(data) && acceptSuffix(data)) {
      return myRule.validate(data.substring(myPrefix.length(), data.length() - mySuffix.length()), context);
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
    return "UtilExpressionWhiteListRule: myPrefix=" + myPrefix +",mySuffix=" + mySuffix+",myRule=" + myRule;
  }
}
