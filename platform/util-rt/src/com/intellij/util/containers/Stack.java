// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import org.jetbrains.annotations.ApiStatus.Obsolete;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * <h3>Obsolescence notice</h3>
 * <p>
 * Use {@link ArrayDeque}.
 * This implementation extends {@link ArrayList} which allows index access.
 * It also allows the iteration, but it's in the reversed order compared to {@link ArrayDeque#iterator}.
 * </p>
 *
 * A drop-in replacement for {@link java.util.Stack} based on {@link ArrayList} (instead of {@link Vector})
 * and therefore is (1) not synchronized and (2) faster.
 *
 * @author max
 */
@Obsolete
public class Stack<T> extends ArrayList<T> {
  public Stack() { }

  public Stack(int initialCapacity) {
    super(initialCapacity);
  }

  public Stack(@NotNull Collection<? extends T> init) {
    super(init);
  }

  @SafeVarargs
  public Stack(T... items) {
    super(items.length);
    for (T item : items) {
      push(item);
    }
  }

  /**
   * Use {@link Deque#push}.
   */
  @Obsolete
  public void push(T t) {
    add(t);
  }

  /**
   * Use {@link Deque#getFirst}.
   */
  @Obsolete
  public T peek() {
    final int size = size();
    if (size == 0) {
      throw new EmptyStackException();
    }
    return get(size - 1);
  }

  /**
   * Use {@link Deque#pop}.
   */
  @Obsolete
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
   * Use {@link Deque#pollFirst}.
   */
  @Obsolete
  @Nullable
  public T tryPop() {
    return isEmpty() ? null : pop();
  }

  /**
   * Use {@link Deque#isEmpty}.
   */
  @Obsolete
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