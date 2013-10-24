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
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

/**
 * @author Vladislav.Soroka
 * @since 10/15/13
 */
public class ProjectResolverContext {
  @NotNull private final ExternalSystemTaskId myExternalSystemTaskId;
  @NotNull private final String myProjectPath;
  @Nullable private final GradleExecutionSettings mySettings;
  @NotNull private final ProjectConnection myConnection;
  @NotNull private final ExternalSystemTaskNotificationListener myListener;
  private final boolean myIsPreviewMode;
  @NotNull
  private ProjectImportAction.AllModels myModels;

  public ProjectResolverContext(@NotNull final ExternalSystemTaskId externalSystemTaskId,
                                @NotNull final String projectPath,
                                @Nullable final GradleExecutionSettings settings,
                                @NotNull final ProjectConnection connection,
                                @NotNull final ExternalSystemTaskNotificationListener listener,
                                final boolean isPreviewMode) {
    myExternalSystemTaskId = externalSystemTaskId;
    myProjectPath = projectPath;
    mySettings = settings;
    myConnection = connection;
    myListener = listener;
    myIsPreviewMode = isPreviewMode;
  }

  @NotNull
  public ExternalSystemTaskId getExternalSystemTaskId() {
    return myExternalSystemTaskId;
  }

  @NotNull
  public String getProjectPath() {
    return myProjectPath;
  }

  @Nullable
  public GradleExecutionSettings getSettings() {
    return mySettings;
  }

  @NotNull
  public ProjectConnection getConnection() {
    return myConnection;
  }

  @NotNull
  public ExternalSystemTaskNotificationListener getListener() {
    return myListener;
  }

  public boolean isPreviewMode() {
    return myIsPreviewMode;
  }

  @NotNull
  public ProjectImportAction.AllModels getModels() {
    return myModels;
  }

  public void setModels(@NotNull ProjectImportAction.AllModels models) {
    myModels = models;
  }

  @Nullable
  public <T> T getExtraProject(@NotNull IdeaModule module, Class<T> modelClazz) {
    return myModels.getExtraProject(module, modelClazz);
  }
}
