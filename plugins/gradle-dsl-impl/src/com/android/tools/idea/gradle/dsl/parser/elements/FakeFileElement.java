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
package com.android.tools.idea.gradle.dsl.parser.elements;

import com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This element is used to combine all the arguments in a GradleDslMethodCall into one value
 * by joining them with a "/". For Example:
 * <p>
 * {@code   path new File("foo", "bar")}
 * <p>
 * Will produce the value "foo/bar"
 */
public class FakeFileElement extends FakeElement {
  @NotNull
  private GradleDslMethodCall myMethodCall;

  public FakeFileElement(@Nullable GradleDslElement parent,
                         @NotNull GradleDslMethodCall methodCall) {
    super(parent, GradleNameElement.copy(methodCall.getNameElement()), methodCall, true);
    myMethodCall = methodCall;
  }

  @Nullable
  @Override
  protected Object extractValue() {
    return PropertyUtil.getFileValue(myMethodCall);
  }

  @Override
  protected void consumeValue(@Nullable Object value) {
    // This is handled in FileConstructorTransform
  }

  @Nullable
  @Override
  public Object produceRawValue() {
    return extractValue();
  }

  @NotNull
  @Override
  public GradleDslSimpleExpression copy() {
    return new FakeFileElement(myParent, myMethodCall);
  }
}
