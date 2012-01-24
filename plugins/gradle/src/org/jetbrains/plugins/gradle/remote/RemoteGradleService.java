package org.jetbrains.plugins.gradle.remote;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.notification.GradleTaskNotificationListener;
import org.jetbrains.plugins.gradle.task.GradleTaskId;
import org.jetbrains.plugins.gradle.task.GradleTaskType;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Collection;

/**
 * Generic interface with common functionality for all remote services that work with gradle tooling api.
 * 
 * @author Denis Zhdanov
 * @since 8/9/11 3:19 PM
 */
public interface RemoteGradleService extends Remote {

  /**
   * Allows to check if current service executes the target task.
   *
   * @param id  target task's id
   * @return    <code>true</code> if a task with the given id is executed at the moment by the current service;
   *            <code>false</code> otherwise
   * @throws RemoteException      as required by RMI
   */
  boolean isTaskInProgress(@NotNull GradleTaskId id) throws RemoteException;

  /**
   * Allows to ask current service for the ids of the tasks with the given type being executed now.  
   *
   * @param type  target task type
   * @return      ids of the tasks of the target type being executed at the moment by the current service (if any)
   * @throws RemoteException      as required by RMI
   */
  @NotNull
  Collection<GradleTaskId> getTasksInProgress(@NotNull GradleTaskType type) throws RemoteException;
  
  /**
   * Provides the service settings to use.
   * 
   * @param settings  settings to use
   * @throws RemoteException      as required by RMI
   */
  void setSettings(@NotNull RemoteGradleProcessSettings settings) throws RemoteException;

  /**
   * Allows to define notification callback to use within the current service
   * 
   * @param notificationListener  notification listener to use with the current service
   * @throws RemoteException      as required by RMI
   */
  void setNotificationListener(@NotNull GradleTaskNotificationListener notificationListener) throws RemoteException;
}
