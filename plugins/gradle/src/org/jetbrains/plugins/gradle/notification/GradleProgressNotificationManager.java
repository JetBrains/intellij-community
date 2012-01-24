package org.jetbrains.plugins.gradle.notification;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.task.GradleTaskId;

/**
 * @author Denis Zhdanov
 * @since 11/10/11 12:04 PM
 */
public interface GradleProgressNotificationManager {

  /**
   * Allows to register given listener within the current manager for listening events from the task with the target id. 
   * 
   * @param taskId    target task's id
   * @param listener  listener to register
   * @return          <code>true</code> if given listener was not registered before for the given key;
   *                  <code>false</code> otherwise
   */
  boolean addNotificationListener(@NotNull GradleTaskId taskId, @NotNull GradleTaskNotificationListener listener);

  /**
   * Allows to de-register given listener from the current manager
   *
   * @param listener  listener to de-register
   * @return          <code>true</code> if given listener was successfully de-registered;
   *                  <code>false</code> if given listener was not registered before
   */
  boolean removeNotificationListener(@NotNull GradleTaskNotificationListener listener);
}
