package org.jetbrains.plugins.gradle.remote.wrapper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.notification.GradleProgressNotificationManagerImpl;
import org.jetbrains.plugins.gradle.remote.GradleApiFacade;
import org.jetbrains.plugins.gradle.remote.GradleProjectResolver;
import org.jetbrains.plugins.gradle.remote.RemoteGradleProcessSettings;
import org.jetbrains.plugins.gradle.remote.RemoteGradleProgressNotificationManager;
import org.jetbrains.plugins.gradle.task.GradleTaskId;
import org.jetbrains.plugins.gradle.task.GradleTaskType;

import java.rmi.RemoteException;
import java.util.Map;
import java.util.Set;

/**
 * This class acts as a point where target remote gradle services are proxied.
 * <p/>
 * Check service wrapper contracts for more details.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/8/12 7:21 PM
 */
public class GradleApiFacadeWrapper implements GradleApiFacade {

  @NotNull private final GradleApiFacade                       myDelegate;
  @NotNull private final GradleProgressNotificationManagerImpl myNotificationManager;

  public GradleApiFacadeWrapper(@NotNull GradleApiFacade delegate, @NotNull GradleProgressNotificationManagerImpl notificationManager) {
    myDelegate = delegate;
    myNotificationManager = notificationManager;
  }

  @NotNull
  @Override
  public GradleProjectResolver getResolver() throws RemoteException, IllegalStateException {
    return new GradleProjectResolverWrapper(myDelegate.getResolver(), myNotificationManager);
  }

  @Override
  public void applySettings(@NotNull RemoteGradleProcessSettings settings) throws RemoteException {
    myDelegate.applySettings(settings); 
  }

  @Override
  public void applyProgressManager(@NotNull RemoteGradleProgressNotificationManager progressManager) throws RemoteException {
    myDelegate.applyProgressManager(progressManager); 
  }

  @Override
  public boolean isTaskInProgress(@NotNull GradleTaskId id) throws RemoteException {
    return myDelegate.isTaskInProgress(id);
  }

  @NotNull
  @Override
  public Map<GradleTaskType, Set<GradleTaskId>> getTasksInProgress() throws RemoteException {
    return myDelegate.getTasksInProgress();
  }
}
