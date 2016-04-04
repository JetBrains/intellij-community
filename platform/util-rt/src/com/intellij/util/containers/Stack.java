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
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A drop-in replacement for {@link java.util.Stack} based on {@link ArrayList} (instead of {@link Vector})
 * and therefore is (1) not synchronized and (2) faster.
 *
 * @author max
 */
public class Stack<T> extends ArrayList<T> {
  public Stack() { }

  public Stack(int initialCapacity) {
    super(initialCapacity);
  }

  public Stack(@NotNull Collection<T> init) {
    super(init);
  }

  public Stack(@NotNull T... items) {
    for (T item : items) {
      push(item);
    }
  }

  public void push(T t) {
    add(t);
  }

  public T peek() {
    final int size = size();
    if (size == 0) throw new EmptyStackException();
    return get(size - 1);
  }

  public T pop() {
    final int size = size();
    if (size == 0) throw new EmptyStackException();
    return remove(size - 1);
  }

  @Nullable
  public T tryPop() {
    return isEmpty() ? null : pop();
  }

  public boolean empty() {
    return isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof RandomAccess && o instanceof List) {
      List other = (List)o;
      if (size() != other.size()) {
        return false;
      }

      for (int i = 0; i < other.size(); i++) {
        Object o1 = other.get(i);
        Object o2 = get(i);
        if (!(o1 == null ? o2 == null : o1.equals(o2))) {
          return false;
        }
      }

      return true;
    }

    return super.equals(o);
  }
}