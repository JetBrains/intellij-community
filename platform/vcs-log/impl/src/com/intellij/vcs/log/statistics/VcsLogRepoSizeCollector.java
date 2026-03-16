// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.statistics;

import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.eventLog.events.EventId2;
import com.intellij.internal.statistic.eventLog.events.RoundedIntEventField;
import com.intellij.internal.statistic.eventLog.events.StringEventField;
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
import com.intellij.vcs.log.VcsLogAggregatedStoredRefsKt;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.VcsLogGraphData;
import com.intellij.vcs.log.impl.VcsProjectLog;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApiStatus.Internal
public @NonNls class VcsLogRepoSizeCollector extends ProjectUsagesCollector {
  public static final RoundedIntEventField COMMIT_COUNT = EventFields.RoundedInt("commit_count");
  public static final RoundedIntEventField BRANCHES_COUNT = EventFields.RoundedInt("branches_count");
  public static final RoundedIntEventField TAGS_COUNT = EventFields.RoundedInt("tags_count");

  private static final EventLogGroup GROUP = new EventLogGroup("vcs.log.data", 6);
  private static final EventId DATA_INITIALIZED = GROUP.registerEvent("dataInitialized");
  private static final EventId1<Integer> COMMIT_COUNT_EVENT = GROUP.registerEvent("commit.count", COMMIT_COUNT);
  private static final EventId1<Integer> BRANCHES_COUNT_EVENT = GROUP.registerEvent("branches.count", BRANCHES_COUNT);
  private static final EventId1<Integer> USERS_COUNT = GROUP.registerEvent("users.count", EventFields.Count);
  public static final StringEventField VCS_FIELD = new StringEventField("vcs") {
    @Override
    public @NotNull List<String> getValidationRule() {
      return getVcsValidationRule();
    }
  };
  private static final EventId2<Integer, String>
    ROOT_COUNT = GROUP.registerEvent("root.count", EventFields.Count, VCS_FIELD);

  @Override
  public @NotNull Set<MetricEvent> getMetrics(@NotNull Project project) {
    if (!TrustedProjects.isProjectTrusted(project)) return Collections.emptySet();

    VcsProjectLog projectLog = project.getServiceIfCreated(VcsProjectLog.class);
    if (projectLog == null) return Collections.emptySet();

    VcsLogData logData = projectLog.getDataManager();
    if (logData != null) {
      VcsLogGraphData dataPack = logData.getGraphData();
      if (dataPack.isFull()) {
        int commitCount = dataPack.getPermanentGraph().getAllCommits().size();
        int branchesCount = VcsLogAggregatedStoredRefsKt.getBranches(dataPack.getRefsModel()).size();
        int usersCount = logData.getAllUsers().size();
        Set<MetricEvent> usages = ContainerUtil.newHashSet(DATA_INITIALIZED.metric());
        usages.add(COMMIT_COUNT_EVENT.metric(commitCount));
        usages.add(BRANCHES_COUNT_EVENT.metric(branchesCount));
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

  public static @NotNull String getVcsKeySafe(@NotNull VcsKey vcs) {
    if (PluginInfoDetectorKt.getPluginInfo(vcs.getClass()).isDevelopedByJetBrains()) {
      return UsageDescriptorKeyValidator.ensureProperKey(StringUtil.toLowerCase(vcs.getName()));
    }
    return "third.party";
  }

  static @NotNull List<String> getVcsValidationRule() {
    return List.of("{enum#vcs}", "{enum:third.party}");
  }

  private static @NotNull MultiMap<VcsKey, VirtualFile> groupRootsByVcs(@NotNull Map<VirtualFile, VcsLogProvider> providers) {
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
