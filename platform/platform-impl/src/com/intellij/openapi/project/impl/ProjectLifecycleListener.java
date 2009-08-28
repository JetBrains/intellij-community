/*
 * @author max
 */
package com.intellij.openapi.project.impl;

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

public interface ProjectLifecycleListener {
  Topic<ProjectLifecycleListener> TOPIC = Topic.create("Various stages of project lifecycle notifications", ProjectLifecycleListener.class);

  void projectComponentsInitialized(Project project);

  void beforeProjectLoaded(@NotNull Project project);
  void afterProjectClosed(@NotNull Project project);

  abstract class Adapter implements ProjectLifecycleListener {
    public void projectComponentsInitialized(final Project project) {}

    public void beforeProjectLoaded(@NotNull final Project project) {

    }

    public void afterProjectClosed(@NotNull Project project) {

    }
  }
}