/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.android;

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractFlavorTypeCollectionDslElement extends GradleDslElementMap {
  @NotNull
  private static final String[] KNOWN_METHOD_NAMES_ARRAY = {
    "all", "create", "register", "maybeCreate", "configure", "forEach", "stream", "getAt",
    "getByName", "named", "findAll", "matching", "add", "clear", "addAll", "equals", "isEmpty", "size",
    "remove", "removeAll", "withType", "getAsMap", "each"
  };

  @NotNull
  protected static final List<String> KNOWN_METHOD_NAMES = Arrays.asList(KNOWN_METHOD_NAMES_ARRAY);

  protected AbstractFlavorTypeCollectionDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  public boolean isBlockElement() {
    return true;
  }
}
