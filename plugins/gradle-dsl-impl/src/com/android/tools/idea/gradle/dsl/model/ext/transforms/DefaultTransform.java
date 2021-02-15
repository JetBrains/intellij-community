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
package com.android.tools.idea.gradle.dsl.model.ext.transforms;

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.createOrReplaceBasicExpression;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.replaceElement;

/**
 * The default transform used by {@link GradlePropertyModel}.
 */
public class DefaultTransform extends PropertyTransform {
  public DefaultTransform() { }

  @Override
  public boolean test(@Nullable GradleDslElement e, @NotNull GradleDslElement holder) {
    return true;
  }

  @Nullable
  @Override
  public GradleDslElement transform(@Nullable GradleDslElement e) {
    return e;
  }

  @NotNull
  @Override
  public GradleDslExpression bind(@NotNull GradleDslElement holder,
                                  @Nullable GradleDslElement oldElement,
                                  @NotNull Object value,
                                  @NotNull String name) {
    return createOrReplaceBasicExpression(holder, oldElement, value, GradleNameElement.fake(name), null);
  }

  @NotNull
  @Override
  public GradleDslExpression bind(@NotNull GradleDslElement holder,
                                  @Nullable GradleDslElement oldElement,
                                  @NotNull Object value,
                                  @NotNull ModelPropertyDescription propertyDescription) {
    return createOrReplaceBasicExpression(holder, oldElement, value, GradleNameElement.fake(propertyDescription.name), propertyDescription);
  }

  @Override
  @NotNull
  public GradleDslElement replace(@NotNull GradleDslElement holder,
                                  @Nullable GradleDslElement oldElement,
                                  @NotNull GradleDslExpression newElement,
                                  @NotNull String name) {
    replaceElement(holder, oldElement, newElement);
    return newElement;
  }
}
