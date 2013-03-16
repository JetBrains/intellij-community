package org.jetbrains.plugins.gradle.internal.task;

/**
 * Enumerates interested types of tasks that may be enqueued to Gradle API.
 * 
 * @author Denis Zhdanov
 * @since 11/10/11 9:07 AM
 */
public enum GradleTaskType {
  
  RESOLVE_PROJECT, REFRESH_TASKS_LIST, EXECUTE_TASK
}
