/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  default void projectOpened(Project project) {
  }

  /**
   * @deprecated Please use {@link VetoableProjectManagerListener}
   */
  @Deprecated
  default boolean canCloseProject(Project project) {
    return true;
  }

  /**
   * Invoked on project close.
   *
   * @param project closing project
   */
  default void projectClosed(Project project) {
  }

  /**
   * Invoked on project close before any closing activities
   */
  default void projectClosing(Project project) {
  }

  default void projectClosingBeforeSave(@NotNull Project project) {
  }
}
