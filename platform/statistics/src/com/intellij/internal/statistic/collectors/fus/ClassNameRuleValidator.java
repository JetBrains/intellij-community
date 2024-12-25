// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus;

import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.internal.statistic.utils.PluginType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Please do not use directly.
 * <br/>
 * Use {@link EventFields#Class(String)} instead.
 */
public class ClassNameRuleValidator extends CustomValidationRule {
  @Override
  public @NotNull String getRuleId() {
    return "class_name";
  }

  @Override
  public boolean acceptRuleId(@Nullable String ruleId) {
    return "dialog_class".equals(ruleId) || "quick_fix_class_name".equals(ruleId) || getRuleId().equals(ruleId);
  }

  @Override
  protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
    if (isThirdPartyValue(data)) return ValidationResultType.ACCEPTED;

    final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(getClassName(data));
    context.setPayload(PLUGIN_INFO, info);

    if (info.getType() == PluginType.UNKNOWN) {
      // if we can't detect a plugin then probably it's not a class name
      return ValidationResultType.REJECTED;
    }
    return info.isSafeToReport() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
  }

  private static @NotNull String getClassName(@NotNull String data) {
    int i = data.indexOf("$$Lambda");
    if (i == -1) {
      return data;
    }
    return data.substring(0, i);
  }
}
