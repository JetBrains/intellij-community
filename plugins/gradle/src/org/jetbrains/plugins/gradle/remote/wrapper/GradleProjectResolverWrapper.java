package org.jetbrains.plugins.gradle.remote.wrapper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.gradle.GradleProject;
import org.jetbrains.plugins.gradle.notification.GradleProgressNotificationManagerImpl;
import org.jetbrains.plugins.gradle.remote.GradleApiException;
import org.jetbrains.plugins.gradle.remote.GradleProjectResolver;
import org.jetbrains.plugins.gradle.internal.task.GradleTaskId;
import org.jetbrains.plugins.gradle.internal.task.GradleTaskManager;

import java.rmi.RemoteException;

/**
 * Intercepts calls to the target {@link GradleProjectResolver} and
 * {@link GradleTaskManager#onQueued(GradleTaskId) updates 'queued' task status}.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/8/12 7:21 PM
 */
public class GradleProjectResolverWrapper extends AbstractRemoteGradleServiceWrapper<GradleProjectResolver>
  implements GradleProjectResolver
{

  @NotNull private final GradleProgressNotificationManagerImpl myNotificationManager;

  public GradleProjectResolverWrapper(@NotNull GradleProjectResolver delegate,
                                      @NotNull GradleProgressNotificationManagerImpl notificationManager)
  {
    super(delegate);
    myNotificationManager = notificationManager;
  }

  @Override
  @Nullable
  public GradleProject resolveProjectInfo(@NotNull GradleTaskId id, @NotNull String projectPath, boolean downloadLibraries)
    throws RemoteException, GradleApiException, IllegalArgumentException, IllegalStateException
  {
    myNotificationManager.onQueued(id);
    try {
      return getDelegate().resolveProjectInfo(id, projectPath, downloadLibraries);
    }
    finally {
      myNotificationManager.onEnd(id);
    }
  }
}
