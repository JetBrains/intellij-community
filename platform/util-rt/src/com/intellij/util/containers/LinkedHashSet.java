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

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@SuppressWarnings("ClassNameSameAsAncestorName")
class LinkedHashSet<E> extends java.util.LinkedHashSet<E> {
  public LinkedHashSet() { }

  public LinkedHashSet(@NotNull Collection<? extends E> collection) {
    super(collection);
  }

  public LinkedHashSet(int i, float v) {
    super(i, v);
  }

  public LinkedHashSet(int i) {
    super(i);
  }

  public void clear() {
    if (size() == 0) return; // optimization
    super.clear();
  }
}
