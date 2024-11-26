// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.initScript.util;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class GradleTasksUtil {

  private static @Nullable String getRelativeTaskPath(@Nullable Project project, @NotNull Task task) {
    if (project == null) return null;
    String taskPath = task.getPath();
    String projectPath = project.getPath();
    if (taskPath.startsWith(projectPath + ":")) {
      return taskPath.substring(projectPath.length() + 1);
    }
    else if (taskPath.startsWith(projectPath)) {
      return taskPath.substring(projectPath.length());
    }
    else {
      return null;
    }
  }

  private static @NotNull List<String> getPossibleTaskNames(@Nullable Project project, @NotNull Task task) {
    String relativeTaskPath = getRelativeTaskPath(project, task);
    List<String> possibleTaskNames = new ArrayList<>();
    if (relativeTaskPath == null) {
      possibleTaskNames.add(task.getPath());
    }
    else {
      possibleTaskNames.add(task.getName());
      possibleTaskNames.add(task.getPath());
      possibleTaskNames.add(relativeTaskPath);
    }
    return possibleTaskNames;
  }

  private static @NotNull MatchResult isMatchedTask(@Nullable Project project, @NotNull Task task, @NotNull List<String> matchers) {
    List<String> possibleNames = getPossibleTaskNames(project, task);
    for (String possibleName : possibleNames) {
      for (String matcher : matchers) {
        if (possibleName.equals(matcher)) {
          return MatchResult.MATCHED;
        }
      }
    }
    for (String possibleName : possibleNames) {
      for (String matcher : matchers) {
        if (possibleName.startsWith(matcher)) {
          return MatchResult.PARTIALLY_MATCHED;
        }
      }
    }
    return MatchResult.NOT_MATCHED;
  }

  private static @NotNull List<Task> filterMatchedTasks(
    @Nullable Project project,
    @NotNull List<Task> tasks,
    @NotNull List<String> matchers
  ) {
    Map<Task, MatchResult> tasksMatchStatus = new LinkedHashMap<>();
    for (Task task : tasks) {
      tasksMatchStatus.put(task, isMatchedTask(project, task, matchers));
    }
    List<Task> matchedTasks = new ArrayList<>();
    for (Map.Entry<Task, MatchResult> entry : tasksMatchStatus.entrySet()) {
      if (entry.getValue().equals(MatchResult.MATCHED)) {
        matchedTasks.add(entry.getKey());
      }
    }
    if (!matchedTasks.isEmpty()) {
      return matchedTasks;
    }
    for (Map.Entry<Task, MatchResult> entry : tasksMatchStatus.entrySet()) {
      if (entry.getValue().equals(MatchResult.PARTIALLY_MATCHED)) {
        matchedTasks.add(entry.getKey());
      }
    }
    return matchedTasks;
  }

  private enum MatchResult {
    MATCHED, PARTIALLY_MATCHED, NOT_MATCHED
  }

  public static @NotNull Project getCurrentProject(@NotNull Gradle gradle, @NotNull Project rootProject) {
    String currentPath = gradle.getStartParameter().getCurrentDir().getPath();
    for (Project project : rootProject.getAllprojects()) {
      if (project.getProjectDir().getPath().equals(currentPath)) {
        return project;
      }
    }
    throw new IllegalArgumentException("Cannot find project by working directory " + currentPath);
  }

  private static @NotNull Gradle getRootGradle(@NotNull Gradle gradle) {
    Gradle rootGradle = gradle;
    while (rootGradle.getParent() != null) {
      rootGradle = rootGradle.getParent();
    }
    return rootGradle;
  }

  private static @NotNull String getCompositePathPrefix(@NotNull Gradle gradle) {
    Gradle rootGradle = gradle;
    LinkedList<String> compositePathPrefix = new LinkedList<>();
    while (rootGradle.getParent() != null) {
      compositePathPrefix.addFirst(rootGradle.getRootProject().getName());
      rootGradle = rootGradle.getParent();
    }
    if (compositePathPrefix.isEmpty()) {
      return "";
    }
    StringJoiner compositePathPrefixJoiner = new StringJoiner(":");
    for (String compositeName : compositePathPrefix) {
      compositePathPrefixJoiner.add(compositeName);
    }
    return ":" + compositePathPrefixJoiner;
  }

  public static @NotNull List<Task> filterStartTasks(@NotNull List<Task> tasks, @NotNull Gradle gradle, @NotNull Project rootProject) {
    Project currentProject = getCurrentProject(gradle, rootProject);
    Logger logger = currentProject.getLogger();
    logger.debug("Current Project: {}", currentProject);

    Gradle rootGradle = getRootGradle(gradle);
    List<String> startTaskNames = rootGradle.getStartParameter().getTaskNames();
    logger.debug("Start Tasks Names: {}", startTaskNames);

    String compositePathPrefix = getCompositePathPrefix(gradle);
    List<String> relativeStartTaskNames = new ArrayList<>();
    for (String startTaskName : startTaskNames) {
      relativeStartTaskNames.add(
        startTaskName.startsWith(compositePathPrefix)
        ? startTaskName.substring(compositePathPrefix.length())
        : startTaskName
      );
    }
    startTaskNames = relativeStartTaskNames;
    logger.debug("Start Tasks Names after cleanup: {}", startTaskNames);

    if (startTaskNames.isEmpty()) {
      startTaskNames = currentProject.getDefaultTasks();
    }

    List<Task> matchedTasks = filterMatchedTasks(currentProject, tasks, startTaskNames);
    logger.debug("Matched tasks: {}", matchedTasks);

    return matchedTasks;
  }
}
