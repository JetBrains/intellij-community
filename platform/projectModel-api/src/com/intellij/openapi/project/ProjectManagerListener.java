// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Listener for Project.
 */
public interface ProjectManagerListener extends EventListener {
  ProjectManagerListener[] EMPTY_ARRAY = new ProjectManagerListener[0];

  /**
   * Invoked on project open.
   *
   * @param project opening project
   */
  default void projectOpened(@NotNull Project project) {
  }

  /**
   * @deprecated Please use {@link VetoableProjectManagerListener}
   */
  @Deprecated
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
