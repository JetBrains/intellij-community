// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus;

import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.lang.Language;
import com.jetbrains.fus.reporting.api.IEventContext;
import com.jetbrains.fus.reporting.api.ValidationResultType;
import org.jetbrains.annotations.NotNull;

public class LangCustomRuleValidator extends CustomValidationRule {
  @Override
  public @NotNull String getRuleId() {
    return "lang";
  }

  @Override
  protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull IEventContext context) {
    if (isThirdPartyValue(data)) return ValidationResultType.ACCEPTED;

    var language = Language.findLanguageByID(data);
    return (
      language == null ? ValidationResultType.REJECTED :
      PluginInfoDetectorKt.getPluginInfo(language.getClass()).isSafeToReport() ? ValidationResultType.ACCEPTED :
      ValidationResultType.THIRD_PARTY
    );
  }
}
