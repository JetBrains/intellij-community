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
package com.android.tools.idea.gradle.dsl.api.ext;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a property that can be set to multiple types. See subclasses for what types can be used and what
 * they mean for the resulting DSL text.
 *
 * @param <T> enum type that this property can be set to.
 */
public interface MultiTypePropertyModel<T extends Enum<T>> extends GradlePropertyModel {
  /**
   * @return the current type of this property
   */
  @NotNull
  T getType();

  /**
   * Sets this property to the given type.
   *
   * @param type the type to set
   */
  void setType(@NotNull T type);

  /**
   * Sets both the type and a new value.
   *
   * @param type the type to set
   * @param value the value to set
   */
  void setValue(@NotNull T type, @NotNull Object value);
}
