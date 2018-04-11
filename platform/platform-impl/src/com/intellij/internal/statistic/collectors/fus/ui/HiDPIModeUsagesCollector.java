// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

/**
 * @author tav
 */
public class HiDPIModeUsagesCollector extends ApplicationUsagesCollector {
  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    String mode = UIUtil.isJreHiDPIEnabled() ? "per_monitor_dpi" : "system_dpi";
    String os = SystemInfo.isWindows ? "Windows" : SystemInfo.isLinux ? "Linux" : SystemInfo.isMac ? "Mac" : "UnknownOS";
    return Collections.singleton(new UsageDescriptor(os + "." + mode, 1));
  }

  @NotNull
  @Override
  public String getGroupId() {
    return "statistics.ui.hidpi.mode";
  }
}
