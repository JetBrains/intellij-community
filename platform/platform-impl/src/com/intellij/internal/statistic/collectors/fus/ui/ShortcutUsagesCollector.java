// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.collectors.fus.ui.persistence.ShortcutsCollector;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.internal.statistic.service.fus.collectors.FUStatisticsDifferenceSender;
import com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator.*;

public final class ShortcutUsagesCollector extends ApplicationUsagesCollector implements FUStatisticsDifferenceSender {

  private static String getGroupName() {
    if (SystemInfo.isMac) return "statistics.ui.shortcuts.on.mac";
    if (SystemInfo.isWindows) return "statistics.ui.shortcuts.on.windows";
    if (SystemInfo.isLinux) return "statistics.ui.shortcuts.on.linux";
    return "statistics.ui.shortcuts.on.other.os";
  }

  @NotNull
  public Set<UsageDescriptor> getUsages() {
    ShortcutsCollector.MyState state = ShortcutsCollector.getInstance().getState();
    return ContainerUtil.map2Set(state.myValues.entrySet(), e -> new UsageDescriptor(ensureProperKey(e.getKey()), e.getValue()));
  }

  @NotNull
  public String getGroupId() {
    return getGroupName();
  }
}
