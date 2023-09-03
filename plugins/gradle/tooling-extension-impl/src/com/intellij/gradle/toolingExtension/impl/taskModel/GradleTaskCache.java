// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.taskModel;

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.MessageBuilder;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext.DataProvider;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


/**
 * Provides fast access to the {@link Project}'s tasks.
 */
public final class GradleTaskCache {

  private final ModelBuilderContext context;
  private final ConcurrentMap<Project, Set<Task>> allTasks;

  private GradleTaskCache(@NotNull ModelBuilderContext context) {
    this.context = context;
    this.allTasks = new ConcurrentHashMap<>();
  }

  public Set<Task> getTasks(@NotNull Project project) {
    Set<Task> result = new LinkedHashSet<>(getProjectTasks(project));
    for (Project subProject : project.getSubprojects()) {
      result.addAll(getProjectTasks(subProject));
    }
    return result;
  }

  private Set<Task> getProjectTasks(@NotNull Project project) {
    Set<Task> projectTasks = allTasks.get(project);
    if (projectTasks == null) {
      Exception stackTrace = new IllegalStateException();
      Message message = MessageBuilder.create(
        "Project tasks aren't found",
        "Tasks for " + project + " wasn't collected. " +
        "All tasks should be collected during " + GradleModelFetchPhase.TASK_WARM_UP_PHASE + "."
      ).error().withException(stackTrace).build();
      context.report(project, message);
      return Collections.emptySet();
    }
    return projectTasks;
  }

  public void setTasks(@NotNull Project project, @NotNull Set<Task> tasks) {
    Set<Task> previousTasks = allTasks.put(project, tasks);
    if (previousTasks != null) {
      Message message = MessageBuilder.create(
        "Project tasks redefinition",
        "Tasks for " + project + " was already collected."
      ).error().build();
      context.report(project, message);
    }
  }

  private static final @NotNull DataProvider<GradleTaskCache> INSTANCE_PROVIDER = GradleTaskCache::new;

  public static @NotNull GradleTaskCache getInstance(@NotNull ModelBuilderContext context) {
    return context.getData(INSTANCE_PROVIDER);
  }
}
