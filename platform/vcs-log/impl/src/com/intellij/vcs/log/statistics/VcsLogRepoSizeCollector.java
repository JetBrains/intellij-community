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
import com.intellij.internal.statistic.StatisticsUtilKt;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.data.VisiblePack;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.impl.VcsLogContentProvider;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static java.util.Arrays.asList;

@SuppressWarnings("StringToUpperCaseOrToLowerCaseWithoutLocale")
public class VcsLogRepoSizeCollector extends AbstractApplicationUsagesCollector {

  public static final GroupDescriptor ID = GroupDescriptor.create("VCS Log 2");

  @NotNull
  @Override
  public Set<UsageDescriptor> getProjectUsages(@NotNull Project project) throws CollectUsagesException {
    VcsLogManager logManager = VcsLogContentProvider.findLogManager(project);
    VisiblePack dataPack = getDataPack(logManager);
    if (dataPack != null) {
      PermanentGraph<Integer> permanentGraph = dataPack.getPermanentGraph();
      MultiMap<VcsKey, VirtualFile> groupedRoots = groupRootsByVcs(dataPack.getLogProviders());

      Set<UsageDescriptor> usages = ContainerUtil.newHashSet();
      usages.add(StatisticsUtilKt.getCountingUsage("data.commit.count", permanentGraph.getAllCommits().size(),
                                                   asList(0, 1, 100, 1000, 10 * 1000, 100 * 1000, 500 * 1000)));
      for (VcsKey vcs : groupedRoots.keySet()) {
        usages.add(StatisticsUtilKt.getCountingUsage("data." + vcs.getName().toLowerCase() + ".root.count",
                                                     groupedRoots.get(vcs).size(),
                                                     asList(0, 1, 2, 5, 8, 15, 30, 50, 100)
        ));
      }
      return usages;
    }
    return Collections.emptySet();
  }

  @Nullable
  private static VisiblePack getDataPack(@Nullable VcsLogManager logManager) {
    if (logManager != null) {
      final VcsLogUiImpl ui = logManager.getLogUi();
      if (ui != null) {
        final Ref<VisiblePack> dataPack = Ref.create();
        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
          @Override
          public void run() {
            dataPack.set(ui.getDataPack());
          }
        }, ModalityState.defaultModalityState());
        return dataPack.get();
      }
    }
    return null;
  }

  @NotNull
  private static MultiMap<VcsKey, VirtualFile> groupRootsByVcs(@NotNull Map<VirtualFile, VcsLogProvider> providers) {
    MultiMap<VcsKey, VirtualFile> result = MultiMap.create();
    for (Map.Entry<VirtualFile, VcsLogProvider> entry : providers.entrySet()) {
      VirtualFile root = entry.getKey();
      VcsKey vcs = entry.getValue().getSupportedVcs();
      result.putValue(vcs, root);
    }
    return result;
  }

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return ID;
  }
}
