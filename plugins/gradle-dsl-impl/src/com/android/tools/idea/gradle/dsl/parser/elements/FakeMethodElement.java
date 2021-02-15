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
package com.android.tools.idea.gradle.dsl.parser.elements;

import org.jetbrains.annotations.NotNull;

public class FakeMethodElement extends FakeElement {
  public FakeMethodElement(@NotNull GradleDslMethodCall methodCall) {
    super(methodCall.getParent(), GradleNameElement.fake(methodCall.getMethodName()), methodCall, true);
  }

  @Override
  @NotNull
  public GradleDslSimpleExpression copy() {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public Object extractValue() {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public void consumeValue(Object value) {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public Object produceRawValue() {
    throw new UnsupportedOperationException("not implemented");
  }
}
