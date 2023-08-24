// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.taskModel.provider;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.DefaultTaskContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.MessageBuilder;
import org.jetbrains.plugins.gradle.tooling.MessageReporter;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext.DataProvider;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides fast access to the {@link Project}'s tasks.
 *
 * @see TasksFactory#getTasks(Project)
 */
public class TasksFactory {
  private static final boolean TASKS_REFRESH_REQUIRED =
    GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version("5.0")) < 0;
  private final Map<Project, Set<Task>> allTasks = new ConcurrentHashMap<>();
  private final Set<Project> processedRootProjects = Collections.newSetFromMap(new ConcurrentHashMap<>());
  @NotNull private final MessageReporter myMessageReporter;

  public TasksFactory(@NotNull ModelBuilderContext context) {
    myMessageReporter = context;
  }

  private void collectTasks(Project project) {
    Project rootProject = project.getRootProject();
    if (processedRootProjects.add(rootProject)) {
      allTasks.putAll(getAllTasks(rootProject));
    }
  }

  @NotNull
  private Map<Project, Set<Task>> getAllTasks(@NotNull Project root) {
    final Map<Project, Set<Task>> foundTargets = new TreeMap<>();
    for (final Project project : root.getAllprojects()) {
      try {
        retryOnce(() -> {
          maybeRefreshTasks(project);
          TaskContainer projectTasks = project.getTasks();
          foundTargets.put(project, new TreeSet<>(projectTasks));
        });
      }
      catch (Exception e) {
        String title = "Can not load tasks for " + project;
        myMessageReporter.report(project, MessageBuilder.create(title, title).warning().withException(e).build());
      }
    }
    return foundTargets;
  }

  /**
   * Retries to launch given runnable.
   * If fails second time, throw exception from first failure.
   * @param r runnable to run
   */
  private static void retryOnce(Runnable r) {
    try {
      r.run();
    } catch (Exception first) {
      try {
        r.run();
      } catch (Exception second) {
        throw first;
      }
    }
  }

  public Set<Task> getTasks(Project project) {
    collectTasks(project);

    Set<Task> result = new LinkedHashSet<>();
    Set<Task> projectTasks = allTasks.get(project);
    if (projectTasks != null) {
      result.addAll(projectTasks);
    }
    for (Project subProject : project.getSubprojects()) {
      Set<Task> subProjectTasks = allTasks.get(subProject);
      if (subProjectTasks != null) {
        result.addAll(subProjectTasks);
      }
    }
    return result;
  }

  private static void maybeRefreshTasks(Project project) {
    if (TASKS_REFRESH_REQUIRED) {
      TaskContainer tasks = project.getTasks();
      if (tasks instanceof DefaultTaskContainer) {
        ((DefaultTaskContainer)tasks).discoverTasks();
        SortedSet<String> taskNames = tasks.getNames();
        for (String taskName : taskNames) {
          tasks.findByName(taskName);
        }
      }
    }
  }

  private static final @NotNull DataProvider<TasksFactory> INSTANCE_PROVIDER = TasksFactory::new;

  public static @NotNull TasksFactory getInstance(@NotNull ModelBuilderContext context) {
    return context.getData(INSTANCE_PROVIDER);
  }
}