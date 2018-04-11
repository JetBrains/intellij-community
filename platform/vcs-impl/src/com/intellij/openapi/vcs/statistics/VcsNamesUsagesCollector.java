// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.statistics;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VcsNamesUsagesCollector extends ProjectUsagesCollector {

  @NotNull
  public String getGroupId() {
    return "statistics.vcs.names";
  }

  @NotNull
  public Set<UsageDescriptor> getUsages(@NotNull Project project) {
    return getDescriptors(project);
  }

  @NotNull
  public static Set<UsageDescriptor> getDescriptors(@NotNull Project project) {
    Set<UsageDescriptor> usages = new HashSet<>();

    AbstractVcs[] activeVcss = ProjectLevelVcsManager.getInstance(project).getAllActiveVcss();
    List<String> vcsNames = ContainerUtil.map(activeVcss, AbstractVcs::getName);

    for (String vcs : vcsNames) {
      usages.add(new UsageDescriptor(vcs, 1));
    }

    if (vcsNames.size() > 1) {
      usages.add(new UsageDescriptor(StringUtil.join(ContainerUtil.sorted(vcsNames), ","), 1));
    }

    return usages;
  }
}
