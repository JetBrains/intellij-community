// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.impl;

import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.FUSRule;
import com.intellij.internal.statistic.eventLog.validator.rules.PayloadKey;
import com.intellij.internal.statistic.eventLog.validator.rules.PerformanceCareRule;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.internal.statistic.utils.PluginType;
import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * <p>
 *   Base class for custom validation rules.
 *   If your data cannot be validated with enumerated values or by a regexp,
 *   inherit the class and implement {@link CustomValidationRule#doValidate(String, EventContext)}.
 *   For more information see {@link IntellijSensitiveDataValidator}.
 * </p>
 *
 * <p><i>Example:</i>
 * {@link com.intellij.internal.statistic.collectors.fus.ClassNameRuleValidator},
 * {@link com.intellij.internal.statistic.collectors.fus.LangCustomRuleValidator}, etc.</p>
 *
 * @see IntellijSensitiveDataValidator
 */
public abstract class CustomValidationRule extends PerformanceCareRule implements FUSRule, UtilValidationRule {
  public static final ExtensionPointName<CustomValidationRule> EP_NAME =
    ExtensionPointName.create("com.intellij.statistics.validation.customValidationRule");

  public static final PayloadKey<PluginInfo> PLUGIN_INFO = new PayloadKey<>("plugin_info");

  public boolean acceptRuleId(@Nullable @NonNls String ruleId) {
    return getRuleId().equals(ruleId);
  }

  @NotNull
  public String getRuleId() {
    throw new UnsupportedOperationException(String.format("The method getRuleId must be overridden in %s", getClass()));
  }

  public static <T extends CustomValidationRule> T getCustomValidationRuleInstance(Class<T> clazz) {
    Optional<CustomValidationRule> optionalCustomValidationRule = EP_NAME.getExtensionList()
      .stream()
      .filter(customValidationRule -> customValidationRule.getClass() == clazz)
      .findFirst();
    if (optionalCustomValidationRule.isEmpty())
      throw new IllegalStateException(String.format("CustomValidationRule instance is not found for class %s.", clazz.getName()));
    //noinspection unchecked
    return  (T) optionalCustomValidationRule.get();
  }

  @NotNull
  protected static ValidationResultType acceptWhenReportedByPluginFromPluginRepository(@NotNull EventContext context) {
    final Object pluginType = context.eventData.get("plugin_type");
    final PluginType type = pluginType != null ? PluginInfoDetectorKt.findPluginTypeByValue(pluginType.toString()) : null;
    if (type == null || !type.isSafeToReport()) {
      return ValidationResultType.REJECTED;
    }

    if (type.isPlatformOrJvm() || type == PluginType.FROM_SOURCES || hasPluginField(context)) {
      return ValidationResultType.ACCEPTED;
    }
    return ValidationResultType.REJECTED;
  }

  @NotNull
  protected static ValidationResultType acceptWhenReportedByJetBrainsPlugin(@NotNull EventContext context) {
    return isReportedByJetBrainsPlugin(context) ? ValidationResultType.ACCEPTED : ValidationResultType.REJECTED;
  }

  protected static boolean isReportedByJetBrainsPlugin(@NotNull EventContext context) {
    final Object pluginType = context.eventData.get("plugin_type");
    final PluginType type = pluginType != null ? PluginInfoDetectorKt.findPluginTypeByValue(pluginType.toString()) : null;
    if (type == null || !type.isDevelopedByJetBrains()) {
      return false;
    }

    if (type.isPlatformOrJvm() || type == PluginType.FROM_SOURCES || hasPluginField(context)) {
      return true;
    }
    return false;
  }

  protected static boolean hasPluginField(@NotNull EventContext context) {
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
