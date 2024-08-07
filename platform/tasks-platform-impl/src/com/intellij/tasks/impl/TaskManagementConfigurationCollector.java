// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.impl;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.ClassEventField;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

final class TaskManagementConfigurationCollector extends ProjectUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("task.management.configuration", 1);
  private static final ClassEventField REPOSITORY_TYPE = EventFields.Class("repository_type");

  private static final EventId1<Class<?>> CONFIGURED_REPOSITORY = GROUP.registerEvent("configured.repository", REPOSITORY_TYPE);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @Override
  protected @NotNull Set<MetricEvent> getMetrics(@NotNull Project project) {
    return ContainerUtil.map2Set(
      TaskManager.getManager(project).getAllRepositories(),
      repository -> CONFIGURED_REPOSITORY.metric(repository.getRepositoryType().getClass())
    );
  }
}
