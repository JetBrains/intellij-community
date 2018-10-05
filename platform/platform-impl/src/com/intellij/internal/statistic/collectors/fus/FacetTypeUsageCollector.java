// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class FacetTypeUsageCollector extends ProjectUsagesCollector {
  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages(@NotNull Project project) {
    Set<UsageDescriptor> set = ContainerUtil.newHashSet();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      module.getModuleTypeName();
      for (Facet facet : FacetManager.getInstance(module).getAllFacets()) {
        set.add(new UsageDescriptor(facet.getType().getStringId()));
      }
    }
    return set;
  }

  @NotNull
  @Override
  public String getGroupId() {
    return "statistics.module.facets";
  }
}
