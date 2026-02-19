// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Listener for the {@link Project} lifecycle events.
 * @see ProjectManager#TOPIC
 */
public interface ProjectManagerListener extends EventListener {
  ProjectManagerListener[] EMPTY_ARRAY = new ProjectManagerListener[0];

  /**
   * @deprecated Do not use.
   * Consider using <a href="https://plugins.jetbrains.com/docs/intellij/plugin-components.html#project-open">Post start-up activity</a>.
   */
  @Deprecated(forRemoval = true)
  default void projectOpened(@NotNull Project project) {
  }

  /**
   * @deprecated Use {@link VetoableProjectManagerListener} instead.
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated(forRemoval = true)
  default boolean canCloseProject(@SuppressWarnings("unused") @NotNull Project project) {
    return true;
  }

  /**
   * Invoked on project close.
   * <p>
   * Consider using {@link ProjectCloseListener}.
   */
  default void projectClosed(@NotNull Project project) {
  }

  /**
   * Invoked on project close before any closing activities.
   * <p>
   * Consider using {@link ProjectCloseListener}.
   */
  default void projectClosing(@NotNull Project project) {
  }

  default void projectClosingBeforeSave(@NotNull Project project) {
  }
}
