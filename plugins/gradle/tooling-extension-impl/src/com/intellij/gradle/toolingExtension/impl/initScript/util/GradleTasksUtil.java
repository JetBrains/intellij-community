// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.initScript.util;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.invocation.Gradle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class GradleTasksUtil {

  private static @Nullable String getRelativeTaskPath(@NotNull Project project, @NotNull Task task) {
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

  private static @NotNull List<String> getPossibleTaskNames(@NotNull Project project, @NotNull Task task) {
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

  private static @NotNull MatchResult isMatchedTask(@NotNull Project project, @NotNull Task task, @NotNull List<String> matchers) {
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

  private static @NotNull List<Task> getMatchedTasks(@NotNull Project project, @NotNull List<String> matchers) {
    Map<Task, MatchResult> tasksMatchStatus = new LinkedHashMap<>();
    for (Task task : project.getTasks()) {
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

  private static @NotNull List<String> getStartTaskNames(@NotNull Project project) {
    Gradle gradle = project.getGradle();
    Gradle rootGradle = getRootGradle(gradle);

    List<String> startTaskNames = rootGradle.getStartParameter().getTaskNames();

    String compositePathPrefix = getCompositePathPrefix(gradle);
    project.getLogger().debug("Composite path prefix: {}", compositePathPrefix);

    List<String> relativeStartTaskNames = new ArrayList<>();
    for (String startTaskName : startTaskNames) {
      relativeStartTaskNames.add(
        startTaskName.startsWith(compositePathPrefix)
        ? startTaskName.substring(compositePathPrefix.length())
        : startTaskName
      );
    }
    startTaskNames = relativeStartTaskNames;

    if (startTaskNames.isEmpty()) {
      startTaskNames = project.getDefaultTasks();
    }

    return startTaskNames;
  }

  public static @NotNull List<Task> getStartTasks(@NotNull Project project) {
    project.getLogger().debug("Project: {}", project);

    List<String> startTaskNames = getStartTaskNames(project);
    project.getLogger().debug("Start Tasks Names: {}", startTaskNames);

    List<Task> startTasks = getMatchedTasks(project, startTaskNames);
    project.getLogger().debug("Start Tasks: {}", startTasks);

    return startTasks;
  }
}
