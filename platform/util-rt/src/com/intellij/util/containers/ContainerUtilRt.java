/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.Pair;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stripped-down version of {@code com.intellij.util.containers.ContainerUtil}.
 * Intended to use by external (out-of-IDE-process) runners and helpers so it should not contain any library dependencies.
 *
 * @since 12.0
 */
@SuppressWarnings({"UtilityClassWithoutPrivateConstructor", "UnusedDeclaration"})
public class ContainerUtilRt {
  private static final int ARRAY_COPY_THRESHOLD = 20;

  @NotNull
  public static <K, V> HashMap<K, V> newHashMap() {
    return new com.intellij.util.containers.HashMap<K, V>();
  }

  @NotNull
  public static <K, V> HashMap<K, V> newHashMap(@NotNull Map<K, V> map) {
    return new com.intellij.util.containers.HashMap<K, V>(map);
  }

  @NotNull
  public static <K, V> Map<K, V> newHashMap(@NotNull List<K> keys, @NotNull List<V> values) {
    if (keys.size() != values.size()) {
      throw new IllegalArgumentException(keys + " should have same length as " + values);
    }

    Map<K, V> map = newHashMap();
    for (int i = 0; i < keys.size(); ++i) {
      map.put(keys.get(i), values.get(i));
    }
    return map;
  }

  @NotNull
  public static <K, V> Map<K,V> newHashMap(@NotNull Pair<K, V> first, Pair<K, V>[] entries) {
    Map<K, V> map = newHashMap();
    map.put(first.getFirst(), first.getSecond());
    for (Pair<K, V> entry : entries) {
      map.put(entry.getFirst(), entry.getSecond());
    }
    return map;
  }

  @NotNull
  public static <K extends Comparable, V> TreeMap<K, V> newTreeMap() {
    return new TreeMap<K, V>();
  }

  @NotNull
  public static <K extends Comparable, V> TreeMap<K, V> newTreeMap(@NotNull Map<K, V> map) {
    return new TreeMap<K, V>(map);
  }

  @NotNull
  public static <K, V> LinkedHashMap<K, V> newLinkedHashMap() {
    return new com.intellij.util.containers.LinkedHashMap<K, V>();
  }

  @NotNull
  public static <K, V> LinkedHashMap<K, V> newLinkedHashMap(@NotNull Map<K, V> map) {
    return new com.intellij.util.containers.LinkedHashMap<K, V>(map);
  }

  @NotNull
  public static <K, V> LinkedHashMap<K,V> newLinkedHashMap(@NotNull Pair<K, V> first, Pair<K, V>[] entries) {
    LinkedHashMap<K, V> map = newLinkedHashMap();
    map.put(first.getFirst(), first.getSecond());
    for (Pair<K, V> entry : entries) {
      map.put(entry.getFirst(), entry.getSecond());
    }
    return map;
  }

  @NotNull
  public static <T> LinkedList<T> newLinkedList() {
    return new LinkedList<T>();
  }

  @NotNull
  public static <T> LinkedList<T> newLinkedList(@NotNull T... elements) {
    final LinkedList<T> list = newLinkedList();
    Collections.addAll(list, elements);
    return list;
  }

  @NotNull
  public static <T> LinkedList<T> newLinkedList(@NotNull Iterable<? extends T> elements) {
    return copy(ContainerUtilRt.<T>newLinkedList(), elements);
  }

  @NotNull
  public static <T> ArrayList<T> newArrayList() {
    return new ArrayList<T>();
  }

  @NotNull
  public static <T> ArrayList<T> newArrayList(@NotNull T... elements) {
    ArrayList<T> list = newArrayListWithCapacity(elements.length);
    Collections.addAll(list, elements);
    return list;
  }

  @NotNull
  public static <T> ArrayList<T> newArrayList(@NotNull Iterable<? extends T> elements) {
    if (elements instanceof Collection) {
      @SuppressWarnings("unchecked") Collection<? extends T> collection = (Collection<? extends T>)elements;
      return new ArrayList<T>(collection);
    }
    return copy(ContainerUtilRt.<T>newArrayList(), elements);
  }

  /** @deprecated Use {@link #newArrayListWithCapacity(int)} (to remove in IDEA 15) */
  public static <T> ArrayList<T> newArrayListWithExpectedSize(int size) {
    return newArrayListWithCapacity(size);
  }

  @NotNull
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
  public static <T> HashSet<T> newHashSet() {
    return new com.intellij.util.containers.HashSet<T>();
  }

  @NotNull
  public static <T> HashSet<T> newHashSet(int initialCapacity) {
    return new com.intellij.util.containers.HashSet<T>(initialCapacity);
  }

  @NotNull
  public static <T> HashSet<T> newHashSet(@NotNull T... elements) {
    return new com.intellij.util.containers.HashSet<T>(Arrays.asList(elements));
  }

  @NotNull
  public static <T> HashSet<T> newHashSet(@NotNull Iterable<? extends T> elements) {
    if (elements instanceof Collection) {
      @SuppressWarnings("unchecked") Collection<? extends T> collection = (Collection<? extends T>)elements;
      return new com.intellij.util.containers.HashSet<T>(collection);
    }
    return newHashSet(elements.iterator());
  }

