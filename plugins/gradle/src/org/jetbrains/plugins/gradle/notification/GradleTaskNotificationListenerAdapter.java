package org.jetbrains.plugins.gradle.notification;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.internal.task.GradleTaskId;

/**
 * @author Denis Zhdanov
 * @since 11/10/11 12:18 PM
 */
public abstract class GradleTaskNotificationListenerAdapter implements GradleTaskNotificationListener {

  @Override
  public void onQueued(@NotNull GradleTaskId id) {
  }

  @Override
  public void onStart(@NotNull GradleTaskId id) {
  }

  @Override
  public void onStatusChange(@NotNull GradleTaskNotificationEvent event) {
  }

  @Override
  public void onEnd(@NotNull GradleTaskId id) {
  }
}
