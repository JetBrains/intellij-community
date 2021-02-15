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
package com.android.tools.idea.gradle.dsl.model.notifications;

import com.android.tools.idea.gradle.dsl.api.BuildModelNotification;
import com.android.tools.idea.gradle.dsl.api.BuildModelNotification.NotificationType;
import com.intellij.util.Producer;
import org.jetbrains.annotations.NotNull;

/**
 * A type reference for subclasses of BuildModelNotification.
 * In order to add a new type of BuildModelNotification the following must be done
 *   1) Create a public type in {@link NotificationType}
 *   2) Create a new static variable on this class which contains the {@link Class} of the new type and a method to create it.
 *
 * @param <T> the class representing the notification.
 */
public class NotificationTypeReference<T extends BuildModelNotification> {
  public static final NotificationTypeReference<IncompleteParsingNotification> INCOMPLETE_PARSING =
    new NotificationTypeReference<>(IncompleteParsingNotification.class, IncompleteParsingNotification::new);
  public static final NotificationTypeReference<PropertyPlacementNotification> PROPERTY_PLACEMENT =
    new NotificationTypeReference<>(PropertyPlacementNotification.class, PropertyPlacementNotification::new);
  public static final NotificationTypeReference<InvalidExpressionNotification> INVALID_EXPRESSION =
    new NotificationTypeReference<>(InvalidExpressionNotification.class, InvalidExpressionNotification::new);
  public static final NotificationTypeReference<CircularApplication> CIRCULAR_APPLICATION =
    new NotificationTypeReference<>(CircularApplication.class, CircularApplication::new);

  @NotNull private final Class<T> myClazz;
  @NotNull private final Producer<T> myConstructor;

  NotificationTypeReference(@NotNull Class<T> clazz, @NotNull Producer<T> constructor) {
    myClazz = clazz;
    myConstructor = constructor;
  }

  @NotNull
  public Class<T> getClazz() {
    return myClazz;
  }

  @NotNull
  public Producer<T> getConstructor() {
    return myConstructor;
  }
}
