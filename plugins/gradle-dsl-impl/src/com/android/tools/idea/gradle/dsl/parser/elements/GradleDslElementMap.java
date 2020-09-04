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
package com.android.tools.idea.gradle.dsl.parser.elements;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a map of {@link GradleDslElement}s from their names.
 */
public class GradleDslElementMap extends GradlePropertiesDslElement {
  protected GradleDslElementMap(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, null, name);
  }

  @NotNull
  protected <E extends GradleDslElement> Collection<E> getValues(Class<E> clazz) {
    List<E> result = new ArrayList<>();
    for (Map.Entry<String, GradleDslElement> entry : getPropertyElements().entrySet()) {
      GradleDslElement propertyElement = entry.getValue();
      if (clazz.isInstance(propertyElement)) {
        result.add(clazz.cast(propertyElement));
      }
    }
    return result;
  }
}
