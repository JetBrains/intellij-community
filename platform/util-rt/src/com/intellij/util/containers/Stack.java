// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  public Stack(@NotNull Collection<? extends T> init) {
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
    if (size == 0) {
      throw new EmptyStackException();
    }
    return get(size - 1);
  }

  public T pop() {
    final int size = size();
    if (size == 0) throw new EmptyStackException();
    return remove(size - 1);
  }

  public int search(Object o) {
    int idx = lastIndexOf(o);
    return idx == -1 ? -1 : size() - idx;
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
      List<?> other = (List<?>)o;
      if (size() != other.size()) {
        return false;
      }

      for (int i = 0; i < other.size(); i++) {
        Object o1 = other.get(i);
        Object o2 = get(i);
        if (!Objects.equals(o1, o2)) {
          return false;
        }
      }

      return true;
    }

    return super.equals(o);
  }
}