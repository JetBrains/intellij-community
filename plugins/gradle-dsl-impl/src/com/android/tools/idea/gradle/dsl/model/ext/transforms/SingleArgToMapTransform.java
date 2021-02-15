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

import com.android.tools.idea.gradle.dsl.parser.elements.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.createBasicExpression;

/**
 * This transform is used to convert from the single argument form, to one with uses a map.
 * For example:
 * compile fileTree('libs') -> compile fileTree(dir: 'libs', include: ['*.jar'], exclude: ['*.aar'])
 * This transformation should occur whenever include or exclude is set. To achieve this, this transform should be added to the models that
 * represent "include" and "exclude".
 */
public class SingleArgToMapTransform extends PropertyTransform {
  @NotNull
  private final String mySingleArgName;
  @NotNull
  private final String myFieldName;

  public SingleArgToMapTransform(@NotNull String singleArgName, @NotNull String fieldName) {
    myFieldName = fieldName;
    mySingleArgName = singleArgName;
  }

  @Override
  public boolean test(@Nullable GradleDslElement e, @NotNull GradleDslElement holder) {
    if (e instanceof GradleDslMethodCall) {
      GradleDslMethodCall methodCall = (GradleDslMethodCall)e;
      if (!methodCall.getArguments().isEmpty() && methodCall.getArguments().get(0) instanceof GradleDslSimpleExpression) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  @Override
  public GradleDslElement transform(@Nullable GradleDslElement e) {
    return null; // If this transform is active then we don't have a value for this property yet.
  }

  @NotNull
  @Override
  public GradleDslSimpleExpression bind(@NotNull GradleDslElement holder,
                                        @Nullable GradleDslElement oldElement,
                                        @NotNull Object value,
                                        @NotNull String name) {
    // Here we just create the new element we need to add to the map we will create.
    // This element we be given as newElement in #replace.
    // Note: The parent will be changed to the expression map in replace.
    return createBasicExpression(holder, value, GradleNameElement.create(myFieldName));
  }

  @Override
  @NotNull
  public GradleDslExpression bindList(@NotNull GradleDslElement holder,
                                      @Nullable GradleDslElement oldElement,
                                      @NotNull String name,
                                      boolean isMethodCall) {
    return new GradleDslExpressionList(holder, GradleNameElement.create(myFieldName), isMethodCall);
  }

  @Override
  @NotNull
  public GradleDslExpression bindMap(@NotNull GradleDslElement holder,
                                     @Nullable GradleDslElement oldElement,
                                     @NotNull String name,
                                     boolean isMethodCall) {
    return new GradleDslExpressionMap(holder, GradleNameElement.create(myFieldName), isMethodCall);
  }

  @Override
  @NotNull
  public GradleDslExpression replace(@NotNull GradleDslElement holder,
                                     @Nullable GradleDslElement oldElement,
                                     @NotNull GradleDslExpression newElement,
                                     @NotNull String name) {
    if (oldElement instanceof GradleDslMethodCall) {
      GradleDslMethodCall methodCall = (GradleDslMethodCall)oldElement;
      if (!methodCall.getArguments().isEmpty() && methodCall.getArguments().get(0) instanceof GradleDslSimpleExpression) {
        // We need to adjust the tree so that the current element is contained within a map.
        GradleDslSimpleExpression argument = (GradleDslSimpleExpression)methodCall.getArguments().get(0);
        // Remove the old argument
        methodCall.remove(argument);

        // Create the new map.
        GradleDslExpressionMap expressionMap = new GradleDslExpressionMap(methodCall, GradleNameElement.empty());
        // Add the other elements.
        argument.rename(mySingleArgName);
        expressionMap.setNewElement(argument.copy());
        expressionMap.setNewElement(newElement);

        // Add the map as an argument.
        methodCall.addNewArgument(expressionMap);
      }

      return methodCall;
    }

    throw new IllegalStateException("Can't replace an element that isn't a GradleDslMethodCall");
  }
}
