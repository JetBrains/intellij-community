/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

public class VcsLogProperties {
  public static class VcsLogProperty<T> {
    private final T defaultValue;

    private VcsLogProperty(@NotNull T defaultValue) {
      this.defaultValue = defaultValue;
    }
  }

  @NotNull public static final VcsLogProperty<Boolean> LIGHTWEIGHT_BRANCHES = new VcsLogProperty<>(false);

  @NotNull
  public static <T> T get(@NotNull VcsLogProvider provider, VcsLogProperty<T> property) {
    T value = provider.getPropertyValue(property);
    if (value == null) return property.defaultValue;
    return value;
  }
}
