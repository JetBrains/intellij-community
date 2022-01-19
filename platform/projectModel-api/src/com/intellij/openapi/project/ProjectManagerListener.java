// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Listener for the {@link Project} lifecycle events.
 * @see ProjectManager#TOPIC
 */
public interface ProjectManagerListener extends EventListener {
  ProjectManagerListener[] EMPTY_ARRAY = new ProjectManagerListener[0];

  /**
   * Invoked on project open. Executed in EDT.
   *
   * @param project opening project
   */
  default void projectOpened(@NotNull Project project) {
  }

  /**
   * @deprecated Use {@link VetoableProjectManagerListener} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.3")
  default boolean canCloseProject(@NotNull Project project) {
    return true;
  }

  /**
   * Invoked on project close.
   *
   * @param project closing project
   */
  default void projectClosed(@NotNull Project project) {
  }

  /**
   * Invoked on project close before any closing activities
   */
  default void projectClosing(@NotNull Project project) {
  }

  default void projectClosingBeforeSave(@NotNull Project project) {
  }
}
