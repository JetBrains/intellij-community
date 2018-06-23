// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.beans.FSContent;
import com.intellij.internal.statistic.service.fus.beans.FSGroup;
import com.intellij.internal.statistic.service.fus.beans.FSSession;
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
      Map<String, Set<UsageDescriptor>> usages = getProjectUsages(project, approvedGroups);
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
  private Map<String, Set<UsageDescriptor>> getApplicationUsages(@NotNull Set<String> approvedGroups) {
    synchronized (LOCK) {
      Map<String, Set<UsageDescriptor>> usageDescriptors = new LinkedHashMap<>();
      for (ApplicationUsagesCollector usagesCollector : ApplicationUsagesCollector.getExtensions(this)) {

        collectUsages(usageDescriptors, usagesCollector, usagesCollector::getUsages, approvedGroups);
      }

      return usageDescriptors;
    }
  }

  private static void collectUsages(@NotNull Map<String, Set<UsageDescriptor>> usageDescriptors,
                                    @NotNull FeatureUsagesCollector usagesCollector,
                                    @NotNull Factory<Set<UsageDescriptor>> usagesProducer,
                                    @NotNull Set<String> approvedGroups) {
    if (!usagesCollector.isValid()) return;
    if (approvedGroups.contains(usagesCollector.getGroupId())) {
      addUsageDescriptors(usagesCollector.getGroupId(), usageDescriptors, usagesProducer);
    } else if (ApplicationManagerEx.getApplicationEx().isInternal()) {
      addUsageDescriptors(createDebugModeId(usagesCollector.getGroupId()), usageDescriptors, usagesProducer);
    }
  }

  @NotNull
  public static String createDebugModeId(@NotNull String groupId) {
    return "internal." + groupId;
  }

  private static void addUsageDescriptors(@NotNull String groupDescriptor, @NotNull Map<String, Set<UsageDescriptor>> allUsageDescriptors,
                                          @NotNull Factory<Set<UsageDescriptor>> usagesProducer) {
    Set<UsageDescriptor> usages = usagesProducer.create();
    usages = usages.stream().filter(descriptor -> descriptor.getValue() > 0).collect(Collectors.toSet());
    if (!usages.isEmpty()) {
      allUsageDescriptors.merge(groupDescriptor, usages, ContainerUtil::union);
    }
  }

  @NotNull
  Map<String, Set<UsageDescriptor>> getProjectUsages(@NotNull Project project, @NotNull Set<String> approvedGroups) {
    synchronized (LOCK) {
      Map<String, Set<UsageDescriptor>> usageDescriptors = new LinkedHashMap<>();
      for (ProjectUsagesCollector usagesCollector : ProjectUsagesCollector.getExtensions(this)) {
        collectUsages(usageDescriptors, usagesCollector, () -> usagesCollector.getUsages(project), approvedGroups);
      }
      return usageDescriptors;
    }
  }

  static void writeContent(@NotNull FSContent content,
                           @NotNull FUSession fuSession,
                           @NotNull Map<String, Set<UsageDescriptor>> usages) {
    FSSession session = FSSession.create(fuSession);
    if (content.getSessions() != null && content.getSessions().contains(session)) return;
    for (Map.Entry<String, Set<UsageDescriptor>> group : usages.entrySet()) {
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
