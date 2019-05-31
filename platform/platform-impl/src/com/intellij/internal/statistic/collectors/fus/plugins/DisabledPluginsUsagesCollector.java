// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.plugins;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.internal.statistic.utils.StatisticsUtilKt;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

final class DisabledPluginsUsagesCollector extends ApplicationUsagesCollector {
  @Override
  @NotNull
  public String getGroupId() {
    return "plugins.disabled";
  }

  @Override
  @NotNull
  public Set<UsageDescriptor> getUsages() {
    Set<UsageDescriptor> set = new THashSet<>();
    for (String id : PluginManagerCore.disabledPlugins()) {
      if (StatisticsUtilKt.isSafeToReport(id)) {
        set.add(new UsageDescriptor(id, 1));
      }
    }
    return set;
  }
}