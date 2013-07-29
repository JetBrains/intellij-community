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

import com.intellij.openapi.externalSystem.task.ExternalSystemTaskManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 3/14/13 5:09 PM
 */
public class GradleTaskManager implements ExternalSystemTaskManager<GradleExecutionSettings> {
  
  private final GradleExecutionHelper myHelper = new GradleExecutionHelper();

  @Override
  public void executeTasks(@NotNull final ExternalSystemTaskId id,
                           @NotNull final List<String> taskNames,
                           @NotNull String projectPath,
                           @Nullable final GradleExecutionSettings settings,
                           @Nullable final String vmOptions,
                           @NotNull final ExternalSystemTaskNotificationListener listener) throws ExternalSystemException
  {
    Function<ProjectConnection, Void> f = new Function<ProjectConnection, Void>() {
      @Override
      public Void fun(ProjectConnection connection) {
        BuildLauncher launcher = myHelper.getBuildLauncher(id, connection, settings, listener);
        if (!StringUtil.isEmpty(vmOptions)) {
          assert vmOptions != null;
          launcher.setJvmArguments(vmOptions.trim());
        }
        launcher.forTasks(ArrayUtil.toStringArray(taskNames));
        launcher.run();
        return null;
      }
    };
    myHelper.execute(projectPath, settings, f); 
  }
}
