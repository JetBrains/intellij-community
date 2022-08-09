// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.task;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Listens for Gradle tasks execution.
 * <br>
 * Internal extension point to support internal functionality. E.g., vfs updates for java-related projects
 */
@ApiStatus.Internal
public interface GradleTaskResultListener {

  ExtensionPointName<GradleTaskResultListener> EP_NAME = ExtensionPointName.create("org.jetbrains.plugins.gradle.taskResultListener");

  /**
   * Called right after successful execution of Gradle task, before returning to External System API
   */
  void onSuccess(@NotNull ExternalSystemTaskId id, @NotNull String projectPath);
}
