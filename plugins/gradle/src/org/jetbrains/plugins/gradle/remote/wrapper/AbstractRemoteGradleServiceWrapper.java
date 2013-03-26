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
import org.jetbrains.plugins.gradle.notification.GradleTaskNotificationListener;
import org.jetbrains.plugins.gradle.remote.RemoteGradleProcessSettings;
import org.jetbrains.plugins.gradle.remote.RemoteGradleService;
import org.jetbrains.plugins.gradle.internal.task.GradleTaskId;
import org.jetbrains.plugins.gradle.internal.task.GradleTaskType;

import java.rmi.RemoteException;
import java.util.Map;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 3/14/13 5:55 PM
 */
public abstract class AbstractRemoteGradleServiceWrapper<T extends RemoteGradleService> implements RemoteGradleService {

  @NotNull private final T myDelegate;

  public AbstractRemoteGradleServiceWrapper(@NotNull T delegate) {
    myDelegate = delegate;
  }

  @Override
  public void setSettings(@NotNull RemoteGradleProcessSettings settings) throws RemoteException {
    myDelegate.setSettings(settings);
  }

  @Override
  public void setNotificationListener(@NotNull GradleTaskNotificationListener notificationListener) throws RemoteException {
    myDelegate.setNotificationListener(notificationListener);
  }

  @Override
  public boolean isTaskInProgress(@NotNull GradleTaskId id) throws RemoteException {
    return myDelegate.isTaskInProgress(id);
  }

  @Override
  @NotNull
  public Map<GradleTaskType, Set<GradleTaskId>> getTasksInProgress() throws RemoteException {
    return myDelegate.getTasksInProgress();
  }

  @NotNull
  public T getDelegate() {
    return myDelegate;
  }
}
