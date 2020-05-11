// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status.widget;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.internal.statistic.beans.MetricEventFactoryKt.newBooleanMetric;

public class StatusBarWidgetUsagesCollector extends ApplicationUsagesCollector {
  private static final String GROUP_ID = "status.bar.widgets";

  @Override
  public @NotNull String getGroupId() {
    return GROUP_ID;
  }

  @Override
  public @NotNull Set<MetricEvent> getMetrics() {
    Set<MetricEvent> result = new HashSet<>();
    StatusBarWidgetSettings settings = ServiceManager.getService(StatusBarWidgetSettings.class);
    StatusBarWidgetFactory.EP_NAME.processWithPluginDescriptor((factory, plugin) -> {
      PluginInfo pluginInfo = PluginInfoDetectorKt.getPluginInfoByDescriptor(plugin);
      if (pluginInfo.isSafeToReport()) {
        boolean enabled = settings.isEnabled(factory);
        if (enabled != factory.isEnabledByDefault()) {
          FeatureUsageData data = new FeatureUsageData().addData("id", factory.getId()).addPluginInfo(pluginInfo);
          result.add(newBooleanMetric("widget", enabled, data));
        }
      }
    });
    return result;
  }
}
