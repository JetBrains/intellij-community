// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.plugins;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class DisabledPluginsUsagesCollector extends ApplicationUsagesCollector {

  @NotNull
  public String getGroupId() {
    return "statistics.plugins.disabled";
  }

  @NotNull
  public Set<UsageDescriptor> getUsages() {
    return ContainerUtil.map2Set(PluginManagerCore.getDisabledPlugins(), descriptor -> new UsageDescriptor(descriptor, 1));
  }
}
