// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Function;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.*;

/**
 * Stripped-down version of {@link com.intellij.util.containers.ContainerUtil}.
 * Intended to use by external (out-of-IDE-process) runners and helpers so it should not contain any library dependencies.
 * @deprecated Use collection methods instead
 */
@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
@Deprecated
public final class ContainerUtilRt {
  /**
   * @deprecated Use {@link HashMap#HashMap()}
   */
  @NotNull
  @Contract(value = " -> new", pure = true)
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static <K, V> HashMap<K, V> newHashMap() {
    return new HashMap<K, V>();
  }

  /**
   * @deprecated Use {@link ArrayList#ArrayList()} instead
   */
  @NotNull
  @Deprecated
  @Contract(value = " -> new", pure = true)
  public static <T> ArrayList<T> newArrayList() {
    return new ArrayList<T>();
  }

  /**
   * @deprecated Use {@link com.intellij.util.containers.ContainerUtil#newArrayList(Object[])} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> ArrayList<T> newArrayList(@NotNull T... elements) {
    ArrayList<T> list = new ArrayList<T>(elements.length);
    Collections.addAll(list, elements);
    return list;
  }

  /**
   * @deprecated Use {@link HashSet#HashSet(int)}
   */
  @NotNull
  @Contract(value = " -> new", pure = true)
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static <T> HashSet<T> newHashSet() {
    return new HashSet<T>();
  }

  /**
   * @deprecated Use {@link com.intellij.util.containers.ContainerUtil#newHashSet(Object[])}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> HashSet<T> newHashSet(@NotNull T... elements) {
    return new HashSet<T>(Arrays.asList(elements));
  }

  /**
   * @deprecated Use {@link com.intellij.util.containers.ContainerUtil#newHashSet(Iterable)}
   */
  @Deprecated
  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> HashSet<T> newHashSet(@NotNull Iterable<? extends T> elements) {
    Iterator<? extends T> iterator = elements.iterator();
    HashSet<T> set = new HashSet<T>();
    while (iterator.hasNext()) set.add(iterator.next());
    return set;
  }

  /**
   * @deprecated Use {@link com.intellij.util.containers.ContainerUtil#newLinkedHashSet(Object[])}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> LinkedHashSet<T> newLinkedHashSet(@NotNull T... elements) {
    return new LinkedHashSet<T>(Arrays.asList(elements));
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

    @NotNull
    @Override
    public Object[] toArray() {
      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }

    @NotNull
    @Override
    public <E> E[] toArray(@NotNull E[] a) {
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
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  @NotNull
  @Contract(pure=true)
  public static <T> List<T> emptyList() {
    //noinspection unchecked
    return (List<T>)EmptyList.INSTANCE;
  }

  /**
   * @deprecated Use {@link com.intellij.util.containers.ContainerUtil#map2List(Collection, Function)}
   * @return read-only list consisting of the elements from collection converted by mapper
   */
  @Deprecated
  @NotNull
  @Contract(pure=true)
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  public static <T, V> List<V> map2List(@NotNull Collection<? extends T> collection, @NotNull Function<? super T, ? extends V> mapper) {
    if (collection.isEmpty()) return emptyList();
    List<V> list = new ArrayList<V>(collection.size());
    for (final T t : collection) {
      list.add(mapper.fun(t));
    }
    return list;
  }
}
