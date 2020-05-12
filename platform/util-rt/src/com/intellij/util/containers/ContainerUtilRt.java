// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Function;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.*;

/**
 * Stripped-down version of {@link com.intellij.util.containers.ContainerUtil}.
 * Intended to use by external (out-of-IDE-process) runners and helpers so it should not contain any library dependencies.
 */
public final class ContainerUtilRt {
  /**
   * @deprecated Use {@link HashMap#HashMap(int)}
   */
  @NotNull
  @Contract(value = "_ -> new", pure = true)
  @Deprecated
  public static <K, V> Map<K, V> newHashMap(int initialCapacity) {
    return new HashMap<K, V>(initialCapacity);
  }

  /**
   * @deprecated Use {@link HashMap#HashMap()}
   */
  @NotNull
  @Contract(value = " -> new", pure = true)
  @Deprecated
  public static <K, V> HashMap<K, V> newHashMap() {
    return new HashMap<K, V>();
  }

  /**
   * @deprecated Use {@link HashMap#HashMap(Map)}
   */
  @NotNull
  @Contract(value = "_ -> new", pure = true)
  @Deprecated
  public static <K, V> HashMap<K, V> newHashMap(@NotNull Map<? extends K, ? extends V> map) {
    return new HashMap<K, V>(map);
  }

  /**
   * Use only for {@link Iterable}, for {@link Collection} please use {@link LinkedList#LinkedList(Collection)} directly.
   */
  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> LinkedList<T> newLinkedList(@NotNull Iterable<? extends T> elements) {
    return copy(new LinkedList<T>(), elements);
  }

  /**
   * @deprecated Use {@link LinkedList#LinkedList(Collection)} instead.
   */
  @NotNull
  @Contract(value = "_ -> fail", pure = true)
  @Deprecated
  public static <T> LinkedList<T> newLinkedList(@SuppressWarnings("unused") @NotNull Collection<? extends T> elements) {
    throw new AbstractMethodError("Use 'new LinkedList<>(elements)' instead");
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
  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> ArrayList<T> newArrayList(@NotNull T... elements) {
    ArrayList<T> list = new ArrayList<T>(elements.length);
    Collections.addAll(list, elements);
    return list;
  }

  /**
   * @deprecated Use {@link com.intellij.util.containers.ContainerUtil#newArrayList(Iterable)} instead
   */
  @Deprecated
  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> ArrayList<T> newArrayList(@NotNull Iterable<? extends T> elements) {
    return copy(new ArrayList<T>(), elements);
  }

  /**
   * @deprecated Use {@link ArrayList#ArrayList(Collection)} instead
   */
  @Deprecated
  @NotNull
  @Contract(value = "_ -> fail", pure = true)
  public static <T> ArrayList<T> newArrayList(@SuppressWarnings("unused") @NotNull Collection<? extends T> elements) {
    throw new AbstractMethodError("Use 'new ArrayList<>(elements)' instead");
  }

  @NotNull
  static <T, C extends Collection<? super T>> C copy(@NotNull C collection, @NotNull Iterable<? extends T> elements) {
    for (T element : elements) {
      collection.add(element);
    }
    return collection;
  }

  /**
   * @deprecated Use {@link HashSet#HashSet(int)}
   */
  @NotNull
  @Contract(value = " -> new", pure = true)
  @Deprecated
  public static <T> HashSet<T> newHashSet() {
    return new HashSet<T>();
  }

  /**
   * @deprecated Use {@link com.intellij.util.containers.ContainerUtil#newHashSet(Object[])}
   */
  @Deprecated
  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> HashSet<T> newHashSet(@NotNull T... elements) {
    return new HashSet<T>(Arrays.asList(elements));
  }

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
  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> LinkedHashSet<T> newLinkedHashSet(@NotNull T... elements) {
    return new LinkedHashSet<T>(Arrays.asList(elements));
  }

  /**
   * @deprecated Use {@link TreeSet#TreeSet()}
   */
  @NotNull
  @Contract(value = " -> new", pure = true)
  @Deprecated
  public static <T extends Comparable<? super T>> TreeSet<T> newTreeSet() {
    return new TreeSet<T>();
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
      //noinspection deprecation
      return EmptyIterator.getInstance();
    }

    @NotNull
    @Override
    public ListIterator<T> listIterator() {
      //noinspection deprecation
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
  @NotNull
  @Contract(pure=true)
  public static <T> List<T> emptyList() {
    //noinspection unchecked
    return (List<T>)EmptyList.INSTANCE;
  }

  /**
   * @deprecated Use {@link com.intellij.util.containers.ContainerUtil#addIfNotNull(Collection, Object)}
   */
  @Deprecated
  public static <T> void addIfNotNull(@NotNull Collection<? super T> result, @Nullable T element) {
    if (element != null) {
      result.add(element);
    }
  }

  /**
   * @deprecated Use {@link com.intellij.util.containers.ContainerUtil#map2List(Collection, Function)}
   * @return read-only list consisting of the elements from collection converted by mapper
   */
  @Deprecated
  @NotNull
  @Contract(pure=true)
  public static <T, V> List<V> map2List(@NotNull Collection<? extends T> collection, @NotNull Function<? super T, ? extends V> mapper) {
    if (collection.isEmpty()) return emptyList();
    List<V> list = new ArrayList<V>(collection.size());
    for (final T t : collection) {
      list.add(mapper.fun(t));
    }
    return list;
  }
}
