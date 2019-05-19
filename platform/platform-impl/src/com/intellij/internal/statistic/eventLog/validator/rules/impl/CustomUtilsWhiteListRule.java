// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.impl;

import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.FUSRule;
import com.intellij.internal.statistic.eventLog.validator.rules.PerformanceCareRule;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CustomUtilsWhiteListRule extends PerformanceCareRule implements FUSRule {
  public static final ExtensionPointName<CustomUtilsWhiteListRule> EP_NAME =
    ExtensionPointName.create("com.intellij.statistics.validation.customUtilsWhiteListRule");

  public abstract boolean acceptRuleId(@Nullable String ruleId);

  @NotNull
  protected static ValidationResultType acceptWhenReportedByJetbrainsPlugin(@NotNull EventContext context) {
    if ("PLATFORM".equals(context.eventData.get("plugin_type"))) return ValidationResultType.ACCEPTED;
    Object plugin = context.eventData.get("plugin");
    if (plugin != null) {
      PluginId pluginId = PluginId.findId(plugin.toString());
      if (pluginId != null && PluginInfoDetectorKt.getPluginInfoById(pluginId).isDevelopedByJetBrains()) {
        return ValidationResultType.ACCEPTED;
      }
    }
    return ValidationResultType.REJECTED;
  }
}
