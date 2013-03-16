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
import org.jetbrains.plugins.gradle.internal.task.GradleTaskId;
import org.jetbrains.plugins.gradle.internal.task.GradleTaskType;
import org.jetbrains.plugins.gradle.model.gradle.GradleTaskDescriptor;
import org.jetbrains.plugins.gradle.notification.GradleTaskNotificationListener;
import org.jetbrains.plugins.gradle.remote.GradleApiException;
import org.jetbrains.plugins.gradle.remote.GradleBuildManager;
import org.jetbrains.plugins.gradle.remote.RemoteGradleProcessSettings;

import java.rmi.RemoteException;
import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 3/14/13 5:09 PM
 */
public class GradleBuildManagerImpl implements GradleBuildManager {

  private final RemoteGradleServiceHelper myHelper = new RemoteGradleServiceHelper();

  @Override
  public Collection<GradleTaskDescriptor> listTasks(@NotNull final GradleTaskId id, @NotNull final String projectPath) {
    Function<ProjectConnection, Collection<GradleTaskDescriptor>> f = new Function<ProjectConnection, Collection<GradleTaskDescriptor>>() {
      @Nullable
      @Override
      public Collection<GradleTaskDescriptor> fun(ProjectConnection connection) {
        ModelBuilder<? extends IdeaProject> modelBuilder = myHelper.getModelBuilder(id, connection, false);
        IdeaProject project = modelBuilder.get();
        DomainObjectSet<? extends IdeaModule> modules = project.getModules();
        if (modules == null) {
          return Collections.emptyList();
        }
        Set<GradleTaskDescriptor> result = new HashSet<GradleTaskDescriptor>();
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
            result.add(new GradleTaskDescriptor(name, task.getDescription()));
          }
        }
        return result;
      }
    };
    return myHelper.execute(id, GradleTaskType.REFRESH_TASKS_LIST, projectPath, f);
  }

  @Override
  public void executeTasks(@NotNull final GradleTaskId id, @NotNull final List<String> taskNames, @NotNull String projectPath)
    throws RemoteException, GradleApiException
  {
    Function<ProjectConnection, Void>  f = new Function<ProjectConnection, Void>() {
      @Override
      public Void fun(ProjectConnection connection) {
        BuildLauncher launcher = myHelper.getBuildLauncher(id, connection);
        launcher.forTasks(ArrayUtil.toStringArray(taskNames));
        launcher.run();
        return null;
      }
    };
    myHelper.execute(id, GradleTaskType.EXECUTE_TASK, projectPath, f);
  }

  @Override
  public void setSettings(@NotNull RemoteGradleProcessSettings settings) throws RemoteException {
    myHelper.setSettings(settings); 
  }

  @Override
  public void setNotificationListener(@NotNull GradleTaskNotificationListener notificationListener) throws RemoteException {
    myHelper.setNotificationListener(notificationListener); 
  }

  @Override
  public boolean isTaskInProgress(@NotNull GradleTaskId id) throws RemoteException {
    return myHelper.isTaskInProgress(id);
  }

  @NotNull
  @Override
  public Map<GradleTaskType, Set<GradleTaskId>> getTasksInProgress() throws RemoteException {
    return myHelper.getTasksInProgress();
  }
}
