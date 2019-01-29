// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.plugins;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.internal.statistic.utils.StatisticsUtilKt;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.stream.Collectors;

public class DisabledPluginsUsagesCollector extends ApplicationUsagesCollector {

  @Override
  @NotNull
  public String getGroupId() {
    return "plugins.disabled";
  }

  @Override
  @NotNull
  public Set<UsageDescriptor> getUsages() {
    return PluginManagerCore.getDisabledPlugins().stream().filter(id -> StatisticsUtilKt.isSafeToReport(id))
      .map(descriptor -> new UsageDescriptor(descriptor, 1)).collect(Collectors.toSet());
  }
}
