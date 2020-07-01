// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.impl;

import com.intellij.internal.statistic.eventLog.validator.SensitiveDataValidator;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
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

/**
 * <p>
 *   Base class for custom validation rules.
 *   If your data cannot be validated with enumerated values or by a regexp,
 *   inherit the class and implement {@link CustomValidationRule#doValidate(String, EventContext)}.
 *   For more information see {@link SensitiveDataValidator}.
 * </p>
 *
 * <p><i>Example:</i>
 * {@link com.intellij.internal.statistic.collectors.fus.ClassNameRuleValidator},
 * {@link com.intellij.internal.statistic.collectors.fus.LangCustomRuleValidator}, etc.</p>
 *
 * @see SensitiveDataValidator
 */
public abstract class CustomValidationRule extends PerformanceCareRule implements FUSRule {
  public static final ExtensionPointName<CustomValidationRule> EP_NAME =
    ExtensionPointName.create("com.intellij.statistics.validation.customValidationRule");

  public abstract boolean acceptRuleId(@Nullable String ruleId);

  @NotNull
  protected static ValidationResultType acceptWhenReportedByPluginFromPluginRepository(@NotNull EventContext context) {
    final Object pluginType = context.eventData.get("plugin_type");
    final PluginType type = pluginType != null ? PluginInfoDetectorKt.findPluginTypeByValue(pluginType.toString()) : null;
    if (type == null || !type.isSafeToReport()) {
      return ValidationResultType.REJECTED;
    }

    if (type == PluginType.PLATFORM || type == PluginType.FROM_SOURCES || hasPluginField(context)) {
      return ValidationResultType.ACCEPTED;
    }
    return ValidationResultType.REJECTED;
  }

  @NotNull
  protected static ValidationResultType acceptWhenReportedByJetBrainsPlugin(@NotNull EventContext context) {
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
    PluginId pluginId = PluginId.findId(plugin);
    return pluginId != null && PluginInfoDetectorKt.getPluginInfoById(pluginId).isSafeToReport();
  }

  @Nullable
  protected Language getLanguage(@NotNull EventContext context) {
    final Object id = context.eventData.get("lang");
    return id instanceof String ? Language.findLanguageByID((String)id) : null;
  }

  @Nullable
  protected String getEventDataField(@NotNull EventContext context, @NotNull String name) {
    return context.eventData.containsKey(name) ? context.eventData.get(name).toString() : null;
  }
}
