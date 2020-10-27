/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType;
import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ExtModel extends GradleDslModel {
  /**
   * Returns a {@link GradlePropertyModel} representing the property with a given {@code name} on the
   * Gradle ExtraPropertiesExtension. The returned {@link GradlePropertyModel} will have a {@link ValueType} of NONE if the
   * field was not present.
   */
  @NotNull
  GradlePropertyModel findProperty(@NotNull String name);

  /**
   * Returns all of the existing properties defined in this block. This does not include variables, every {@link GradlePropertyModel}
   * returned by this method will have a property type of {@link PropertyType#REGULAR}
   */
  @NotNull
  List<GradlePropertyModel> getProperties();

  /**
   * Return all of the existing variables defined within Gradle's ExtraPropertiesExtension. Note that these can only be referenced from
   * properties in this modules {@link ExtModel}.
   */
  @NotNull
  List<GradlePropertyModel> getVariables();
}
