// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus;

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

public class LangCustomRuleValidator extends CustomValidationRule {
  @Override
  public @NotNull String getRuleId() {
    return "lang";
  }

  @Override
  protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
    if (isThirdPartyValue(data)) return ValidationResultType.ACCEPTED;

    final Language language = Language.findLanguageByID(data);
    if (language == null) {
      return ValidationResultType.REJECTED;
    }
    return PluginInfoDetectorKt.getPluginInfo(language.getClass()).isSafeToReport() ?
           ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
  }
}
