// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.legacy.ui;

import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.collectors.fus.ui.persistence.ShortcutsCollector;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@Deprecated // to be removed in 2018.2
public final class LegacyShortcutUsagesCollector extends UsagesCollector {
  private static final GroupDescriptor GROUP = GroupDescriptor.create(getGroupName(), GroupDescriptor.HIGHER_PRIORITY);

  private static String getGroupName() {
    if (SystemInfo.isMac) return "Shortcuts on Mac";
    if (SystemInfo.isWindows) return "Shortcuts on Windows";
    if (SystemInfo.isLinux) return "Shortcuts on Linux";
    return "Shortcuts on OtherOs";
  }

  @NotNull
  public Set<UsageDescriptor> getUsages() {
    ShortcutsCollector.MyState state = ShortcutsCollector.getInstance().getState();
    return ContainerUtil.map2Set(state.myValues.entrySet(), e -> new UsageDescriptor(e.getKey(), e.getValue()));
  }

  @NotNull
  public GroupDescriptor getGroupId() {
    return GROUP;
  }
}
