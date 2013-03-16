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

import com.intellij.util.Function;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.ConcurrentHashSet;
import com.intellij.util.containers.HashMap;
import org.gradle.tooling.*;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.model.idea.BasicIdeaProject;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.internal.task.GradleTaskId;
import org.jetbrains.plugins.gradle.internal.task.GradleTaskType;
import org.jetbrains.plugins.gradle.notification.GradleTaskNotificationEvent;
import org.jetbrains.plugins.gradle.notification.GradleTaskNotificationListener;
import org.jetbrains.plugins.gradle.remote.GradleApiException;
import org.jetbrains.plugins.gradle.remote.RemoteGradleProcessSettings;
import org.jetbrains.plugins.gradle.remote.RemoteGradleService;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Denis Zhdanov
 * @since 3/14/13 5:11 PM
 */
public class RemoteGradleServiceHelper implements RemoteGradleService {

  private final AtomicReference<RemoteGradleProcessSettings>     mySettings        = new AtomicReference<RemoteGradleProcessSettings>();
  private final ConcurrentMap<GradleTaskType, Set<GradleTaskId>> myTasksInProgress =
    new ConcurrentHashMap<GradleTaskType, Set<GradleTaskId>>();

  private final AtomicReference<GradleTaskNotificationListener> myNotificationListener
    = new AtomicReference<GradleTaskNotificationListener>();

  @Override
  public void setSettings(@NotNull RemoteGradleProcessSettings settings) throws RemoteException {
    mySettings.set(settings);
  }

  @Override
  public void setNotificationListener(@NotNull GradleTaskNotificationListener notificationListener) throws RemoteException {
    myNotificationListener.set(notificationListener);
  }

  @Override
  public boolean isTaskInProgress(@NotNull GradleTaskId id) throws RemoteException {
    for (Set<GradleTaskId> ids : myTasksInProgress.values()) {
      if (ids.contains(id)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  @Override
  public Map<GradleTaskType, Set<GradleTaskId>> getTasksInProgress() throws RemoteException {
    return new HashMap<GradleTaskType, Set<GradleTaskId>>(myTasksInProgress);
  }

  @NotNull
  public ModelBuilder<? extends IdeaProject> getModelBuilder(@NotNull final GradleTaskId id,
                                                             @NotNull ProjectConnection connection,
                                                             boolean downloadLibraries)
  {
    ModelBuilder<? extends IdeaProject> result = connection.model(downloadLibraries ? IdeaProject.class : BasicIdeaProject.class);
    prepare(result, id);
    return result;
  }

  @NotNull
  public BuildLauncher getBuildLauncher(@NotNull final GradleTaskId id, @NotNull ProjectConnection connection) {
    BuildLauncher result = connection.newBuild();
    prepare(result, id);
    return result;
  }

  private void prepare(LongRunningOperation operation, @NotNull final GradleTaskId id) {
    final GradleTaskNotificationListener progressManager = myNotificationListener.get();
    progressManager.onStart(id);
    final RemoteGradleProcessSettings settings = mySettings.get();
    if (settings != null) {
      final String javaHome = settings.getJavaHome();
      if (javaHome != null && new File(javaHome).isDirectory()) {
        operation.setJavaHome(new File(javaHome));
      }
    }
    operation.addProgressListener(new ProgressListener() {
      @Override
      public void statusChanged(ProgressEvent event) {
        progressManager.onStatusChange(new GradleTaskNotificationEvent(id, event.getDescription()));
      }
    });
  }

  public <T> T execute(@NotNull GradleTaskId id,
                       @NotNull final GradleTaskType type,
                       @NotNull String projectPath,
                       @NotNull Function<ProjectConnection, T> f)
  {
    Set<GradleTaskId> ids = myTasksInProgress.get(type);
    if (ids == null) {
      myTasksInProgress.putIfAbsent(type, new ConcurrentHashSet<GradleTaskId>());
      ids = myTasksInProgress.get(type);
    }
    ids.add(id);
    ProjectConnection connection = getConnection(projectPath);
    try {
      return f.fun(connection);
    }
    catch (Throwable e) {
      throw new GradleApiException(e);
    }
    finally {
      try {
        connection.close();
      }
      catch (Throwable e) {
        // ignore
      }
    }
  }
  
  /**
   * Allows to retrieve gradle api connection to use for the given project.
   *
   * @param projectPath     target project path
   * @return                connection to use
   * @throws IllegalStateException    if it's not possible to create the connection
   */
  @NotNull
  private ProjectConnection getConnection(@NotNull String projectPath) throws IllegalStateException {
    File projectFile = new File(projectPath);
    if (!projectFile.isFile()) {
      throw new IllegalArgumentException(GradleBundle.message("gradle.import.text.error.invalid.path", projectPath));
    }
    File projectDir = projectFile.getParentFile();
    GradleConnector connector = GradleConnector.newConnector();
    RemoteGradleProcessSettings settings = mySettings.get();
    if (settings != null) {

      // Setup wrapper/local installation usage.
      if (!settings.isUseWrapper()) {
        String gradleHome = settings.getGradleHome();
        if (gradleHome != null) {
          try {
            // There were problems with symbolic links processing at the gradle side.
            connector.useInstallation(new File(gradleHome).getCanonicalFile());
          }
          catch (IOException e) {
            connector.useInstallation(new File(settings.getGradleHome()));
          }
        }
      }

      // Setup service directory if necessary.
      String serviceDirectory = settings.getServiceDirectory();
      if (serviceDirectory != null) {
        connector.useGradleUserHomeDir(new File(serviceDirectory));
      }

      // Setup logging if necessary.
      if (settings.isVerboseApi() && connector instanceof DefaultGradleConnector) {
        ((DefaultGradleConnector)connector).setVerboseLogging(true);
      }

      // Setup daemon ttl if necessary.
      long ttl = settings.getTtlInMs();
      if (ttl > 0 && connector instanceof DefaultGradleConnector) {
        ((DefaultGradleConnector)connector).daemonMaxIdleTime((int)ttl, TimeUnit.MILLISECONDS);
      }
    }
    connector.forProjectDirectory(projectDir);
    ProjectConnection connection = connector.connect();
    if (connection == null) {
      throw new IllegalStateException(String.format(
        "Can't create connection to the target project via gradle tooling api. Project path: '%s'", projectPath
      ));
    }
    return connection;
  }
}
