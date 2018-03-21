// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

// see example:
// public final class MyApplicationActionsUsageTriggerCollector extends ApplicationUsageTriggerCollector {
//    public static void record(@NotNull String metric) {
//       FUSApplicationUsageTrigger.getInstance().trigger(MyApplicationActionsUsageTriggerCollector.class, metric);
//   }
//
//   public String getGroupId() { return "statistics.my.application.actions";}
//  }
//  in any place of code write: MyApplicationActionsUsageTriggerCollector.record("my.cool.action.performed");

public abstract class ApplicationUsageTriggerCollector extends ApplicationUsagesCollector implements FUStatisticsDifferenceSender {
  @NotNull
  @Override
  public final Set<UsageDescriptor> getUsages() {
    Map<String, Integer> data = FUSApplicationUsageTrigger.getInstance().getData(getGroupId());

    return ContainerUtil.map2Set(data.entrySet(), e -> new UsageDescriptor(e.getKey(), e.getValue()));
  }
}
