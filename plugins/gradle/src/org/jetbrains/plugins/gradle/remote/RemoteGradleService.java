package org.jetbrains.plugins.gradle.remote;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.notification.GradleTaskNotificationListener;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Generic interface with common functionality for all remote services that work with gradle tooling api.
 * 
 * @author Denis Zhdanov
 * @since 8/9/11 3:19 PM
 */
public interface RemoteGradleService extends Remote {

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
