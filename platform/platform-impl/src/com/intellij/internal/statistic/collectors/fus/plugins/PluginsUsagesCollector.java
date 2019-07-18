// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.plugins;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.beans.MetricEventFactoryKt;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

final class PluginsUsagesCollector extends ApplicationUsagesCollector {
  @Override
  @NotNull
  public String getGroupId() {
    return "plugins";
  }

  @Override
  @NotNull
  public Set<MetricEvent> getMetrics() {
    final Set<MetricEvent> result = new THashSet<>();
    for (String id : PluginManagerCore.disabledPlugins()) {
      final PluginInfo info = PluginInfoDetectorKt.getPluginInfoById(PluginId.findId(id));
      final FeatureUsageData data = new FeatureUsageData().addPluginInfo(info);
      result.add(MetricEventFactoryKt.newMetric("disabled.plugin", data));
    }

    final IdeaPluginDescriptor[] plugins = PluginManagerCore.getPlugins();
    final List<IdeaPluginDescriptor> nonBundledEnabledPlugins = ContainerUtil.filter(plugins, d -> d.isEnabled() && !d.isBundled());
    for (IdeaPluginDescriptor descriptor : nonBundledEnabledPlugins) {
      final PluginInfo info = PluginInfoDetectorKt.getPluginInfoByDescriptor(descriptor);
      final FeatureUsageData data = new FeatureUsageData().addPluginInfo(info);
      result.add(MetricEventFactoryKt.newMetric("enabled.not.bundled.plugin", data));
    }
    return result;
  }
}