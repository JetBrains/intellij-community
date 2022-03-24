/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.tooling.builder;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.DefaultTaskContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.MessageBuilder;
import org.jetbrains.plugins.gradle.tooling.MessageReporter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TasksFactory {
  private static final boolean TASKS_REFRESH_REQUIRED =
    GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version("5.0")) < 0;
  private final Map<Project, Set<Task>> allTasks = new ConcurrentHashMap<Project, Set<Task>>();
  ConcurrentMap<Project, Lock> initializationLocks = new ConcurrentHashMap<Project, Lock>();
  private final Set<Project> processedRootProjects = Collections.newSetFromMap(new ConcurrentHashMap<Project, Boolean>());
  @NotNull private final MessageReporter myMessageReporter;

  public TasksFactory(@NotNull MessageReporter messageReporter) {
    myMessageReporter = messageReporter;
  }

  private void collectTasks(Project root) {
    allTasks.putAll(getAllTasks(root));
  }

  @NotNull
  private Map<Project, Set<Task>> getAllTasks(@NotNull Project root) {
    final Map<Project, Set<Task>> foundTargets = new TreeMap<Project, Set<Task>>();
    for (Project project : root.getAllprojects()) {
      try {
        maybeRefreshTasks(project);
        TaskContainer projectTasks = project.getTasks();
        foundTargets.put(project, new TreeSet<Task>(projectTasks));
      }
      catch (Exception e) {
        String title = "Can not load tasks for " + project;
        myMessageReporter.report(project, MessageBuilder.create(title, title).warning().withException(e).build());
      }
    }
    return foundTargets;
  }

  public Set<Task> getTasks(Project project) {
    Project rootProject = project.getRootProject();

    if (!processedRootProjects.contains(rootProject)) {
      Lock lock = new ReentrantLock();
      try {
        Lock existingLock = initializationLocks.putIfAbsent(rootProject, lock);
        if (existingLock != null) {
          lock = existingLock;
        }
        lock.lock();
        if (!processedRootProjects.contains(rootProject)) {
          collectTasks(rootProject);
          processedRootProjects.add(rootProject);
        }
      } finally {
        lock.unlock();
        initializationLocks.remove(project);
      }
    }

    Set<Task> tasks = new LinkedHashSet<Task>(getTasksNullsafe(project));
    for (Project subProject : project.getSubprojects()) {
      tasks.addAll(getTasksNullsafe(subProject));
    }
    return tasks;
  }

  private Set<Task> getTasksNullsafe(Project project) {
    Set<Task> tasks = allTasks.get(project);
    if (tasks != null) {
      return tasks;
    }
    else {
      return Collections.emptySet();
    }
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
}