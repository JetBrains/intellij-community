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
package org.jetbrains.plugins.gradle.remote.impl;

import com.intellij.openapi.externalSystem.build.ExternalSystemBuildManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskDescriptor;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 3/14/13 5:09 PM
 */
public class GradleBuildManager implements ExternalSystemBuildManager<GradleExecutionSettings> {
  
  private final GradleExecutionHelper myHelper = new GradleExecutionHelper();

  @Override
  public Collection<ExternalSystemTaskDescriptor> listTasks(@NotNull final ExternalSystemTaskId id,
                                                            @NotNull String projectPath,
                                                            @Nullable final GradleExecutionSettings settings)
    throws ExternalSystemException
  {
    Function<ProjectConnection, Collection<ExternalSystemTaskDescriptor>> f =
      new Function<ProjectConnection, Collection<ExternalSystemTaskDescriptor>>() {
        @Nullable
        @Override
        public Collection<ExternalSystemTaskDescriptor> fun(ProjectConnection connection) {
          ModelBuilder<? extends IdeaProject> modelBuilder = myHelper.getModelBuilder(id, settings, connection, false);
          IdeaProject project = modelBuilder.get();
          DomainObjectSet<? extends IdeaModule> modules = project.getModules();
          if (modules == null) {
            return Collections.emptyList();
          }
          Set<ExternalSystemTaskDescriptor> result = new HashSet<ExternalSystemTaskDescriptor>();
          for (IdeaModule module : modules) {
            for (GradleTask task : module.getGradleProject().getTasks()) {
              String name = task.getName();
              if (name == null || name.trim().isEmpty()) {
                continue;
              }
              String s = name.toLowerCase();
              if (s.contains("idea") || s.contains("eclipse")) {
                continue;
              }
              result.add(new ExternalSystemTaskDescriptor(name, task.getDescription()));
            }
          }
          return result;
        }
      };
    return myHelper.execute(projectPath, settings, f);
  }

  @Override
  public void executeTasks(@NotNull final ExternalSystemTaskId id,
                           @NotNull final List<String> taskNames,
                           @NotNull String projectPath,
                           @Nullable final GradleExecutionSettings settings) throws ExternalSystemException
  {
    Function<ProjectConnection, Void> f = new Function<ProjectConnection, Void>() {
      @Override
      public Void fun(ProjectConnection connection) {
        BuildLauncher launcher = myHelper.getBuildLauncher(id, connection, settings);
        launcher.forTasks(ArrayUtil.toStringArray(taskNames));
        launcher.run();
        return null;
      }
    };
    myHelper.execute(projectPath, settings, f); 
  }
}
