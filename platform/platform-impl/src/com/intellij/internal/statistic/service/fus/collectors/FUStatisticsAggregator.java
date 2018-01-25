// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.beans.gson.FSContent;
import com.intellij.internal.statistic.service.fus.beans.gson.FSGroup;
import com.intellij.internal.statistic.service.fus.beans.gson.FSSession;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.intellij.internal.statistic.utils.StatisticsUploadAssistant.LOCK;

public class FUStatisticsAggregator implements UsagesCollectorConsumer {
  private static final Logger LOG = Logger.getInstance("com.intellij.internal.statistic.service.whiteList.collectors.FUStatisticsAggregator.");

  public FUStatisticsAggregator() {
  }

  public static FUStatisticsAggregator create() {
     return new FUStatisticsAggregator();
  }

  @Nullable
  public  FSContent getUsageCollectorsData(@NotNull Set<String> approvedGroups ) {
    if (approvedGroups.isEmpty()) return null;

    FSContent content = FSContent.create();

    collectApplicationUsages(content, approvedGroups);
    collectOpenProjectUsages(content, approvedGroups);
    collectPersistedProjectUsages(content, approvedGroups);

    return content;
  }

  public void collectApplicationUsages(FSContent content, @NotNull Set<String> approvedGroups) {
    writeContent(content, FUSession.APPLICATION_SESSION, getApplicationUsages(approvedGroups));
  }

  public void collectOpenProjectUsages(@NotNull FSContent content, @NotNull Set<String> approvedGroups) {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      if (project.isDisposed()) continue;
      Map<String, Set<UsageDescriptor>> usages = getProjectUsages(project, approvedGroups);
      if (!usages.isEmpty()) {
        writeContent(content, FUSession.create(project), usages);
      }
    }
  }

  private void collectPersistedProjectUsages(@NotNull FSContent content,
                                             @NotNull Set<String> approvedGroups) {
    // todo: implement persistence component
  }

  @NotNull
  public Map<String, Set<UsageDescriptor>> getApplicationUsages(@NotNull Set<String> approvedGroups) {
    synchronized (LOCK) {
      Map<String, Set<UsageDescriptor>> usageDescriptors = new LinkedHashMap<>();
      for (ApplicationUsagesCollector usagesCollector : ApplicationUsagesCollector.getExtensions(this)) {
        if (!usagesCollector.isValid()) continue;
        String groupDescriptor = usagesCollector.getGroupId();
        if (!approvedGroups.contains(groupDescriptor)) continue;

        try {
          usageDescriptors.merge(groupDescriptor, usagesCollector.getUsages(), ContainerUtil::union);
        }
        catch (CollectUsagesException e) {
          LOG.info(e);
        }
      }

      return usageDescriptors;
    }
  }

  @NotNull
  public Map<String, Set<UsageDescriptor>> getProjectUsages(@NotNull Project project, @NotNull Set<String> approvedGroups) {
    synchronized (LOCK) {
      Map<String, Set<UsageDescriptor>> usageDescriptors = new LinkedHashMap<>();
      for (ProjectUsagesCollector usagesCollector : ProjectUsagesCollector.getExtensions(this)) {
        if (!usagesCollector.isValid()) continue;
        String groupDescriptor = usagesCollector.getGroupId();
        if (!approvedGroups.contains(groupDescriptor)) continue;

        try {
          usageDescriptors.merge(groupDescriptor, usagesCollector.getUsages(project), ContainerUtil::union);
        }
        catch (CollectUsagesException e) {
          LOG.info(e);
        }
      }

      return usageDescriptors;
    }
  }


  public void writeContent(FSContent content,
                           @NotNull FUSession fuSession,
                           Map<String, Set<UsageDescriptor>> usages) {
    FSSession session = FSSession.create(Integer.toString(fuSession.getId()), fuSession.getBuildId());
    if (content.getSessions() !=null && content.getSessions().contains(session)) return;

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
