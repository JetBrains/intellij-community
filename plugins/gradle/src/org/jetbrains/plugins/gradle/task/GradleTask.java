package org.jetbrains.plugins.gradle.task;

import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.notification.GradleTaskNotificationListener;

/**
 * @author Denis Zhdanov
 * @since 1/24/12 7:16 AM
 */
public interface GradleTask {

  @NotNull
  GradleTaskId getId();

  @NotNull
  GradleTaskState getState();

  /**
   * @return    error occurred during the task execution (if any)
   */
  @Nullable
  Throwable getError();

  /**
   * Executes current task and updates given indicator's {@link ProgressIndicator#setText2(String) status} during that.
   * 
   * @param indicator  target progress indicator
   */
  void execute(@NotNull ProgressIndicator indicator);
  
  /**
   * Executes current task at the calling thread, i.e. the call to this method blocks.
   * 
   * @param listeners  callbacks to be notified about the task execution update
   */
  void execute(@NotNull GradleTaskNotificationListener ... listeners);

  /**
   * Forces current task to refresh {@link #getState() its state}.
   */
  void refreshState();
}
