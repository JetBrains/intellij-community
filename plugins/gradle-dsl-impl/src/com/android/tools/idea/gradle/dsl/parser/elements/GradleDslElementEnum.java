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

import java.util.Map;
import java.util.Map.Entry;
import org.jetbrains.annotations.NotNull;

public class GradleDslElementEnum extends GradlePropertiesDslElement {
  private GradleDslElementEnum(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, null, name);
  }

  public GradleDslElementEnum(@NotNull GradleDslElement parent, @NotNull GradleNameElement name, Map<String,String> values) {
    this(parent, name);
    for (Entry<String, String> entry : values.entrySet()) {
      GradleDslGlobalValue element = new GradleDslGlobalValue(this, new EnumValue(entry.getValue()), entry.getKey());
      addDefaultProperty(element);
    }
  }

  public class EnumValue {
    @NotNull private final String myValue;

    private EnumValue(@NotNull String value) {
      myValue = value;
    }

    public String getValue() {
      return myValue;
    }
  }
}
