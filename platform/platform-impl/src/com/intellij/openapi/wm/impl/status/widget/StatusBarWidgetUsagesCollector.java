// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status.widget;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId3;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class StatusBarWidgetUsagesCollector extends ApplicationUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("status.bar.widgets", 2);
  private static final EventId3<PluginInfo, String, Boolean> WIDGET =
    GROUP.registerEvent("widget",
                        EventFields.PluginInfo,
                        EventFields.StringValidatedByCustomRule("id", StatusBarWidgetFactoryValidationRule.class),
                        EventFields.Boolean("enabled"));

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @Override
  public @NotNull Set<MetricEvent> getMetrics() {
    Set<MetricEvent> result = new HashSet<>();
    StatusBarWidgetSettings settings = StatusBarWidgetSettings.getInstance();
    StatusBarWidgetFactory.EP_NAME.processWithPluginDescriptor((factory, plugin) -> {
      PluginInfo pluginInfo = PluginInfoDetectorKt.getPluginInfoByDescriptor(plugin);
      boolean enabled = settings.isEnabled(factory);
      if (enabled != factory.isEnabledByDefault()) {
        result.add(WIDGET.metric(pluginInfo, factory.getId(), enabled));
      }
    });
    return result;
  }

  public static class StatusBarWidgetFactoryValidationRule extends CustomValidationRule {
    public static final String RULE_ID = "status_bar_widget_factory";

    @NotNull
    @Override
    public String getRuleId() {
      return RULE_ID;
    }

    @NotNull
    @Override
    protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      for (StatusBarWidgetFactory type : StatusBarWidgetFactory.EP_NAME.getExtensions()) {
        if (StringUtil.equals(type.getId(), data)) {
          final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(type.getClass());
          return info.isDevelopedByJetBrains() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
        }
      }
      return ValidationResultType.REJECTED;
    }
  }
}
