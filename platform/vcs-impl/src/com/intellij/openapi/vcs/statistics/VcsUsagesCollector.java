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

import com.intellij.internal.statistic.AbstractProjectsUsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class VcsUsagesCollector extends AbstractProjectsUsagesCollector {
  private static final String GROUP_ID = "vcs";

  @NotNull
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create(GROUP_ID, GroupDescriptor.HIGHER_PRIORITY);
  }

  @NotNull
  public Set<UsageDescriptor> getProjectUsages(@NotNull Project project) {
    return ContainerUtil.map2Set(ProjectLevelVcsManager.getInstance(project).getAllActiveVcss(), vcs -> {
      return new UsageDescriptor(vcs.getName(), 1);
    });
  }
}
