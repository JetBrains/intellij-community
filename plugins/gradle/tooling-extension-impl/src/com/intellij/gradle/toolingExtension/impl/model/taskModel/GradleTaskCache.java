// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.taskModel;

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages;
import com.intellij.gradle.toolingExtension.impl.util.GradleProjectUtil;
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.tooling.model.ProjectIdentifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.Message;
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
  private final ConcurrentMap<ProjectIdentifier, Set<Task>> allTasks;

  private GradleTaskCache(@NotNull ModelBuilderContext context) {
    this.context = context;
    this.allTasks = new ConcurrentHashMap<>();
  }

  public Set<Task> getAllTasks(@NotNull Project project) {
    Set<Task> result = new LinkedHashSet<>(getProjectTasks(project));
    for (Project subProject : project.getSubprojects()) {
      result.addAll(getProjectTasks(subProject));
    }
    return result;
  }

  private Set<Task> getProjectTasks(@NotNull Project project) {
    ProjectIdentifier projectIdentifier = GradleProjectUtil.getProjectIdentifier(project);
    Set<Task> projectTasks = allTasks.get(projectIdentifier);
    if (projectTasks == null) {
      context.getMessageReporter().createMessage()
        .withGroup(Messages.TASK_CACHE_GET_GROUP)
        .withTitle("Task model aren't found")
        .withText(
          "Tasks for " + project.getDisplayName() + " wasn't collected. " +
          "All tasks should be collected during " + GradleModelFetchPhase.TASK_WARM_UP_PHASE + "."
        )
        .withStackTrace()
        .withKind(Message.Kind.INTERNAL)
        .reportMessage(project);
      return Collections.emptySet();
    }
    return projectTasks;
  }

  public void setProjectTasks(@NotNull Project project, @NotNull Set<Task> tasks) {
    ProjectIdentifier projectIdentifier = GradleProjectUtil.getProjectIdentifier(project);
    Set<Task> previousTasks = allTasks.put(projectIdentifier, tasks);
    if (previousTasks != null) {
      context.getMessageReporter().createMessage()
        .withGroup(Messages.TASK_CACHE_SET_GROUP)
        .withTitle("Task model redefinition")
        .withText("Tasks for " + project.getDisplayName() + " was already collected.")
        .withStackTrace()
        .withKind(Message.Kind.INTERNAL)
        .reportMessage(project);
    }
  }

  /**
   * Marks that a project task model is loaded with errors.
   * This mark means that error for {@code project} is already processed and reported.
   */
  public void markTaskModelAsError(@NotNull Project project) {
    ProjectIdentifier projectIdentifier = GradleProjectUtil.getProjectIdentifier(project);
    allTasks.put(projectIdentifier, Collections.emptySet());
  }

  private static final @NotNull DataProvider<GradleTaskCache> INSTANCE_PROVIDER = GradleTaskCache::new;

  public static @NotNull GradleTaskCache getInstance(@NotNull ModelBuilderContext context) {
    return context.getData(INSTANCE_PROVIDER);
  }
}
