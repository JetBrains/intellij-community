// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.initScript.util;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.tasks.TaskCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public final class GradleTasksUtil {

  private static @Nullable String getRelativeTaskPath(@NotNull String targetProjectPath, @NotNull Task task) {
    String taskPath = task.getPath();
    if (taskPath.startsWith(targetProjectPath + ":")) {
      return taskPath.substring(targetProjectPath.length() + 1);
    }
    else if (taskPath.startsWith(targetProjectPath)) {
      return taskPath.substring(targetProjectPath.length());
    }
    else {
      return null;
    }
  }

  private static @NotNull List<String> getPossibleTaskNames(@NotNull String targetProjectPath, @NotNull Task task) {
    String relativeTaskPath = getRelativeTaskPath(targetProjectPath, task);
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

  private static @NotNull TaskCollection<Task> getMatchedTasks(
    @NotNull String targetProjectPath,
    @NotNull TaskCollection<Task> tasks,
    @NotNull List<String> matchers
  ) {
    return tasks.matching(task -> {
      List<String> possibleNames = getPossibleTaskNames(targetProjectPath, task);
      for (String possibleName : possibleNames) {
        for (String matcher : matchers) {
          if (possibleName.startsWith(matcher)) {
            return true;
          }
        }
      }
      return false;
    });
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

  private static @NotNull String getTargetProjectPath(@NotNull Project project) {
    Gradle gradle = project.getGradle();
    File currentDir = gradle.getStartParameter().getCurrentDir();
    for (Project globalProject : gradle.getRootProject().getAllprojects()) {
      if (globalProject.getProjectDir().equals(currentDir)) {
        return globalProject.getPath();
      }
    }
    throw new IllegalArgumentException("Cannot find project by working directory " + currentDir);
  }

  public static @NotNull TaskCollection<Task> getStartTasks(@NotNull Project project) {
    project.getLogger().debug("Project: {}", project);

    String targetProjectPath = getTargetProjectPath(project);
    project.getLogger().debug("Target Project Path: {}", targetProjectPath);

    List<String> startTaskNames = getStartTaskNames(project);
    project.getLogger().debug("Start Tasks Names: {}", startTaskNames);

    return getMatchedTasks(targetProjectPath, project.getTasks(), startTaskNames);
  }
}
