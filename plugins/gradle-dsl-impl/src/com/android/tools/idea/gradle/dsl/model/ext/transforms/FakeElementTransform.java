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

import com.android.tools.idea.gradle.dsl.parser.elements.FakeElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.replaceElement;

/**
 * Transform for fake elements, the main purpose of this transform is to ensure that
 * {@link #bind(GradleDslElement, GradleDslElement, Object, String)} always calls {@link FakeElement#setValue(Object)}
 * instead of triggering a createOrReplace. This makes it so we don't try to perform any operations such as removing or adding it
 * to the tree.
 */
public final class FakeElementTransform extends PropertyTransform {
  public FakeElementTransform() { }

  @Override
  public boolean test(@Nullable GradleDslElement e, @NotNull GradleDslElement holder) {
    return e instanceof FakeElement;
  }

  @Override
  @NotNull
  public GradleDslElement transform(@Nullable GradleDslElement e) {
    return e;
  }

  @Override
  @NotNull
  public GradleDslExpression bind(@NotNull GradleDslElement holder,
                                  @Nullable GradleDslElement oldElement,
                                  @NotNull Object value,
                                  @NotNull String name) {
    if (oldElement instanceof FakeElement) {
      ((FakeElement)oldElement).setValue(value);
      return (FakeElement)oldElement;
    }
    throw new IllegalStateException("Must be a fake element");
  }

  @Override
  @NotNull
  public GradleDslExpression replace(@NotNull GradleDslElement holder,
                                     @Nullable GradleDslElement oldElement,
                                     @NotNull GradleDslExpression newElement,
                                     @NotNull String name) {
    replaceElement(holder, oldElement, newElement);
    return newElement;
  }

  @Override
  @Nullable
  public GradleDslElement delete(@NotNull GradleDslElement holder, @NotNull GradleDslElement oldElement,
                                 @NotNull GradleDslElement transformedElement) {
    // This element must be a fake element, we don't need to remove of from it's holder.
    oldElement.delete();
    return null;
  }
}
