// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status.widget;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
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
      if (PluginInfoDetectorKt.getPluginInfoByDescriptor(plugin).isSafeToReport()) {
        boolean enabled = settings.isEnabled(factory);
        if (enabled != factory.isEnabledByDefault()) {
          result.add(newBooleanMetric(factory.getId(), enabled));
        }
      }
    });
    return result;
  }
}
