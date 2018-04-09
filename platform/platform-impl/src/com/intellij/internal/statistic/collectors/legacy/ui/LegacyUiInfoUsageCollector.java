// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.legacy.ui;

import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.collectors.fus.ui.UiInfoUsageCollector;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
@Deprecated // to be removed in 2018.2
class LegacyUiInfoUsageCollector extends UsagesCollector {
  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
   return UiInfoUsageCollector.getDescriptors();
  }

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create("UI Features");
  }
}
