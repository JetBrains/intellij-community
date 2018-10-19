// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.internal.statistic.service.fus.beans.CollectorGroupDescriptor;
import com.intellij.internal.statistic.service.fus.beans.FSContent;
import com.intellij.internal.statistic.service.fus.beans.FSGroup;
import com.intellij.internal.statistic.service.fus.beans.FSSession;
import com.intellij.internal.statistic.utils.StatisticsUtilKt;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Factory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.internal.statistic.utils.StatisticsUploadAssistant.LOCK;

public class FUStatisticsAggregator implements UsagesCollectorConsumer {

  public FUStatisticsAggregator() {
  }

  public static FUStatisticsAggregator create() {
    return new FUStatisticsAggregator();
  }

  @Nullable
  public FSContent getUsageCollectorsData(@NotNull Set<String> approvedGroups) {
    if (approvedGroups.isEmpty() && !ApplicationManagerEx.getApplicationEx().isInternal()) return null;

    FSContent content = FSContent.create();

    collectApplicationUsages(content, approvedGroups);
    collectOpenProjectUsages(content, approvedGroups);
    collectPersistedProjectUsages(content);

    return content;
  }

  public void collectApplicationUsages(FSContent content, @NotNull Set<String> approvedGroups) {
    writeContent(content, FUSession.APPLICATION_SESSION, getApplicationUsages(approvedGroups));
  }

  private void collectOpenProjectUsages(@NotNull FSContent content, @NotNull Set<String> approvedGroups) {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      if (project.isDisposed()) continue;
      Map<CollectorGroupDescriptor, Set<UsageDescriptor>> usages = getProjectUsages(project, approvedGroups);
      if (!usages.isEmpty()) {
        writeContent(content, FUSession.create(project), usages);
      }
    }
  }

  private static void collectPersistedProjectUsages(@NotNull FSContent content) {
    for (FSSession session : FUStatisticsPersistence.getPersistedSessions()) {
      content.addSession(session);
    }
  }

  @NotNull
  private Map<CollectorGroupDescriptor, Set<UsageDescriptor>> getApplicationUsages(@NotNull Set<String> approvedGroups) {
    synchronized (LOCK) {
      Map<CollectorGroupDescriptor, Set<UsageDescriptor>> usageDescriptors = new LinkedHashMap<>();
      for (ApplicationUsagesCollector usagesCollector : ApplicationUsagesCollector.getExtensions(this)) {
        collectUsages(null, usageDescriptors, usagesCollector, usagesCollector.getContext(), usagesCollector::getUsages,
                        approvedGroups, isStateCollector(usagesCollector));
      }

      return usageDescriptors;
    }
  }

  private static void collectUsages(@Nullable Project project,
                                    @NotNull Map<CollectorGroupDescriptor, Set<UsageDescriptor>> usageDescriptors,
                                    @NotNull FeatureUsagesCollector usagesCollector,
                                    @Nullable FUSUsageContext context,
                                    @NotNull Factory<Set<UsageDescriptor>> usagesProducer,
                                    @NotNull Set<String> approvedGroups,
                                    boolean isStateCollector) {
    if (!usagesCollector.isValid()) return;
    if (approvedGroups.contains(usagesCollector.getGroupId())) {
      addUsageDescriptors(project, usagesCollector.getGroupId(), context, usageDescriptors, usagesProducer, isStateCollector);
    }
    else if (ApplicationManagerEx.getApplicationEx().isInternal()) {
      addUsageDescriptors(project, createDebugModeId(usagesCollector.getGroupId()), context, usageDescriptors, usagesProducer, isStateCollector);
    }
  }

  @NotNull
  public static String createDebugModeId(@NotNull String groupId) {
    return "internal." + groupId;
  }

  private static void addUsageDescriptors(@Nullable Project project, @NotNull String groupDescriptor,
                                          @Nullable FUSUsageContext context,
                                          @NotNull Map<CollectorGroupDescriptor, Set<UsageDescriptor>> allUsageDescriptors,
                                          @NotNull Factory<Set<UsageDescriptor>> usagesProducer, boolean isStateCollector) {
    Set<UsageDescriptor> usages = usagesProducer.create();
    usages = usages.stream().filter(descriptor -> descriptor.getValue() > 0).collect(Collectors.toSet());
    if (!usages.isEmpty()) {
      if (isStateCollector) {
        logUsagesAsStateEvents(project, groupDescriptor, context, usages);
      }
      CollectorGroupDescriptor collectorGroupDescriptor = CollectorGroupDescriptor.create(groupDescriptor, context);
      allUsageDescriptors.merge(collectorGroupDescriptor, usages, ContainerUtil::union);
    }
  }

  private static boolean isStateCollector(@NotNull FeatureUsagesCollector usagesCollector) {
    return !(usagesCollector instanceof FUStatisticsDifferenceSender);
  }

  private static void logUsagesAsStateEvents(@Nullable Project project, @NotNull String groupDescriptor,
                                             @Nullable FUSUsageContext context,
                                             @NotNull Set<UsageDescriptor> usages) {
    final FeatureUsageLogger logger = FeatureUsageLogger.INSTANCE;
    final Map<String, ?> groupData = StatisticsUtilKt.createData(project, context);
    for (UsageDescriptor usage : usages) {
      final Map<String, Object> eventData = StatisticsUtilKt.mergeWithEventData(groupData, usage.getContext(), usage.getValue());
      logger.logState(groupDescriptor, usage.getKey(), eventData);
    }
  }

  @NotNull
  Map<CollectorGroupDescriptor, Set<UsageDescriptor>> getProjectUsages(@NotNull Project project, @NotNull Set<String> approvedGroups) {
    synchronized (LOCK) {
      Map<CollectorGroupDescriptor, Set<UsageDescriptor>> usageDescriptors = new LinkedHashMap<>();
      for (ProjectUsagesCollector usagesCollector : ProjectUsagesCollector.getExtensions(this)) {
        collectUsages(project, usageDescriptors, usagesCollector, usagesCollector.getContext(project),
                        () -> usagesCollector.getUsages(project), approvedGroups, isStateCollector(usagesCollector));
      }
      return usageDescriptors;
    }
  }

  static void writeContent(@NotNull FSContent content,
                           @NotNull FUSession fuSession,
                           @NotNull Map<CollectorGroupDescriptor, Set<UsageDescriptor>> usages) {
    FSSession session = FSSession.create(fuSession);
    if (content.getSessions() != null && content.getSessions().contains(session)) return;
    for (Map.Entry<CollectorGroupDescriptor, Set<UsageDescriptor>> group : usages.entrySet()) {
      Set<UsageDescriptor> value = group.getValue();
      if (!value.isEmpty()) {
        session.addGroup(FSGroup.create(group.getKey(), value));
      }
    }
    if (session.hasGroups()) {
      content.addSession(session);
    }
  }
}
