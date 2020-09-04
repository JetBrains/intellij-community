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
package com.android.tools.idea.gradle.dsl.parser.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * Represents a value that doesn't have a backing element. These are used to represent commonly used
 * values that are automatically populated by Gradle e.g "rootDir" and "projectDir".
 */
public class GradleDslGlobalValue extends GradleDslSimpleExpression {
  @NotNull Object myFakeValue;

  public GradleDslGlobalValue(@NotNull GradleDslElement parent, @NotNull Object value) {
    super(parent, null, GradleNameElement.fake("fakevalue"), null);
    myFakeValue = value;
  }

  public GradleDslGlobalValue(@NotNull GradleDslElement parent, @NotNull Object value, @NotNull String name) {
    super(parent, null, GradleNameElement.fake(name), null);
    myFakeValue = value;
  }

  @Override
  @NotNull
  public Object produceValue() {
    return myFakeValue;
  }

  @Override
  @NotNull
  public Object produceUnresolvedValue() {
    return produceValue();
  }

  @Override
  public void setValue(@NotNull Object value) {
    myFakeValue = value;
    valueChanged();
  }

  @Nullable
  @Override
  public Object produceRawValue() {
    return getUnresolvedValue();
  }

  @NotNull
  @Override
  public GradleDslGlobalValue copy() {
    assert myParent != null;
    return new GradleDslGlobalValue(myParent, produceValue());
  }

  @Override
  @NotNull
  public Collection<GradleDslElement> getChildren() {
    return Collections.emptyList();
  }

  @Override
  protected void apply() {
    // Do nothing
  }
}