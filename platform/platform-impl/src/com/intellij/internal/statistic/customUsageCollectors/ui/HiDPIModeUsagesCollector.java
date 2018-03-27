// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.customUsageCollectors.ui;

import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

/**
 * @author tav
 */
public class HiDPIModeUsagesCollector extends UsagesCollector {
  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    String mode = UIUtil.isJreHiDPIEnabled() ? "per-monitor-dpi" : "system-dpi";
    String os = SystemInfo.isWindows ? "Windows" : SystemInfo.isLinux ? "Linux" : SystemInfo.isMac ? "Mac" : "Unknown OS";
    return Collections.singleton(new UsageDescriptor(os + " " + mode, 1));
  }

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create("user.ui.hidpi.mode");
  }
}
