// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.integrations.maven;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * {@link GradleMavenProjectImportNotificationListener} listens for Gradle project import events.
 *
 * @author Vladislav.Soroka
 */
final class GradleMavenProjectImportNotificationListener implements ExternalSystemTaskNotificationListener {
  @Override
  public void onSuccess(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
    if (GradleConstants.SYSTEM_ID.getId().equals(id.getProjectSystemId().getId())
        && id.getType() == ExternalSystemTaskType.RESOLVE_PROJECT) {
      Project project = id.findProject();
      if (project != null) {
        new ImportMavenRepositoriesTask(project).schedule();
      }
    }
  }
}
