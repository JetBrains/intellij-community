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
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.createBasicExpression;

/**
 * This transform allows for the editing of properties that are tied to map elements within method calls.
 * For example this transform could be used to represent dir, include and exclude in the following build block
 * dependencies {
 * compile fileTree(dir: 'lib', include: ['*.jar'], exclude: ['*.aar'])
 * }
 * <p>
 * In order to allow a property to represent "include" above, it would need to by created with "fileTree" as the
 * method name, "include" as the field name and attached to a {@link GradlePropertyModel} which is tied to the
 * overall method call element "compile fileTree(dir: 'lib', include: ['*.jar'], exclude: ['*.aar'])".
 */
public class MapMethodTransform extends PropertyTransform {
  @NotNull
  private final String myMethodName;
  @NotNull
  private final String myFieldName;

  public MapMethodTransform(@NotNull String methodName, @NotNull String fieldName) {
    myMethodName = methodName;
    myFieldName = fieldName;
  }

  @Override
  public boolean test(@Nullable GradleDslElement e, @NotNull GradleDslElement holder) {
    if (e == null) {
      return true;
    }

    if (!(e instanceof GradleDslMethodCall)) {
      return false;
    }

    GradleDslMethodCall methodCall = (GradleDslMethodCall)e;
    if (methodCall.getArguments().isEmpty()) {
      return true;
    }

    // Make sure the first argument is a map.
    if (!(methodCall.getArguments().get(0) instanceof GradleDslExpressionMap)) {
      return false;
    }

    return true;
  }

  @Nullable
  @Override
  public GradleDslElement transform(@Nullable GradleDslElement e) {
    if (e == null) {
      return null;
    }

    assert e instanceof GradleDslMethodCall;
    GradleDslMethodCall methodCall = (GradleDslMethodCall)e;
    if (methodCall.getArguments().isEmpty()) {
      return null;
    }
    assert methodCall.getArguments().get(0) instanceof GradleDslExpressionMap;
    GradleDslExpressionMap map = (GradleDslExpressionMap)methodCall.getArguments().get(0);
    return map.getPropertyElement(myFieldName);
  }

  @NotNull
  @Override
  public GradleDslExpression bind(@NotNull GradleDslElement holder,
                                  @Nullable GradleDslElement oldElement,
                                  @NotNull Object value,
                                  @NotNull String name) {
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
  public GradleDslMethodCall replace(@NotNull GradleDslElement holder,
                                     @Nullable GradleDslElement oldElement,
                                     @NotNull GradleDslExpression newElement,
                                     @NotNull String name) {
    GradleDslMethodCall methodCall;

    if (!(oldElement instanceof GradleDslMethodCall)) {
      methodCall = new GradleDslMethodCall(holder, GradleNameElement.create(name), myMethodName);
    }
    else {
      methodCall = (GradleDslMethodCall)oldElement;
    }
    GradleDslExpressionMap map;
    GradleDslElement fieldElement = null;
    if (!methodCall.getArguments().isEmpty()) {
      assert methodCall.getArguments().get(0) instanceof GradleDslExpressionMap;
      map = (GradleDslExpressionMap)methodCall.getArguments().get(0);
      fieldElement = transform(methodCall);
    }
    else {
      map = new GradleDslExpressionMap(methodCall, GradleNameElement.empty(), false);
      methodCall.addNewArgument(map);
    }
    if (fieldElement != null) {
      map.removeProperty(fieldElement);
    }
    map.setNewElement(newElement);

    return methodCall;
  }
}
