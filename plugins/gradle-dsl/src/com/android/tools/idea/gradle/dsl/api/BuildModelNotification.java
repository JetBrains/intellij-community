/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.api;

import org.jetbrains.annotations.NotNull;

/**
 * Interface to represent information, warnings and errors that are created by a GradleBuildModel.
 * These can be obtained from a GradleFileModel by {@link GradleFileModel#getNotifications()}.
 * The types of notification that can be provided are contained in {@link NotificationType}
 */
public interface BuildModelNotification {
  enum NotificationType {
    INCOMPLETE_PARSE, // Some elements in a build file related to this GradleFileModel couldn't be parsed.
    PROPERTY_PLACEMENT, // There was an issue with the placement of properties.
    INVALID_EXPRESSION, // When an attempt to create an expression failed.
  }

  boolean isCorrectionAvailable();

  void correct();

  @NotNull
  NotificationType getType();
}
