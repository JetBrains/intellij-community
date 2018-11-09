// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Function;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stripped-down version of {@code com.intellij.util.containers.ContainerUtil}.
 * Intended to use by external (out-of-IDE-process) runners and helpers so it should not contain any library dependencies.
 *
 * @since 12.0
 */
public class ContainerUtilRt {
  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <K, V> HashMap<K, V> newHashMap() {
    return new java.util.HashMap<K, V>();
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <K, V> HashMap<K, V> newHashMap(@NotNull Map<? extends K, ? extends V> map) {
    return new java.util.HashMap<K, V>(map);
  }

  @NotNull
  @Contract(value = "_,_ -> new", pure = true)
  public static <K, V> Map<K, V> newHashMap(@NotNull List<? extends K> keys, @NotNull List<? extends V> values) {
    if (keys.size() != values.size()) {
      throw new IllegalArgumentException(keys + " should have same length as " + values);
    }

    Map<K, V> map = newHashMap(keys.size());
    for (int i = 0; i < keys.size(); ++i) {
      map.put(keys.get(i), values.get(i));
    }
    return map;
  }

  @NotNull
  @Contract(value = "_,_ -> new", pure = true)
  public static <K, V> Map<K, V> newHashMap(@NotNull Pair<? extends K, ? extends V> first, @NotNull Pair<? extends K, ? extends V>... entries) {
    Map<K, V> map = newHashMap(entries.length + 1);
    map.put(first.getFirst(), first.getSecond());
    for (Pair<? extends K, ? extends V> entry : entries) {
      map.put(entry.getFirst(), entry.getSecond());
    }
    return map;
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <K, V> Map<K, V> newHashMap(int initialCapacity) {
    return new java.util.HashMap<K, V>(initialCapacity);
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <K extends Comparable, V> TreeMap<K, V> newTreeMap() {
    return new TreeMap<K, V>();
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <K extends Comparable, V> TreeMap<K, V> newTreeMap(@NotNull Map<? extends K, ? extends V> map) {
    return new TreeMap<K, V>(map);
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <K, V> LinkedHashMap<K, V> newLinkedHashMap() {
    return new LinkedHashMap<K, V>();
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <K, V> LinkedHashMap<K, V> newLinkedHashMap(int capacity) {
    return new LinkedHashMap<K, V>(capacity);
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <K, V> LinkedHashMap<K, V> newLinkedHashMap(@NotNull Map<? extends K, ? extends V> map) {
    return new LinkedHashMap<K, V>(map);
  }

  @NotNull
  @Contract(value = "_,_ -> new", pure = true)
  public static <K, V> LinkedHashMap<K,V> newLinkedHashMap(@NotNull Pair<? extends K, ? extends V> first, @NotNull Pair<? extends K, ? extends V>... entries) {
    LinkedHashMap<K, V> map = newLinkedHashMap();
    map.put(first.getFirst(), first.getSecond());
    for (Pair<? extends K, ? extends V> entry : entries) {
      map.put(entry.getFirst(), entry.getSecond());
    }
    return map;
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <T> LinkedList<T> newLinkedList() {
    return new LinkedList<T>();
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> LinkedList<T> newLinkedList(@NotNull T... elements) {
    final LinkedList<T> list = newLinkedList();
    Collections.addAll(list, elements);
    return list;
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> LinkedList<T> newLinkedList(@NotNull Iterable<? extends T> elements) {
    return copy(ContainerUtilRt.<T>newLinkedList(), elements);
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <T> ArrayList<T> newArrayList() {
    return new ArrayList<T>();
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> ArrayList<T> newArrayList(@NotNull T... elements) {
    ArrayList<T> list = newArrayListWithCapacity(elements.length);
    Collections.addAll(list, elements);
    return list;
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> ArrayList<T> newArrayList(@NotNull Iterable<? extends T> elements) {
    if (elements instanceof Collection) {
      @SuppressWarnings("unchecked") Collection<? extends T> collection = (Collection<? extends T>)elements;
      return new ArrayList<T>(collection);
    }
    return copy(ContainerUtilRt.<T>newArrayList(), elements);
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> ArrayList<T> newArrayListWithCapacity(int size) {
    return new ArrayList<T>(size);
  }

  @NotNull
  private static <T, C extends Collection<T>> C copy(@NotNull C collection, @NotNull Iterable<? extends T> elements) {
    for (T element : elements) {
      collection.add(element);
    }
    return collection;
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <T> HashSet<T> newHashSet() {
    return new java.util.HashSet<T>();
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> HashSet<T> newHashSet(int initialCapacity) {
    return new java.util.HashSet<T>(initialCapacity);
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> HashSet<T> newHashSet(@NotNull T... elements) {
    return new java.util.HashSet<T>(Arrays.asList(elements));
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> HashSet<T> newHashSet(@NotNull Iterable<? extends T> elements) {
    if (elements instanceof Collection) {
      @SuppressWarnings("unchecked") Collection<? extends T> collection = (Collection<? extends T>)elements;
      return new java.util.HashSet<T>(collection);
    }
    return newHashSet(elements.iterator());
  }

  @NotNull
  public static <T> HashSet<T> newHashSet(@NotNull Iterator<? extends T> iterator) {
    HashSet<T> set = newHashSet();
    while (iterator.hasNext()) set.add(iterator.next());
    return set;
  }

  @Contract(value = " -> new", pure = true)
  @NotNull
  public static <T> LinkedHashSet<T> newLinkedHashSet() {
    return new LinkedHashSet<T>();
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> LinkedHashSet<T> newLinkedHashSet(@NotNull T... elements) {
    return newLinkedHashSet(Arrays.asList(elements));
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> LinkedHashSet<T> newLinkedHashSet(@NotNull Iterable<? extends T> elements) {
    if (elements instanceof Collection) {
      @SuppressWarnings("unchecked") Collection<? extends T> collection = (Collection<? extends T>)elements;
      return new LinkedHashSet<T>(collection);
    }
    return copy(ContainerUtilRt.<T>newLinkedHashSet(), elements);
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <T> TreeSet<T> newTreeSet() {
    return new TreeSet<T>();
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> TreeSet<T> newTreeSet(@NotNull T... elements) {
    TreeSet<T> set = newTreeSet();
    Collections.addAll(set, elements);
    return set;
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> TreeSet<T> newTreeSet(@NotNull Iterable<? extends T> elements) {
    return copy(ContainerUtilRt.<T>newTreeSet(), elements);
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> TreeSet<T> newTreeSet(@Nullable Comparator<? super T> comparator) {
    return new TreeSet<T>(comparator);
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <T> Stack<T> newStack() {
    return new Stack<T>();
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> Stack<T> newStack(@NotNull Collection<? extends T> elements) {
    return new Stack<T>(elements);
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> Stack<T> newStack(@NotNull T... initial) {
    return new Stack<T>(Arrays.asList(initial));
  }

  /**
   * A variant of {@link Collections#emptyList()},
   * except that {@link #toArray()} here does not create garbage {@code new Object[0]} constantly.
   */
  private static class EmptyList<T> extends AbstractList<T> implements RandomAccess, Serializable {
    private static final long serialVersionUID = 1L;

    private static final EmptyList INSTANCE = new EmptyList();

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
      return o instanceof List && ((List)o).isEmpty();
    }

    @Override
    public int hashCode() {
      return 1;
    }
  }

  @NotNull
  @Contract(pure=true)
  public static <T> List<T> emptyList() {
    //noinspection unchecked
    return (List<T>)EmptyList.INSTANCE;
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <T> CopyOnWriteArrayList<T> createEmptyCOWList() {
    // does not create garbage new Object[0]
    return new CopyOnWriteArrayList<T>(ContainerUtilRt.<T>emptyList());
  }

  /**
   * @see #addIfNotNull(Collection, Object)
   */
  @Deprecated
  public static <T> void addIfNotNull(@Nullable T element, @NotNull Collection<? super T> result) {
    if (element != null) {
      result.add(element);
    }
  }

  public static <T> void addIfNotNull(@NotNull Collection<? super T> result, @Nullable T element) {
    if (element != null) {
      result.add(element);
    }
  }

  /**
   * @return read-only list consisting of the elements from array converted by mapper
   */
  @NotNull
  @Contract(pure=true)
  public static <T, V> List<V> map2List(@NotNull T[] array, @NotNull Function<? super T, ? extends V> mapper) {
    return map2List(Arrays.asList(array), mapper);
  }

  /**
   * @param collection an input collection to process
   * @param mapping a side-effect free function which transforms collection elements
   * @return read-only list consisting of the elements from the array converted by mapping with nulls filtered out
   */
  @NotNull
  @Contract(pure=true)
  public static <T, V> List<V> mapNotNull(@NotNull Collection<? extends T> collection, @NotNull Function<? super T, ? extends V> mapping) {
    if (collection.isEmpty()) {
      return emptyList();
    }

    List<V> result = new ArrayList<V>(collection.size());
    for (T t : collection) {
      final V o = mapping.fun(t);
      if (o != null) {
        result.add(o);
      }
    }
    return result.isEmpty() ? ContainerUtilRt.<V>emptyList() : result;
  }

  /**
   * @return read-only list consisting of the elements from collection converted by mapper
   */
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
  
  /**
   * @return read-only list consisting key-value pairs of a map
   */
  @NotNull
  @Contract(pure=true)
  public static <K, V> List<Pair<K, V>> map2List(@NotNull Map<? extends K, ? extends V> map) {
    if (map.isEmpty()) return emptyList();
    final List<Pair<K, V>> result = new ArrayList<Pair<K, V>>(map.size());
    for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
      result.add(Pair.create(entry.getKey(), entry.getValue()));
    }
    return result;
  }

  /**
   * @return read-only set consisting of the elements from collection converted by mapper
   */
  @NotNull
  @Contract(pure=true)
  public static <T, V> Set<V> map2Set(@NotNull T[] collection, @NotNull Function<? super T, ? extends V> mapper) {
    return map2Set(Arrays.asList(collection), mapper);
  }

  /**
   * @return read-only set consisting of the elements from collection converted by mapper
   */
  @NotNull
  @Contract(pure=true)
  public static <T, V> Set<V> map2Set(@NotNull Collection<? extends T> collection, @NotNull Function<? super T, ? extends V> mapper) {
    if (collection.isEmpty()) return Collections.emptySet();
    Set <V> set = new HashSet<V>(collection.size());
    for (final T t : collection) {
      set.add(mapper.fun(t));
    }
    return set;
  }

  /**
   * @deprecated use {@link List#toArray(Object[])} instead
   */
  @Deprecated
  @NotNull
  public static <T> T[] toArray(@NotNull List<T> collection, @NotNull T[] array) {
    return collection.toArray(array);
  }

  /**
   * @deprecated use {@link Collection#toArray(Object[])} instead
   */
  @Deprecated
  @NotNull
  public static <T> T[] toArray(@NotNull Collection<? extends T> c, @NotNull T[] sample) {
    return c.toArray(sample);
  }

  @Nullable
  @Contract(pure=true)
  public static <T, L extends List<T>> T getLastItem(@Nullable L list, @Nullable T def) {
    return isEmpty(list) ? def : list.get(list.size() - 1);
  }

  @Nullable
  @Contract(pure=true)
  public static <T, L extends List<T>> T getLastItem(@Nullable L list) {
    return getLastItem(list, null);
  }

  @Contract(value = "null -> true", pure = true)
  public static <T> boolean isEmpty(@Nullable Collection<? extends T> collection) {
    return collection == null || collection.isEmpty();
  }

  @Nullable
  @Contract(pure=true)
  public static <T, V extends T> V find(@NotNull Iterable<? extends V> iterable, @NotNull Condition<? super T> condition) {
    return find(iterable.iterator(), condition);
  }

  @Nullable
  public static <T, V extends T> V find(@NotNull Iterator<? extends V> iterator, @NotNull Condition<? super T> condition) {
    while (iterator.hasNext()) {
      V value = iterator.next();
      if (condition.value(value)) return value;
    }
    return null;
  }

  @Contract(pure=true)
  public static <T> int indexOf(@NotNull List<? extends T> list, @NotNull Condition<? super T> condition) {
    for (int i = 0, listSize = list.size(); i < listSize; i++) {
      T t = list.get(i);
      if (condition.value(t)) {
        return i;
      }
    }
    return -1;
  }

}
