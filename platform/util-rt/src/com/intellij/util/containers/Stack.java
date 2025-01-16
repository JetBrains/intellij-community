// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A drop-in replacement for {@link java.util.Stack} based on {@link ArrayList} (instead of {@link Vector})
 * and therefore is (1) not synchronized and (2) faster.
 *
 * @author max
 * @deprecated use {@link ArrayDeque}
 */
@Deprecated
public class Stack<T> extends ArrayList<T> {
  public Stack() { }

  public Stack(int initialCapacity) {
    super(initialCapacity);
  }

  public Stack(@NotNull Collection<? extends T> init) {
    super(init);
  }

  @SafeVarargs
  public Stack(@NotNull T... items) {
    super(items.length);
    for (T item : items) {
      push(item);
    }
  }

  /**
   * @deprecated use {@link Deque#push}
   */
  @Deprecated
  public void push(T t) {
    add(t);
  }

  /**
   * @deprecated use {@link Deque#getFirst}
   */
  @Deprecated
  public T peek() {
    final int size = size();
    if (size == 0) {
      throw new EmptyStackException();
    }
    return get(size - 1);
  }

  /**
   * @deprecated use {@link Deque#pop}
   */
  @Deprecated
  public T pop() {
    final int size = size();
    if (size == 0) throw new EmptyStackException();
    return remove(size - 1);
  }

  /**
   * @deprecated don't search for element index in a stack, use another collection
   */
  @Deprecated
  public int search(Object o) {
    int idx = lastIndexOf(o);
    return idx == -1 ? -1 : size() - idx;
  }

  /**
   * @deprecated use {@link Deque#pollFirst}
   */
  @Deprecated
  @Nullable
  public T tryPop() {
    return isEmpty() ? null : pop();
  }

  /**
   * @deprecated use {@link Deque#isEmpty}
   */
  @Deprecated
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