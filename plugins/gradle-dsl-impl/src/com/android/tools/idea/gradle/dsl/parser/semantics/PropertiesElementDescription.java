/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.semantics;

import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElementConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PropertiesElementDescription<T extends GradlePropertiesDslElement> {
  @Nullable public final String name;
  @NotNull public final Class<T> clazz;
  @NotNull public final GradlePropertiesDslElementConstructor<T> constructor;

  public PropertiesElementDescription(
    @Nullable String name,
    @NotNull Class<T> clazz,
    @NotNull GradlePropertiesDslElementConstructor<T> constructor
  ) {
    this.name = name;
    this.clazz = clazz;
    this.constructor = constructor;
  }
}
