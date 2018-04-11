// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

public abstract class FUSUsageTriggerCollector extends ProjectUsagesCollector implements  FUStatisticsDifferenceSender {
  @NotNull
  @Override
  public final Set<UsageDescriptor> getUsages(@NotNull Project project) {
    Map<String, Integer> data = FUSUsageTrigger.getInstance(project).getData(getGroupId());

    return ContainerUtil.map2Set(data.entrySet(), e -> new UsageDescriptor(e.getKey(), e.getValue()));
  }
}
