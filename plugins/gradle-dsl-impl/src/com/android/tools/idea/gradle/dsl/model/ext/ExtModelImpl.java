/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.ext;

import com.android.tools.idea.gradle.dsl.api.ext.ExtModel;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR;

/**
 * Represents the extra user-defined properties defined in the Gradle file.
 * <p>
 * For more details please read
 * <a href="https://docs.gradle.org/current/userguide/writing_build_scripts.html#sec:extra_properties">Extra Properties</a>.
 * </p>
 */
public final class ExtModelImpl extends GradleDslBlockModel implements ExtModel {
  public ExtModelImpl(@NotNull ExtDslElement dslElement) {
    super(dslElement);
  }

  @Nullable
  public <T extends GradleDslElement> T getPropertyElement(@NotNull String property, @NotNull Class<T> clazz) {
    return myDslElement.getPropertyElement(property, clazz);
  }

  @Override
  @NotNull
  public GradlePropertyModel findProperty(@NotNull String name) {
    GradleDslElement element = myDslElement.getElement(name);
    if (element == null) {
      element = myDslElement.getVariableElement(name);
    }

    return element == null ? new GradlePropertyModelImpl(myDslElement, REGULAR, name) : new GradlePropertyModelImpl(element);
  }

  @Override
  @NotNull
  public List<GradlePropertyModel> getProperties() {
    return myDslElement.getPropertyElements().values().stream().map(element -> new GradlePropertyModelImpl(element))
      .collect(Collectors.toList());
  }

  @NotNull
  @Override
  public List<GradlePropertyModel> getVariables() {
    return myDslElement.getVariableElements().values().stream().map(element -> new GradlePropertyModelImpl(element))
      .collect(Collectors.toList());
  }
}
