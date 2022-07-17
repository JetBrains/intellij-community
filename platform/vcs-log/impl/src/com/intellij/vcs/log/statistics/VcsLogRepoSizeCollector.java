// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.statistics;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.internal.statistic.utils.StatisticsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.VcsProjectLog;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@NonNls
public class VcsLogRepoSizeCollector extends ProjectUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("vcs.log.data", 5);
  private static final EventId DATA_INITIALIZED = GROUP.registerEvent("dataInitialized");
  private static final EventId1<Integer> COMMIT_COUNT = GROUP.registerEvent("commit.count", EventFields.Count);
  private static final EventId1<Integer> BRANCHES_COUNT = GROUP.registerEvent("branches.count", EventFields.Count);
  private static final EventId1<Integer> USERS_COUNT = GROUP.registerEvent("users.count", EventFields.Count);
  public static final StringEventField VCS_FIELD = new StringEventField("vcs") {
    @NotNull
    @Override
    public List<String> getValidationRule() {
      return List.of("{enum#vcs}", "{enum:third.party}");
    }
  };
  private static final EventId2<Integer, String>
    ROOT_COUNT = GROUP.registerEvent("root.count", EventFields.Count, VCS_FIELD);

  @NotNull
  @Override
  public Set<MetricEvent> getMetrics(@NotNull Project project) {
    VcsProjectLog projectLog = VcsProjectLog.getInstance(project);
    VcsLogData logData = projectLog.getDataManager();
    if (logData != null) {
      DataPack dataPack = logData.getDataPack();
      if (dataPack.isFull()) {
        int commitCount = dataPack.getPermanentGraph().getAllCommits().size();
        int branchesCount = dataPack.getRefsModel().getBranches().size();
        int usersCount = logData.getAllUsers().size();
        Set<MetricEvent> usages = ContainerUtil.newHashSet(DATA_INITIALIZED.metric());
        usages.add(COMMIT_COUNT.metric(StatisticsUtil.roundToPowerOfTwo(commitCount)));
        usages.add(BRANCHES_COUNT.metric(StatisticsUtil.roundToPowerOfTwo(branchesCount)));
        usages.add(USERS_COUNT.metric(StatisticsUtil.roundToPowerOfTwo(usersCount)));
        MultiMap<VcsKey, VirtualFile> groupedRoots = groupRootsByVcs(dataPack.getLogProviders());
        for (VcsKey vcs : groupedRoots.keySet()) {
          int rootCount = groupedRoots.get(vcs).size();
          usages.add(ROOT_COUNT.metric(StatisticsUtil.roundToPowerOfTwo(rootCount), getVcsKeySafe(vcs)));
        }
        return usages;
      }
    }
    return Collections.emptySet();
  }

  @NotNull
  private static String getVcsKeySafe(@NotNull VcsKey vcs) {
    if (PluginInfoDetectorKt.getPluginInfo(vcs.getClass()).isDevelopedByJetBrains()) {
      return UsageDescriptorKeyValidator.ensureProperKey(StringUtil.toLowerCase(vcs.getName()));
    }
    return "third.party";
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

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }
}
