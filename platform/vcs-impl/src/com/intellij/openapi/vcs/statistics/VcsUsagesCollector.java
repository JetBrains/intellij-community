/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.statistics;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.impl.VcsDescriptor;
import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

// todo !!! this "statistics.usagesCollector" extension !!!
public class VcsUsagesCollector extends UsagesCollector {
  private static final String GROUP_ID = "vcs";

  public static void persistProjectUsages(@NotNull Project project) {
    persistProjectUsages(project, getProjectUsages(project));
  }

  public static void persistProjectUsages(@NotNull Project project, @NotNull Set<UsageDescriptor> usages) {
    persistProjectUsages(project, usages, VcsStatisticsPersistenceComponent.getInstance());
  }

  public static void persistProjectUsages(@NotNull Project project,
                                          @NotNull Set<UsageDescriptor> usages,
                                          @NotNull VcsStatisticsPersistenceComponent persistence) {
    persistence.persist(project, usages);
  }

  @NotNull
  public Set<UsageDescriptor> getApplicationUsages() {
    return getApplicationUsages(VcsStatisticsPersistenceComponent.getInstance());
  }

  @NotNull
  public Set<UsageDescriptor> getApplicationUsages(@NotNull final VcsStatisticsPersistenceComponent persistence) {
    final Map<String, Integer> vcsUsagesMap = new HashMap<String, Integer>();

    for (Set<UsageDescriptor> descriptors : persistence.getVcsUsageMap().values()) {
      for (UsageDescriptor descriptor : descriptors) {
        final String key = descriptor.getKey();
        final Integer count = vcsUsagesMap.get(key);
        vcsUsagesMap.put(key, count == null ? 1 : count.intValue() + 1);
      }
    }

    return ContainerUtil.map2Set(vcsUsagesMap.entrySet(), new Function<Map.Entry<String, Integer>, UsageDescriptor>() {
      @Override
      public UsageDescriptor fun(Map.Entry<String, Integer> vcsUsage) {
        return new UsageDescriptor(createGroupDescriptor(), vcsUsage.getKey(), vcsUsage.getValue());
      }
    });
  }

  @NotNull
  public String getGroupId() {
    return GROUP_ID;
  }

  @NotNull
  public Set<UsageDescriptor> getUsages(@Nullable Project project) {
    if (project != null) {
      persistProjectUsages(project, getProjectUsages(project));
    }

    return getApplicationUsages();
  }

  public static Set<UsageDescriptor> getProjectUsages(@NotNull Project project) {
    return ContainerUtil.map2Set(ProjectLevelVcsManager.getInstance(project).getAllVcss(), new Function<VcsDescriptor, UsageDescriptor>() {
      @Override
      public UsageDescriptor fun(VcsDescriptor descriptor) {
        return new UsageDescriptor(createGroupDescriptor(), descriptor.getName(), 1);
      }
    });
  }

  public static GroupDescriptor createGroupDescriptor() {
    return GroupDescriptor.create(GROUP_ID, GroupDescriptor.HIGHER_PRIORITY);
  }
}

