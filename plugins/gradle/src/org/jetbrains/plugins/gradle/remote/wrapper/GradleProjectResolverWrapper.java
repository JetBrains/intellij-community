package org.jetbrains.plugins.gradle.remote.wrapper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.gradle.GradleProject;
import org.jetbrains.plugins.gradle.notification.GradleProgressNotificationManagerImpl;
import org.jetbrains.plugins.gradle.notification.GradleTaskNotificationListener;
import org.jetbrains.plugins.gradle.remote.GradleApiException;
import org.jetbrains.plugins.gradle.remote.GradleProjectResolver;
import org.jetbrains.plugins.gradle.remote.RemoteGradleProcessSettings;
import org.jetbrains.plugins.gradle.task.GradleTaskId;
import org.jetbrains.plugins.gradle.task.GradleTaskManager;
import org.jetbrains.plugins.gradle.task.GradleTaskType;

import java.rmi.RemoteException;
import java.util.Map;
import java.util.Set;

/**
 * Intercepts calls to the target {@link GradleProjectResolver} and
 * {@link GradleTaskManager#onQueued(GradleTaskId) updates 'queued' task status}.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/8/12 7:21 PM
 */
public class GradleProjectResolverWrapper implements GradleProjectResolver {

  @NotNull private final GradleProjectResolver                 myResolver;
  @NotNull private final GradleProgressNotificationManagerImpl myNotificationManager;

  public GradleProjectResolverWrapper(@NotNull GradleProjectResolver resolver,
                                      @NotNull GradleProgressNotificationManagerImpl notificationManager)
  {
    myResolver = resolver;
    myNotificationManager = notificationManager;
  }

  @Override
  @NotNull
  public GradleProject resolveProjectInfo(@NotNull GradleTaskId id, @NotNull String projectPath, boolean downloadLibraries)
    throws RemoteException, GradleApiException, IllegalArgumentException, IllegalStateException
  {
    myNotificationManager.onQueued(id);
    return myResolver.resolveProjectInfo(id, projectPath, downloadLibraries);
  }

  @Override
  public void setSettings(@NotNull RemoteGradleProcessSettings settings) throws RemoteException {
    myResolver.setSettings(settings);
  }

  @Override
  public void setNotificationListener(@NotNull GradleTaskNotificationListener notificationListener) throws RemoteException {
    myResolver.setNotificationListener(notificationListener);
  }

  @Override
  public boolean isTaskInProgress(@NotNull GradleTaskId id) throws RemoteException {
    return myResolver.isTaskInProgress(id);
  }

  @Override
  @NotNull
  public Map<GradleTaskType, Set<GradleTaskId>> getTasksInProgress() throws RemoteException {
    return myResolver.getTasksInProgress();
  }
}
