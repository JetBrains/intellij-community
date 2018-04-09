// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.legacy.ui;

import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.collectors.fus.ui.persistence.ToolbarClicksCollector;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@Deprecated // to be removed in 2018.2
public final class LegacyToolbarClicksUsagesCollector extends UsagesCollector {
  private static final GroupDescriptor GROUP = GroupDescriptor.create("Toolbar Clicks", GroupDescriptor.HIGHER_PRIORITY);

  @NotNull
  public Set<UsageDescriptor> getUsages() {
    ToolbarClicksCollector.ClicksState state = ToolbarClicksCollector.getInstance().getState();
    assert state != null;
    return ContainerUtil.map2Set(state.myValues.entrySet(), e -> new UsageDescriptor(e.getKey(), e.getValue()));
  }

  @NotNull
  public GroupDescriptor getGroupId() {
    return GROUP;
  }
}
