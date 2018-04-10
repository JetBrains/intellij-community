// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.legacy.actions;

import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ToolWindowCollector;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
public class LegacyToolWindowUsagesCollector extends UsagesCollector {
  private static final GroupDescriptor GROUP = GroupDescriptor.create("Tool Windows", GroupDescriptor.HIGHER_PRIORITY);

  @NotNull
  public Set<UsageDescriptor> getUsages() {
    ToolWindowCollector.State state = ToolWindowCollector.getInstance().getState();
    assert state != null;
    return ContainerUtil.map2Set(state.myValues.entrySet(), e -> new UsageDescriptor(e.getKey(), e.getValue()));
  }

  @NotNull
  public GroupDescriptor getGroupId() {
    return GROUP;
  }
}
