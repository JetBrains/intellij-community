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
package org.jetbrains.plugins.gradle.remote.wrapper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.gradle.GradleTaskDescriptor;
import org.jetbrains.plugins.gradle.notification.GradleProgressNotificationManagerImpl;
import org.jetbrains.plugins.gradle.remote.GradleApiException;
import org.jetbrains.plugins.gradle.remote.GradleBuildManager;
import org.jetbrains.plugins.gradle.internal.task.GradleTaskId;

import java.rmi.RemoteException;
import java.util.Collection;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 3/14/13 5:53 PM
 */
public class GradleBuildManagerWrapper extends AbstractRemoteGradleServiceWrapper<GradleBuildManager> implements GradleBuildManager {

  @NotNull private final GradleProgressNotificationManagerImpl myNotificationManager;

  public GradleBuildManagerWrapper(@NotNull GradleBuildManager delegate, @NotNull GradleProgressNotificationManagerImpl manager) {
    super(delegate);
    myNotificationManager = manager;
  }

  @Override
  public Collection<GradleTaskDescriptor> listTasks(@NotNull GradleTaskId id, @NotNull String projectPath)
    throws RemoteException, GradleApiException
  {
    myNotificationManager.onQueued(id);
    try {
      return getDelegate().listTasks(id, projectPath);
    }
    finally {
      myNotificationManager.onEnd(id);
    }
  }

  @Override
  public void executeTasks(@NotNull GradleTaskId id, @NotNull List<String> taskNames, @NotNull String projectPath)
    throws RemoteException, GradleApiException
  {
    myNotificationManager.onQueued(id);
    try {
      getDelegate().executeTasks(id, taskNames, projectPath);
    }
    finally {
      myNotificationManager.onEnd(id);
    } 
  }
}
