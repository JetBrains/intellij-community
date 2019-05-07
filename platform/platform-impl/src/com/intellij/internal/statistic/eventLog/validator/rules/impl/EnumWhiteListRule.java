// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.impl;

import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.FUSRegexpAwareRule;
import com.intellij.internal.statistic.eventLog.validator.rules.PerformanceCareRule;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import static com.intellij.internal.statistic.eventLog.validator.ValidationResultType.*;

public class EnumWhiteListRule extends PerformanceCareRule implements FUSRegexpAwareRule {
  private Collection<String> myEnumValues;

  public EnumWhiteListRule(@Nullable Collection<String> strings) {
    myEnumValues = strings == null ? Collections.emptySet() : ContainerUtil.unmodifiableOrEmptyCollection(strings);
  }

  @Override
  public ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
    if (myEnumValues.isEmpty()) return INCORRECT_RULE;
    return myEnumValues.contains(data) ? ACCEPTED : REJECTED;
  }

  @NotNull
  @Override
  public String asRegexp() {
    return  StringUtil.join(myEnumValues.stream().map(s ->RegexpWhiteListRule.escapeText(s)).collect(Collectors.toList()), "|");
  }

  @Override
  public String toString() {
    return "EnumWhiteListRule: myEnumValues=" + asRegexp();
  }
}
