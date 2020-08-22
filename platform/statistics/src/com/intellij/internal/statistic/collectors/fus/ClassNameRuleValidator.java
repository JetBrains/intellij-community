// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus;

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.internal.statistic.utils.PluginType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClassNameRuleValidator extends CustomValidationRule {
  @Override
  public boolean acceptRuleId(@Nullable String ruleId) {
    return "dialog_class".equals(ruleId) || "quick_fix_class_name".equals(ruleId) || "class_name".equals(ruleId);
  }

  @NotNull
  @Override
  protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
    if (isThirdPartyValue(data)) return ValidationResultType.ACCEPTED;

    final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(data);
    context.setPluginInfo(info);

    if (info.getType() == PluginType.UNKNOWN) {
      // if we can't detect a plugin then probably it's not a class name
      return ValidationResultType.REJECTED;
    }
    return info.isSafeToReport() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
  }
}
