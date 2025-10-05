// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.*;

/**
 * Stripped-down version of {@link com.intellij.util.containers.ContainerUtil}.
 * Intended to use by external (out-of-IDE-process) runners and helpers so it should not contain any library dependencies.
 * @deprecated Use collection methods instead
 */
@ApiStatus.ScheduledForRemoval
@Deprecated
public final class ContainerUtilRt {

  /**
   * @deprecated Use {@link com.intellij.util.containers.ContainerUtil#newArrayList(Object[])} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> ArrayList<T> newArrayList(T... elements) {
    ArrayList<T> list = new ArrayList<>(elements.length);
    Collections.addAll(list, elements);
    return list;
  }

  /**
   * @deprecated Use {@link com.intellij.util.containers.ContainerUtil#newLinkedHashSet(Object[])}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> LinkedHashSet<T> newLinkedHashSet(T... elements) {
    return new LinkedHashSet<>(Arrays.asList(elements));
  }

  /**
   * A variant of {@link Collections#emptyList()},
   * except that {@link #toArray()} here does not create garbage {@code new Object[0]} constantly.
   */
  private static final class EmptyList<T> extends AbstractList<T> implements RandomAccess, Serializable {
    private static final long serialVersionUID = 1L;

    private static final EmptyList<?> INSTANCE = new EmptyList<Object>();

    @Override
    public int size() {
      return 0;
    }

    @Override
    public boolean contains(Object obj) {
      return false;
    }

    @Override
    public T get(int index) {
      throw new IndexOutOfBoundsException("Index: " + index);
    }

    @Override
    public Object @NotNull [] toArray() {
      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public <E> E @NotNull [] toArray(E @NotNull [] a) {
      if (a.length != 0) {
        a[0] = null;
      }
      return a;
    }

    @NotNull
    @Override
    public Iterator<T> iterator() {
      return EmptyIterator.getInstance();
    }

    @NotNull
    @Override
    public ListIterator<T> listIterator() {
      return EmptyListIterator.getInstance();
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
      return c.isEmpty();
    }

    @Override
    @Contract(pure = true)
    public boolean isEmpty() {
      return true;
    }

    @Override
    @Contract(pure = true)
    public boolean equals(Object o) {
      return o instanceof List && ((List<?>)o).isEmpty();
    }

    @Override
    public int hashCode() {
      return 1;
    }
  }

  /**
   * @deprecated Use {@link com.intellij.util.containers.ContainerUtil#emptyList()}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  @NotNull
  @Contract(pure=true)
  public static <T> List<T> emptyList() {
    //noinspection unchecked
    return (List<T>)EmptyList.INSTANCE;
  }
}
