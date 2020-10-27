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

import com.android.tools.idea.gradle.dsl.api.util.TypeReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a {@link GradlePropertyModel} which will always automatically follow any references when calling
 * {@link #getValue(TypeReference)} or {@link #getValueType()}. If we had the following build file:
 *
 * ext {
 *   appId = "com.my.application"
 * }
 *
 * android {\n" +
 *  defaultConfig {\n" +
 *    applicationId appId
 *  }
 * }
 *
 * While using a {@link ResolvedPropertyModel} for the applicationId property, {@link #getValueType()} will return STRING and
 * {@link #getValue(TypeReference)} called with STRING_TYPE will return "com.my.application". This is in contrast to the standard
 * {@link GradlePropertyModel} which can be obtained using {@link #getUnresolvedModel()} where {@link #getValueType()}
 * and {@link #getValue(TypeReference)} will return REFERENCE and "appId" respectively.
 *
 */
public interface ResolvedPropertyModel extends GradlePropertyModel {
  /**
   * Gets the {@link ValueType} of the value assigned to this property. The {@link ValueType} returned by this method can not be
   * {@link ValueType.REFERENCE} any {@link ValueType.REFERENCE} encountered during resolution will be followed.
   * For details about possible return values see {@link ValueType}
   */
  @Override
  @NotNull
  ValueType getValueType();

  /**
   * Returns the value of this resolved property, the {@link TypeReference} should be passed in based on the type returned from
   * {@link #getValueType()}
   */
  @Override
  @Nullable
  <T> T getValue(@NotNull TypeReference<T> typeReference);

  /**
   * Returns the {@link GradlePropertyModel} representing this property.
   */
  @Override
  @NotNull
  GradlePropertyModel getUnresolvedModel();

  /**
   * Returns a model representing the property that was the result of resolving the reference chain starting at this property.
   * For example with the following Gradle file:
   *
   * ext {
   *   colour = "red"
   *   carColour = color
   * }
   *
   * Using the ext model to get the carColor property, with {@link ExtModel#findProperty(String)}.
   * Then calling {@link GradlePropertyModel#resolve()} to obtain the resolved model. This model represents the carColour property
   * on the file. This means that any edits to this model will not edit the value of the colour property. This method enables the
   * caller to obtain the colour model from the carColour model.
   */
  @NotNull
  GradlePropertyModel getResultModel();
}
