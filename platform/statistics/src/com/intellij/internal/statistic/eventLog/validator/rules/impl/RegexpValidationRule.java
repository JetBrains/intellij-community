// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.impl;

import com.intellij.internal.statistic.eventLog.StatisticsEventEscaper;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.FUSRegexpAwareRule;
import com.intellij.internal.statistic.eventLog.validator.rules.PerformanceCareRule;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static com.intellij.internal.statistic.eventLog.validator.ValidationResultType.*;

public class RegexpValidationRule extends PerformanceCareRule implements FUSRegexpAwareRule {
  private final NullableLazyValue<Pattern> myPattern;
  @Nullable private final String myRegexp;

  private static final List<String> ESCAPE_FROM = Arrays.asList("\\", "[", "]", "{", "}", "(", ")", "-", "^", "*", "+", "?", ".", "|", "$");
  private static final List<String> ESCAPE_TO = ContainerUtil.map(ESCAPE_FROM, s -> "\\" + s);

  public RegexpValidationRule(@Nullable final String regexp) {
    myPattern = regexp == null ? null : new NullableLazyValue<Pattern>() {
      @Nullable
      @Override
      protected Pattern compute() {
        try {
          return Pattern.compile(regexp);
        } catch (Exception ignored) {
          return null;
        }
      }
    };
    myRegexp = regexp;
  }

  @NotNull
  @Override
  public ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
    Pattern pattern = myPattern.getValue();
    if (pattern == null) return INCORRECT_RULE;
    return pattern.matcher(StatisticsEventEscaper.escape(data)).matches() ? ACCEPTED : REJECTED;
  }

  @NotNull
  @Override
  public String asRegexp() {
    return myRegexp != null ? myRegexp : "<invalid>";
  }

  @Override
  public String toString() {
    return "RegexpValidationRule: myRegexp=" + asRegexp();
  }

  public static String escapeText(@NotNull String text) {
    return StringUtil.replace(text, ESCAPE_FROM, ESCAPE_TO);
  }

}
