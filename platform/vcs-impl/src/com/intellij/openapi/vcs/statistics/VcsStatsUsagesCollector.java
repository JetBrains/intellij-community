// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.statistics;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.internal.statistic.utils.StatisticsUtilKt.getBooleanUsage;
import static com.intellij.internal.statistic.utils.StatisticsUtilKt.getCountingUsage;

public class VcsStatsUsagesCollector extends ProjectUsagesCollector {
  @NotNull
  public String getGroupId() {
    return "statistics.vcs.metrics";
  }

  @NotNull
  public Set<UsageDescriptor> getUsages(@NotNull Project project) {
    return getDescriptors(project);
  }

  @NotNull
  public static Set<UsageDescriptor> getDescriptors(@NotNull Project project) {
    Set<UsageDescriptor> usages = new HashSet<>();

    ChangeListManagerImpl clm = ChangeListManagerImpl.getInstanceImpl(project);
    ProjectLevelVcsManagerEx vcsManager = ProjectLevelVcsManagerEx.getInstanceEx(project);

    usages.add(getCountingUsage("active.changelists.count", clm.getChangeListsNumber()));
    usages.add(getCountingUsage("unversioned.files.count", clm.getUnversionedFiles().size()));
    usages.add(getCountingUsage("ignored.files.count", clm.getIgnoredFiles().size()));
    usages.add(getCountingUsage("vcs.roots.count", vcsManager.getAllVcsRoots().length));
    usages.add(getBooleanUsage("has.default.vcs.root.mapping", vcsManager.haveDefaultMapping() != null));

    return usages;
  }
}
