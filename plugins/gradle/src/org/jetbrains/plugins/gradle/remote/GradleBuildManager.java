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
package org.jetbrains.plugins.gradle.remote;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.gradle.GradleTaskDescriptor;
import org.jetbrains.plugins.gradle.notification.GradleTaskNotificationListener;
import org.jetbrains.plugins.gradle.internal.task.GradleTaskId;
import org.jetbrains.plugins.gradle.internal.task.GradleTaskType;

import java.rmi.RemoteException;
import java.util.*;

/**
 * Abstraction layer for executing gradle tasks.
 * 
 * @author Denis Zhdanov
 * @since 3/14/13 5:04 PM
 */
public interface GradleBuildManager extends RemoteGradleService {

  GradleBuildManager NULL_OBJECT = new GradleBuildManager() {
    @Override
    public Collection<GradleTaskDescriptor> listTasks(@NotNull GradleTaskId id, @NotNull String projectPath) {
      return Collections.emptySet();
    }

    @Override
    public void executeTasks(@NotNull GradleTaskId id, @NotNull List<String> taskNames, @NotNull String projectPath)
      throws RemoteException
    {
    }

    @Override
    public void setSettings(@NotNull RemoteGradleProcessSettings settings) throws RemoteException {
    }

    @Override
    public void setNotificationListener(@NotNull GradleTaskNotificationListener notificationListener) throws RemoteException {
    }

    @Override
    public boolean isTaskInProgress(@NotNull GradleTaskId id) throws RemoteException {
      return false;
    }

    @NotNull
    @Override
    public Map<GradleTaskType, Set<GradleTaskId>> getTasksInProgress() throws RemoteException {
      return Collections.emptyMap();
    }
  };

  Collection<GradleTaskDescriptor> listTasks(@NotNull GradleTaskId id, @NotNull String projectPath)
    throws RemoteException, GradleApiException;

  void executeTasks(@NotNull GradleTaskId id, @NotNull List<String> taskNames, @NotNull String projectPath)
    throws RemoteException, GradleApiException;
}
