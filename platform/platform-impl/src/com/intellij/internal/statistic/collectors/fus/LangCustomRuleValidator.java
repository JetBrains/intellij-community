// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus;

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomUtilsWhiteListRule;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LangCustomRuleValidator extends CustomUtilsWhiteListRule {
  @Override
  public boolean acceptRuleId(@Nullable String ruleId) {
    return "lang".equals(ruleId);
  }

  @NotNull
  @Override
  protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
    if (data.equals("third.party")) return ValidationResultType.ACCEPTED;

    final Language language = Language.findLanguageByID(data);
    return language != null && PluginInfoDetectorKt.getPluginInfo(language.getClass()).isSafeToReport() ?
           ValidationResultType.ACCEPTED : ValidationResultType.REJECTED;
  }
}
