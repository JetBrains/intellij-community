// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.plugins;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public class NonBundledPluginsUsagesCollector extends ApplicationUsagesCollector {
  @NotNull
  public String getGroupId() {
    return "statistics.plugins.non.bundled";
  }

  @NotNull
  public Set<UsageDescriptor> getUsages() {
    final IdeaPluginDescriptor[] plugins = PluginManagerCore.getPlugins();
    final List<IdeaPluginDescriptor> nonBundledEnabledPlugins = ContainerUtil.filter(plugins, d -> d.isEnabled() && !d.isBundled() && d.getPluginId() != null);

    return ContainerUtil.map2Set(nonBundledEnabledPlugins, descriptor -> new UsageDescriptor(descriptor.getPluginId().getIdString(), 1));
  }

}
