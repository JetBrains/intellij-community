// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.legacy.ui;

import com.intellij.ide.ui.LafManager;
import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.Set;

@Deprecated // to be removed in 2018.2
public class LegacyLaFUsagesCollector extends UsagesCollector {
  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    UIManager.LookAndFeelInfo laf = LafManager.getInstance().getCurrentLookAndFeel();
    String key = SystemInfo.OS_NAME + " - ";
    if (!StringUtil.isEmptyOrSpaces(SystemInfo.SUN_DESKTOP)) {
      key += SystemInfo.SUN_DESKTOP + " - ";
    }
    return laf != null ? Collections.singleton(new UsageDescriptor(key + laf.getName(), 1))
                       : Collections.emptySet();
  }

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create("Look and Feel");
  }
}
