/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.log.impl;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public interface VcsLogUiProperties {
  @NotNull
  <T> T get(@NotNull VcsLogUiProperty<T> property);

  <T> void set(@NotNull VcsLogUiProperty<T> property, @NotNull T value);

  <T> boolean exists(@NotNull VcsLogUiProperty<T> property);

  class VcsLogUiProperty<T> {
    @NotNull private final String myName;

    public VcsLogUiProperty(@NotNull String name) {
      myName = name;
    }

    @Override
    public String toString() {
      return myName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      VcsLogUiProperty<?> property = (VcsLogUiProperty<?>)o;
      return Objects.equals(myName, property.myName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myName);
    }
  }
}
