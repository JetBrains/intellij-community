/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.service.task;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import java.util.Collections;
import java.util.List;

/**
 * This extension point allows overriding default Gradle task execution.
 * <br>
 * When IDEA needs to execute some Gradle tasks, implementing extension can intercept execution logic
 * and perform these tasks in its own manner
 */
public interface GradleTaskManagerExtension {

  ExtensionPointName<GradleTaskManagerExtension> EP_NAME = ExtensionPointName.create("org.jetbrains.plugins.gradle.taskManager");

  /**
   * @deprecated use {@link #executeTasks(ExternalSystemTaskId, List, String, GradleExecutionSettings, String, ExternalSystemTaskNotificationListener)}
   */
  @Deprecated(forRemoval = true)
  default boolean executeTasks(
    @NotNull ExternalSystemTaskId id,
    @NotNull List<String> taskNames,
    @NotNull String projectPath,
    @Nullable GradleExecutionSettings settings,
    @NotNull List<String> vmOptions,
    @NotNull List<String> scriptParameters,
    @Nullable String jvmParametersSetup,
    @NotNull ExternalSystemTaskNotificationListener listener
  ) throws ExternalSystemException {
    return false;
  }

  /**
   * Try executing tasks.
   *
   * @param id                 id of operation in IDEA terms
   * @param taskNames          names of tasks
   * @param projectPath        path to project, where tasks are executed
   * @param settings           gradle execution settings
   * @param jvmParametersSetup jvm parameters string
   * @param listener           should be called to notify IDEA on tasks' progress and status
   * @return false if tasks were not executed and IDEA should proceed with default logic, true - if tasks are executed and no
   * more actions are required
   */
  default boolean executeTasks(
    @NotNull ExternalSystemTaskId id,
    @NotNull List<String> taskNames,
    @NotNull String projectPath,
    @Nullable GradleExecutionSettings settings,
    @Nullable String jvmParametersSetup,
    @NotNull ExternalSystemTaskNotificationListener listener
  ) throws ExternalSystemException {
    List<String> vmOptions = settings != null ? settings.getJvmArguments() : Collections.emptyList();
    List<String> arguments = settings != null ? settings.getArguments() : Collections.emptyList();
    return executeTasks(id, taskNames, projectPath, settings, vmOptions, arguments, jvmParametersSetup, listener);
  }

  boolean cancelTask(
    @NotNull ExternalSystemTaskId id,
    @NotNull ExternalSystemTaskNotificationListener listener
  ) throws ExternalSystemException;
}
