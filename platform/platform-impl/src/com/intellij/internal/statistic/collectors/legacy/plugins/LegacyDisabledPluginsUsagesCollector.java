// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.legacy.plugins;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@Deprecated // to be removed in 2018.2
public class LegacyDisabledPluginsUsagesCollector extends UsagesCollector {
  private static final String GROUP_ID = "disabled-plugins";

  @NotNull
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create(GROUP_ID, GroupDescriptor.HIGHER_PRIORITY);
  }

  @NotNull
  public Set<UsageDescriptor> getUsages() {
    return ContainerUtil.map2Set(PluginManagerCore.getDisabledPlugins(), descriptor -> new UsageDescriptor(descriptor, 1));
  }
}
