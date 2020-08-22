// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.statistics;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.beans.MetricEventFactoryKt;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
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
import java.util.Map;
import java.util.Set;

@NonNls
public class VcsLogRepoSizeCollector extends ProjectUsagesCollector {

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
        Set<MetricEvent> usages = ContainerUtil.newHashSet(new MetricEvent("dataInitialized"));
        usages.add(MetricEventFactoryKt.newCounterMetric("commit.count", StatisticsUtil.getNextPowerOfTwo(commitCount)));
        usages.add(MetricEventFactoryKt.newCounterMetric("branches.count", StatisticsUtil.getNextPowerOfTwo(branchesCount)));
        usages.add(MetricEventFactoryKt.newCounterMetric("users.count", StatisticsUtil.getNextPowerOfTwo(usersCount)));
        MultiMap<VcsKey, VirtualFile> groupedRoots = groupRootsByVcs(dataPack.getLogProviders());
        for (VcsKey vcs : groupedRoots.keySet()) {
          FeatureUsageData vcsData = new FeatureUsageData().addData("vcs", getVcsKeySafe(vcs));
          int rootCount = groupedRoots.get(vcs).size();
          usages.add(MetricEventFactoryKt.newCounterMetric("root.count", StatisticsUtil.getNextPowerOfTwo(rootCount), vcsData));
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

  @NotNull
  @Override
  public String getGroupId() {
    return "vcs.log.data";
  }

  @Override
  public int getVersion() {
    return 3;
  }
}