  @NotNull
  public static <T> HashSet<T> newHashSet(@NotNull Iterator<? extends T> iterator) {
    HashSet<T> set = newHashSet();
    while (iterator.hasNext()) set.add(iterator.next());
    return set;
  }

  @NotNull
  public static <T> LinkedHashSet<T> newLinkedHashSet() {
    return new com.intellij.util.containers.LinkedHashSet<T>();
  }

  @NotNull
  public static <T> LinkedHashSet<T> newLinkedHashSet(@NotNull T... elements) {
    return newLinkedHashSet(Arrays.asList(elements));
  }

  @NotNull
  public static <T> LinkedHashSet<T> newLinkedHashSet(@NotNull Iterable<? extends T> elements) {
    if (elements instanceof Collection) {
      @SuppressWarnings("unchecked") Collection<? extends T> collection = (Collection<? extends T>)elements;
      return new com.intellij.util.containers.LinkedHashSet<T>(collection);
    }
    return copy(ContainerUtilRt.<T>newLinkedHashSet(), elements);
  }

  @NotNull
  public static <T> TreeSet<T> newTreeSet() {
    return new TreeSet<T>();
  }

  @NotNull
  public static <T> TreeSet<T> newTreeSet(@NotNull T... elements) {
    TreeSet<T> set = newTreeSet();
    Collections.addAll(set, elements);
    return set;
  }

  @NotNull
  public static <T> TreeSet<T> newTreeSet(@NotNull Iterable<? extends T> elements) {
    return copy(ContainerUtilRt.<T>newTreeSet(), elements);
  }

  @NotNull
  public static <T> TreeSet<T> newTreeSet(@Nullable Comparator<? super T> comparator) {
    return new TreeSet<T>(comparator);
  }

  @NotNull
  public static <T> Stack<T> newStack() {
    return new Stack<T>();
  }

  @NotNull
  public static <T> Stack<T> newStack(@NotNull Collection<T> elements) {
    return new Stack<T>(elements);
  }

  @NotNull
  public static <T> Stack<T> newStack(@NotNull T... initial) {
    return new Stack<T>(Arrays.asList(initial));
  }

  /**
   * A variant of {@link java.util.Collections#emptyList()},
   * except that {@link #toArray()} here does not create garbage <code>new Object[0]</code> constantly.
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
    public <T> T[] toArray(@NotNull T[] a) {
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
  }

  @NotNull
  public static <T> List<T> emptyList() {
    //noinspection unchecked
    return (List<T>)EmptyList.INSTANCE;
  }

  @NotNull
  public static <T> CopyOnWriteArrayList<T> createEmptyCOWList() {
    // does not create garbage new Object[0]
    return new CopyOnWriteArrayList<T>(ContainerUtilRt.<T>emptyList());
  }

  public static <T> void addIfNotNull(@Nullable T element, @NotNull Collection<T> result) {
    if (element != null) {
      result.add(element);
    }
  }

  public static <T> void addIfNotNull(@NotNull Collection<T> result, @Nullable T element) {
    if (element != null) {
      result.add(element);
    }
  }

  /**
   * @return read-only list consisting of the elements from array converted by mapper
   */
  @NotNull
  public static <T, V> List<V> map2List(@NotNull T[] array, @NotNull Function<T, V> mapper) {
    return map2List(Arrays.asList(array), mapper);
  }

  /**
   * @return read-only list consisting of the elements from collection converted by mapper
   */
  @NotNull
  public static <T, V> List<V> map2List(@NotNull Collection<? extends T> collection, @NotNull Function<T, V> mapper) {
    if (collection.isEmpty()) return emptyList();
    List<V> list = new ArrayList<V>(collection.size());
    for (final T t : collection) {
      list.add(mapper.fun(t));
    }
    return list;
  }

  /**
   * @return read-only set consisting of the elements from collection converted by mapper
   */
  @NotNull
  public static <T, V> Set<V> map2Set(@NotNull T[] collection, @NotNull Function<T, V> mapper) {
    return map2Set(Arrays.asList(collection), mapper);
  }

  /**
   * @return read-only set consisting of the elements from collection converted by mapper
   */
  @NotNull
  public static <T, V> Set<V> map2Set(@NotNull Collection<? extends T> collection, @NotNull Function<T, V> mapper) {
    if (collection.isEmpty()) return Collections.emptySet();
    Set <V> set = new HashSet<V>(collection.size());
    for (final T t : collection) {
      set.add(mapper.fun(t));
    }
    return set;
  }

  @NotNull
  public static <T> T[] toArray(@NotNull List<T> collection, @NotNull T[] array) {
    final int length = array.length;
    if (length < ARRAY_COPY_THRESHOLD && array.length >= collection.size()) {
      for (int i = 0; i < collection.size(); i++) {
        array[i] = collection.get(i);
      }
      return array;
    }
    return collection.toArray(array);
  }

  /**
   * This is a replacement for {@link Collection#toArray(Object[])}. For small collections it is faster to stay at java level and refrain
   * from calling JNI {@link System#arraycopy(Object, int, Object, int, int)}
   */
  @NotNull
  public static <T> T[] toArray(@NotNull Collection<T> c, @NotNull T[] sample) {
    final int size = c.size();
    if (size == sample.length && size < ARRAY_COPY_THRESHOLD) {
      int i = 0;
      for (T t : c) {
        sample[i++] = t;
      }
      return sample;
    }

    return c.toArray(sample);
  }
}
