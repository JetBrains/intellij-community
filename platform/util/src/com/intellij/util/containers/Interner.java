/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

public class Interner<T> {

  private final OpenTHashSet<T> mySet = new OpenTHashSet<T>();

  @NotNull
  public T intern(@NotNull T name) {
    T interned = mySet.get(name);
    if (interned != null) {
      return interned;
    }

    boolean added = mySet.add(name);
    assert added;

    return name;
  }

  public void clear() {
    mySet.clear();
  }

}
