// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Set;


public class RegistryProjectUsagesCollector extends ProjectUsagesCollector {
  private static final String GROUP_ID = "statistics.platform.registry.project";


  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages(@NotNull Project project) {
    return RegistryApplicationUsagesCollector.getChangedValuesUsages();
  }

  @NotNull
  @Override
  public String getGroupId() {
    return GROUP_ID;
  }
}
