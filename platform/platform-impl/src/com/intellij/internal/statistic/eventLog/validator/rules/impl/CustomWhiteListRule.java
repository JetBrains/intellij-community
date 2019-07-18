// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.impl;

import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.FUSRule;
import com.intellij.internal.statistic.eventLog.validator.rules.PerformanceCareRule;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.internal.statistic.utils.PluginType;
import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CustomWhiteListRule extends PerformanceCareRule implements FUSRule {
  public static final ExtensionPointName<CustomWhiteListRule> EP_NAME =
    ExtensionPointName.create("com.intellij.statistics.validation.customWhiteListRule");

  public abstract boolean acceptRuleId(@Nullable String ruleId);

  @NotNull
  protected static ValidationResultType acceptWhenReportedByPluginFromPluginRepository(@NotNull EventContext context) {
    final Object pluginType = context.eventData.get("plugin_type");
    final PluginType type = pluginType != null ? PluginInfoDetectorKt.findPluginTypeByValue(pluginType.toString()) : null;
    if (type == null || !type.isSafeToReport()) {
      return ValidationResultType.REJECTED;
    }

    if (type == PluginType.PLATFORM || hasPluginField(context)) {
      return ValidationResultType.ACCEPTED;
    }
    return ValidationResultType.REJECTED;
  }

  @NotNull
  protected static ValidationResultType acceptWhenReportedByJetbrainsPlugin(@NotNull EventContext context) {
    final Object pluginType = context.eventData.get("plugin_type");
    final PluginType type = pluginType != null ? PluginInfoDetectorKt.findPluginTypeByValue(pluginType.toString()) : null;
    if (type == null || !type.isDevelopedByJetBrains()) {
      return ValidationResultType.REJECTED;
    }

    if (type == PluginType.PLATFORM || hasPluginField(context)) {
      return ValidationResultType.ACCEPTED;
    }
    return ValidationResultType.REJECTED;
  }

  private static boolean hasPluginField(@NotNull EventContext context) {
    if (context.eventData.containsKey("plugin")) {
      final Object plugin = context.eventData.get("plugin");
      return plugin instanceof String && StringUtil.isNotEmpty((String)plugin);
    }
    return false;
  }

  protected static boolean isThirdPartyValue(@NotNull String data) {
    return ValidationResultType.THIRD_PARTY.getDescription().equals(data);
  }

  protected static boolean isPluginFromPluginRepository(@NotNull String plugin) {
    final PluginId pluginId = PluginId.findId(plugin);
    return pluginId != null && PluginInfoDetectorKt.getPluginInfoById(pluginId).isSafeToReport();
  }

  @Nullable
  protected Language getLanguage(@NotNull EventContext context) {
    final Object id = context.eventData.get("lang");
    return id instanceof String ? Language.findLanguageByID((String)id) : null;
  }
}
