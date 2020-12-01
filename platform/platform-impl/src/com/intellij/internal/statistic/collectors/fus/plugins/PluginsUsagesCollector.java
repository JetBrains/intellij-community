// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.plugins;

import com.intellij.ide.plugins.DisabledPluginsState;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.beans.MetricEventFactoryKt;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

final class PluginsUsagesCollector extends ApplicationUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("plugins", 3);
  public static final EventId1<PluginInfo> DISABLED_PLUGIN = GROUP.registerEvent("disabled.plugin", EventFields.PluginInfo);
  public static final EventId1<PluginInfo> ENABLED_NOT_BUNDLED_PLUGIN =
    GROUP.registerEvent("enabled.not.bundled.plugin", EventFields.PluginInfo);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @Override
  @NotNull
  public Set<MetricEvent> getMetrics() {
    Set<MetricEvent> result = new HashSet<>();
    for (PluginId id : DisabledPluginsState.disabledPlugins()) {
      PluginInfo info = PluginInfoDetectorKt.getPluginInfoById(id);
      result.add(DISABLED_PLUGIN.metric(info));
    }

    for (IdeaPluginDescriptor descriptor : PluginManagerCore.getLoadedPlugins()) {
      if (descriptor.isEnabled() && !descriptor.isBundled()) {
        PluginInfo info = PluginInfoDetectorKt.getPluginInfoByDescriptor(descriptor);
        result.add(ENABLED_NOT_BUNDLED_PLUGIN.metric(info));
      }
    }
    return result;
  }
}