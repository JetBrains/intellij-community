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
package org.jetbrains.plugins.gradle.internal.task;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.ExternalSystemFacadeManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.externalSystem.build.ExternalSystemBuildManager;

import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 3/15/13 10:02 PM
 */
public class GradleExecuteTaskTask extends AbstractGradleTask {

  @NotNull private final List<String> myTasksToExecute;
  @NotNull private final String       myGradleProjectPath;

  public GradleExecuteTaskTask(@Nullable Project project, @NotNull String gradleProjectPath, @NotNull List<String> tasksToExecute) {
    super(project, ExternalSystemTaskType.EXECUTE_TASK);
    myGradleProjectPath = gradleProjectPath;
    myTasksToExecute = tasksToExecute;
  }

  @Override
  protected void doExecute() throws Exception {
    final ExternalSystemFacadeManager manager = ServiceManager.getService(ExternalSystemFacadeManager.class);
    Project project = getIdeProject();
    ExternalSystemBuildManager buildManager = manager.getFacade(project).getBuildManager();
    setState(GradleTaskState.IN_PROGRESS);
    try {
      buildManager.executeTasks(getId(), myTasksToExecute, myGradleProjectPath);
    }
    finally {
      setState(GradleTaskState.FINISHED);
    }
  }
}
