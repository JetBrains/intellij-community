package org.jetbrains.plugins.gradle.remote;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.task.GradleTaskId;
import org.jetbrains.plugins.gradle.notification.GradleTaskNotificationEvent;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Defines interface for the entity that manages notifications about progress of long-running operations performed at Gradle API side.
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 11/10/11 9:03 AM
 */
public interface RemoteGradleProgressNotificationManager extends Remote {
  
  RemoteGradleProgressNotificationManager NULL_OBJECT = new RemoteGradleProgressNotificationManager() {
    @Override
    public void onStart(@NotNull GradleTaskId id) {
    }
    @Override
    public void onStatusChange(@NotNull GradleTaskNotificationEvent event) {
    }
    @Override
    public void onEnd(@NotNull GradleTaskId id) {
    }
  };

  void onStart(@NotNull GradleTaskId id) throws RemoteException;

  void onStatusChange(@NotNull GradleTaskNotificationEvent event) throws RemoteException;

  void onEnd(@NotNull GradleTaskId id) throws RemoteException;
}
