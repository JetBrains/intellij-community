// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.impl;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.openapi.project.Project;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskRepositoryType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TaskManagementUsageCollector extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("task.management", 1);
  private static final ClassEventField REPOSITORY_TYPE = EventFields.Class("repository_type");

  private static final EventId1<Class<?>> COLLECT_REMOTE_TASKS = GROUP.registerEvent("collect.remote.tasks", REPOSITORY_TYPE);
  private static final EventId1<Class<?>> OPEN_REMOTE_TASK = GROUP.registerEvent("open.remote.task", REPOSITORY_TYPE);
  private static final EventId CREATE_LOCAL_TASK_MANUALLY = GROUP.registerEvent("create.local.task.manually");

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void logCollectRemoteTasks(@NotNull Project project, @NotNull TaskRepository repository) {
    COLLECT_REMOTE_TASKS.log(project, getRepositoryType(repository));
  }

  public static void logOpenRemoteTask(@NotNull Project project, @NotNull Task task) {
    OPEN_REMOTE_TASK.log(project, getRepositoryType(task));
  }

  public static void logCreateLocalTaskManually(@NotNull Project project) {
    CREATE_LOCAL_TASK_MANUALLY.log(project);
  }

  private static @Nullable Class<?> getRepositoryType(@NotNull TaskRepository repository) {
    TaskRepositoryType<?> repositoryType = repository.getRepositoryType();
    return repositoryType != null ? repositoryType.getClass() : null;
  }

  private static @Nullable Class<?> getRepositoryType(@NotNull Task task) {
    TaskRepository repository = task.getRepository();
    return repository != null ? getRepositoryType(repository) : null;
  }
}
