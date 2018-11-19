// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.collectors.fus.ui.persistence.ToolbarClicksCollector;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
import com.intellij.internal.statistic.service.fus.collectors.FUStatisticsDifferenceSender;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator.ensureProperKey;

public final class ToolbarClicksUsagesCollector extends ApplicationUsagesCollector implements FUStatisticsDifferenceSender {
  public static final String GROUP_ID = "statistics.ui.toolbar.clicks";

  @Override
  @NotNull
  public Set<UsageDescriptor> getUsages() {
    ToolbarClicksCollector.ClicksState state = ToolbarClicksCollector.getInstance().getState();
    assert state != null;
    return ContainerUtil.map2Set(state.myValues.entrySet(), e -> new UsageDescriptor(ensureProperKey(e.getKey()), e.getValue()));
  }

  @Override
  @NotNull
  public String getGroupId() {
    return GROUP_ID;
  }

  @NotNull
  @Override
  public FUSUsageContext getContext() {
    return FUSUsageContext.OS_CONTEXT;
  }
}
