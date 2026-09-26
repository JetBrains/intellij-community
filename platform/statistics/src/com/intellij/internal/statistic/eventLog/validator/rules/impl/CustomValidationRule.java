// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.rules.impl;

import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.PerformanceCareRule;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.internal.statistic.utils.PluginType;
import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.text.Strings;
import com.jetbrains.fus.reporting.api.FUSRule;
import com.jetbrains.fus.reporting.api.IEventContext;
import com.jetbrains.fus.reporting.api.PayloadKey;
import com.jetbrains.fus.reporting.api.ValidationResultType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Base class for custom validation rules.
/// If your data cannot be validated with enumerated values or by a regexp,
/// inherit the class and implement [CustomValidationRule#doValidate(String, IEventContext)].
/// For more information see [IntellijSensitiveDataValidator].
///
/// _Example:_
/// [com.intellij.internal.statistic.collectors.fus.ClassNameRuleValidator],
/// [com.intellij.internal.statistic.collectors.fus.LangCustomRuleValidator], etc.
///
/// @see IntellijSensitiveDataValidator
public abstract class CustomValidationRule extends PerformanceCareRule implements FUSRule, UtilValidationRule {
  public static final ExtensionPointName<CustomValidationRule> EP_NAME =
    ExtensionPointName.create("com.intellij.statistics.validation.customValidationRule");

  public static final PayloadKey<PluginInfo> PLUGIN_INFO = new PayloadKey<>("plugin_info");

  public boolean acceptRuleId(@Nullable String ruleId) {
    return getRuleId().equals(ruleId);
  }

  public @NotNull String getRuleId() {
    throw new UnsupportedOperationException(String.format("The method getRuleId must be overridden in %s", getClass()));
  }

  public static String getRuleId(Class<?> clazz) {
    var optionalCustomValidationRule = EP_NAME.getExtensionList().stream()
      .filter(customValidationRule -> customValidationRule.getClass() == clazz)
      .map(rule -> rule.getRuleId())
      .findFirst();

    if (optionalCustomValidationRule.isEmpty()) {
      optionalCustomValidationRule = CustomValidationRuleFactory.EP_NAME.getExtensionList().stream()
        .filter(factory -> factory.getRuleClass() == clazz)
        .map(factory -> factory.getRuleId())
        .findFirst();
    }

    if (optionalCustomValidationRule.isEmpty()) {
      throw new IllegalStateException(String.format("CustomValidationRule instance is not found for class %s.", clazz.getName()));
    }

    return optionalCustomValidationRule.get();
  }

  protected static @NotNull ValidationResultType acceptWhenReportedByPluginFromPluginRepository(@NotNull IEventContext context) {
    var pluginType = context.getEventData().get("plugin_type");
    var type = pluginType != null ? PluginInfoDetectorKt.findPluginTypeByValue(pluginType.toString()) : null;
    if (type == null || !type.isSafeToReport()) {
      return ValidationResultType.REJECTED;
    }

    if (type.isPlatformOrJvm() || type == PluginType.FROM_SOURCES || hasPluginField(context)) {
      return ValidationResultType.ACCEPTED;
    }
    return ValidationResultType.REJECTED;
  }

  protected static @NotNull ValidationResultType acceptWhenReportedByJetBrainsPlugin(@NotNull IEventContext context) {
    return isReportedByJetBrainsPlugin(context) ? ValidationResultType.ACCEPTED : ValidationResultType.REJECTED;
  }

  protected static boolean isReportedByJetBrainsPlugin(@NotNull IEventContext context) {
    var pluginType = context.getEventData().get("plugin_type");
    var type = pluginType != null ? PluginInfoDetectorKt.findPluginTypeByValue(pluginType.toString()) : null;
    if (type == null || !type.isDevelopedByJetBrains()) {
      return false;
    }

    if (type.isPlatformOrJvm() || type == PluginType.FROM_SOURCES || hasPluginField(context)) {
      return true;
    }
    return false;
  }

  protected static boolean hasPluginField(@NotNull IEventContext context) {
    return context.getEventData().get("plugin") instanceof String str && Strings.isNotEmpty(str);
  }

  protected static boolean isThirdPartyValue(@NotNull String data) {
    return ValidationResultType.THIRD_PARTY.getDescription().equals(data);
  }

  protected static boolean isPluginFromPluginRepository(@NotNull String plugin) {
    var pluginId = PluginId.getId(plugin);
    return PluginInfoDetectorKt.getPluginInfoById(pluginId).isSafeToReport();
  }

  protected @Nullable Language getLanguage(@NotNull IEventContext context) {
    return context.getEventData().get("lang") instanceof String str ? Language.findLanguageByID(str) : null;
  }

  protected @Nullable String getEventDataField(@NotNull IEventContext context, @NotNull String name) {
    return context.getEventData().containsKey(name) ? context.getEventData().get(name).toString() : null;
  }

  /// @deprecated This method was added for compatibility with existing custom rules.
  /// Use [#doValidate(String, IEventContext)] instead.
  @Deprecated(forRemoval = true)
  protected @NotNull com.intellij.internal.statistic.eventLog.validator.ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
    return com.intellij.internal.statistic.eventLog.validator.ValidationResultType.REJECTED;
  }

  @Override
  protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull IEventContext context) {
    if (context instanceof EventContext eventContext) {
      return com.intellij.internal.statistic.eventLog.validator.ValidationResultType.toFusApiResultType(this.doValidate(data, eventContext));
    } else {
      return ValidationResultType.REJECTED;
    }
  }
}
