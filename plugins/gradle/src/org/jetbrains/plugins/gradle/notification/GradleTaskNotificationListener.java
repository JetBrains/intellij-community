package org.jetbrains.plugins.gradle.notification;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.task.GradleTaskId;

/**
 * Defines contract for callback to listen gradle task notifications. 
 * 
 * @author Denis Zhdanov
 * @since 11/10/11 11:57 AM
 */
public interface GradleTaskNotificationListener {

  /**
   * Notifies that task with the given id is about to be started.
   * 
   * @param id  target task's id
   */
  void onStart(@NotNull GradleTaskId id);

  /**
   * Notifies about processing state change of task with the given id.
   *
   * @param event  event that holds information about processing change state of the {@link GradleTaskNotificationEvent#getId() target task}
   */
  void onStatusChange(@NotNull GradleTaskNotificationEvent event);
  
  /**
   * Notifies that task with the given id is finished.
   *
   * @param id  target task's id
   */
  void onEnd(@NotNull GradleTaskId id);
}
