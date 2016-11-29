/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import java.util.List;

public interface ConcurrentList<E> extends List<E> {
  /**
   * Append the element if not present.
   *
   * @return true if the element was added
   */
  boolean addIfAbsent(E e);

  /**
   * Appends all of the elements in the specified collection that
   * are not already contained in this list, to the end of
   * this list, in the order that they are returned by the
   * specified collection's iterator.
   *
   * @return the number of elements added
   */
  int addAllAbsent(@NotNull Collection<? extends E> c);
}
