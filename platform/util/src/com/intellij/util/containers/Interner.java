/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Allow to reuse structurally equal objects to avoid memory being wasted on them. Note: objects are cached inside
 * and on hard references, so even the ones that are not used anymore will be still present in the memory.
 *
 * @see WeakInterner
 * @author peter
 */

public class Interner<T> {
  private final OpenTHashSet<T> mySet;

  public Interner() {
    mySet = new OpenTHashSet<T>();
  }
  public Interner(@NotNull TObjectHashingStrategy<T> strategy) {
    mySet = new OpenTHashSet<T>(strategy);
  }

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

  @NotNull
  public Set<T> getValues() {
    return mySet;
  }

}
