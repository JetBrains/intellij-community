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
package com.android.tools.idea.gradle.dsl.parser.semantics;

import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.UNSPECIFIED_FOR_NOW;

import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

public class ModelPropertyDescription {
  @NotNull public final String name;
  @NotNull public final ModelPropertyType type;

  public ModelPropertyDescription(@NotNull String name) {
    this.name = name;
    this.type = UNSPECIFIED_FOR_NOW;
  }

  public ModelPropertyDescription(@NotNull String name, @NotNull ModelPropertyType type) {
    this.name = name;
    this.type = type;
  }

  @Override
  public String toString() {
    return name + " (" + type + ")";
  }

  // TODO(b/151216877): really ModelPropertyDescriptions should be singletons, but while we still have some String descriptions
  //  (for convenience) let's allow multiple descriptions of the same thing and handle equality ourselves.
  @Override
  public int hashCode() {
    return Arrays.hashCode(new Object[] {name, type});
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof ModelPropertyDescription) {
      ModelPropertyDescription mpd = (ModelPropertyDescription) obj;
      return this.name.equals(mpd.name) && this.type.equals(mpd.type);
    }
    else {
      return false;
    }
  }
}
