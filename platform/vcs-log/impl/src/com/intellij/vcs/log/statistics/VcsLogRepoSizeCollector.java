/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log.statistics;

import com.intellij.internal.statistic.AbstractApplicationUsagesCollector;
import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.impl.VcsLogContentProvider;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class VcsLogRepoSizeCollector extends AbstractApplicationUsagesCollector {

  public static final GroupDescriptor ID = GroupDescriptor.create("VCS Log");

  @NotNull
  @Override
  public Set<UsageDescriptor> getProjectUsages(@NotNull Project project) throws CollectUsagesException {
    VcsLogManager logManager = VcsLogContentProvider.findLogManager(project);
    if (logManager != null) {
      VcsLogUiImpl ui = logManager.getLogUi();
      if (ui != null) {
        PermanentGraph<Integer> permanentGraph = ui.getDataPack().getPermanentGraph();
        Map<VcsKey, Integer> rootCounts = groupRootsByVcs(ui.getDataPack().getLogProviders());

        Set<UsageDescriptor> usages = ContainerUtil.newHashSet();
        usages.add(new UsageDescriptor("vcs.log.commit.count", permanentGraph.getAllCommits().size()));
        for (Map.Entry<VcsKey, Integer> entry : rootCounts.entrySet()) {
          usages.add(new RootUsage(entry.getKey(), entry.getValue()));
        }
        return usages;
      }
    }
    return Collections.emptySet();
  }

  @NotNull
  private static Map<VcsKey, Integer> groupRootsByVcs(@NotNull Map<VirtualFile, VcsLogProvider> providers) {
    Map<VcsKey, Integer> result = ContainerUtil.newHashMap();
    for (Map.Entry<VirtualFile, VcsLogProvider> entry : providers.entrySet()) {
      VcsLogProvider provider = entry.getValue();
      VcsKey vcs = provider.getSupportedVcs();
      Integer count = result.get(vcs);
      if (count == null) {
        result.put(vcs, 1);
      }
      else {
        result.put(vcs, count + 1);
      }
    }
    return result;
  }

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return ID;
  }

  @SuppressWarnings("StringToUpperCaseOrToLowerCaseWithoutLocale")
  private static class RootUsage extends UsageDescriptor {
    RootUsage(VcsKey vcs, int value) {
      super("vcs.log." + vcs.getName().toLowerCase() + ".root.count", value);
    }
  }

}
