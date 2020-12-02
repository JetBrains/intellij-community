/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KtsOnlyPropertyTransform extends PropertyTransform {
  PropertyTransform myTransform;

  public KtsOnlyPropertyTransform(PropertyTransform transform) {
    super();
    myTransform = transform;
  }

  @Override
  public boolean test(@Nullable GradleDslElement e, @NotNull GradleDslElement holder) {
    if (holder.getDslFile().isKotlin()) {
      return myTransform.test(e, holder);
    }
    return false;
  }

  @Nullable
  @Override
  public GradleDslElement transform(@Nullable GradleDslElement e) {
    return myTransform.transform(e);
  }

  @NotNull
  @Override
  public GradleDslExpression bind(@NotNull GradleDslElement holder,
                                  @Nullable GradleDslElement oldElement,
                                  @NotNull Object value,
                                  @NotNull String name) {
    return myTransform.bind(holder, oldElement, value, name);
  }

  @NotNull
  @Override
  public GradleDslExpression bind(@NotNull GradleDslElement holder,
                                  @Nullable GradleDslElement oldElement,
                                  @NotNull Object value,
                                  @NotNull ModelPropertyDescription propertyDescription) {
    return myTransform.bind(holder, oldElement, value, propertyDescription);
  }

  @NotNull
  @Override
  public GradleDslExpression bindList(@NotNull GradleDslElement holder,
                                      @Nullable GradleDslElement oldElement,
                                      @NotNull String name,
                                      boolean isMethodCall) {
    return myTransform.bindList(holder, oldElement, name, isMethodCall);
  }

  @Override
  public GradleDslExpression bindList(@NotNull GradleDslElement holder,
                                      @Nullable GradleDslElement oldElement,
                                      @NotNull ModelPropertyDescription propertyDescription,
                                      boolean isMethodCall) {
    return myTransform.bindList(holder, oldElement, propertyDescription, isMethodCall);
  }

  @NotNull
  @Override
  public GradleDslExpression bindMap(@NotNull GradleDslElement holder,
                                     @Nullable GradleDslElement oldElement,
                                     @NotNull String name,
                                     boolean isMethodCall) {
    return myTransform.bindMap(holder, oldElement, name, isMethodCall);
  }

  @NotNull
  @Override
  public GradleDslExpression bindMap(@NotNull GradleDslElement holder,
                                     @Nullable GradleDslElement oldElement,
                                     @NotNull ModelPropertyDescription propertyDescription,
                                     boolean isMethodCall) {
    return super.bindMap(holder, oldElement, propertyDescription, isMethodCall);
  }

  @NotNull
  @Override
  public GradleDslElement replace(@NotNull GradleDslElement holder,
                                  @Nullable GradleDslElement oldElement,
                                  @NotNull GradleDslExpression newElement,
                                  @NotNull String name) {
    return myTransform.replace(holder, oldElement, newElement, name);
  }

  @Nullable
  @Override
  public GradleDslElement delete(@NotNull GradleDslElement holder,
                                 @NotNull GradleDslElement oldElement,
                                 @NotNull GradleDslElement transformedElement) {
    return myTransform.delete(holder, oldElement, transformedElement);
  }
}
