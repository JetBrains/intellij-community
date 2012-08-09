/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stripped-down version of {@code com.intellij.util.containers.ContainerUtil}.
 * Intended to use by external (out-of-IDE-process) runners and helpers so it should not contain any library dependencies.
 *
 * @since 12.0
 */
@SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
public class ContainerUtilRt {
  private static final int ARRAY_COPY_THRESHOLD = 20;

  @NotNull
  public static <K, V> HashMap<K, V> newHashMap() {
    return new HashMap<K, V>();
  }

  @NotNull
  public static <K, V> HashMap<K, V> newHashMap(Map<K, V> map) {
    return new HashMap<K, V>(map);
  }

  @NotNull
  public static <K extends Comparable, V> TreeMap<K, V> newTreeMap() {
    return new TreeMap<K, V>();
  }

  @NotNull
  public static <K, V> LinkedHashMap<K, V> newLinkedHashMap() {
    return new LinkedHashMap<K, V>();
  }

  @NotNull
  public static <T> LinkedList<T> newLinkedList() {
    return new LinkedList<T>();
  }

  @NotNull
  public static <T> ArrayList<T> newArrayList() {
    return new ArrayList<T>();
  }

  @NotNull
  public static <E> ArrayList<E> newArrayList(E... array) {
    ArrayList<E> list = new ArrayList<E>(computeArrayListCapacity(array.length));
    Collections.addAll(list, array);
    return list;
  }

  @NotNull
  public static <E> ArrayList<E> newArrayList(Iterable<? extends E> iterable) {
    ArrayList<E> list = newArrayList();
    for (E anIterable : iterable) {
      list.add(anIterable);
    }
    return list;
  }

  @NotNull
  public static <T> HashSet<T> newHashSet() {
    return new HashSet<T>();
  }

  @NotNull
  public static <T> HashSet<T> newHashSet(T... elements) {
    HashSet<T> set = newHashSet();
    Collections.addAll(set, elements);
    return set;
  }

  @NotNull
  public static <T> HashSet<T> newHashSet(Iterable<? extends T> iterable) {
    return newHashSet(iterable.iterator());
  }

  @NotNull
  public static <T> HashSet<T> newHashSet(Iterator<? extends T> iterator) {
    HashSet<T> set = newHashSet();
    while (iterator.hasNext()) set.add(iterator.next());
    return set;
  }

  @NotNull
  public static <T> TreeSet<T> newTreeSet() {
    return new TreeSet<T>();
  }

  @NotNull
  public static <T> TreeSet<T> newTreeSet(Comparator<? super T> comparator) {
    return new TreeSet<T>(comparator);
  }

  @NotNull
  public static <T> ArrayList<T> newArrayListWithExpectedSize(int size) {
    return new ArrayList<T>(size);
  }

  @NotNull
  public static <T> ArrayList<T> newArrayListWithCapacity(int size) {
    return new ArrayList<T>(computeArrayListCapacity(size));
  }

  private static int computeArrayListCapacity(int size) {
    return 5 + size + size / 5;
  }

  /**
   * Optimized toArray() as opposed to the {@link java.util.Collections#emptyList()}.
   */
  private static class EmptyList extends AbstractList<Object> implements RandomAccess {
    private static final EmptyList INSTANCE = new EmptyList();

    public int size() {
      return 0;
    }

    public boolean contains(Object obj) {
      return false;
    }

    public Object get(int index) {
      throw new IndexOutOfBoundsException("Index: " + index);
    }

    @Override
    public Object[] toArray() {
      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public <T> T[] toArray(T[] a) {
      return a;
    }
  }

  @NotNull
  public static <T> List<T> emptyList() {
    @SuppressWarnings({"unchecked", "UnnecessaryLocalVariable"}) final List<T> list = (List<T>)EmptyList.INSTANCE;
    return list;
  }

  @NotNull
  public static <T> CopyOnWriteArrayList<T> createEmptyCOWList() {
    // does not create garbage new Object[0]
    return new CopyOnWriteArrayList<T>(ContainerUtilRt.<T>emptyList());
  }

  public static <T> void addIfNotNull(final T element, @NotNull Collection<T> result) {
    if (element != null) {
      result.add(element);
    }
  }

  public static <T> void addIfNotNull(@NotNull Collection<T> result, @Nullable final T element) {
    if (element != null) {
      result.add(element);
    }
  }

  @NotNull
  public static <T, V> List<V> map2List(@NotNull T[] array, @NotNull Function<T, V> mapper) {
    return map2List(Arrays.asList(array), mapper);
  }

  @NotNull
  public static <T, V> List<V> map2List(@NotNull Collection<? extends T> collection, @NotNull Function<T, V> mapper) {
    final ArrayList<V> list = new ArrayList<V>(collection.size());
    for (final T t : collection) {
      list.add(mapper.fun(t));
    }
    return list;
  }

  @NotNull
  public static <T, V> Set<V> map2Set(@NotNull T[] collection, @NotNull Function<T, V> mapper) {
    return map2Set(Arrays.asList(collection), mapper);
  }

  @NotNull
  public static <T, V> Set<V> map2Set(@NotNull Collection<? extends T> collection, @NotNull Function<T, V> mapper) {
    final HashSet<V> set = new HashSet<V>(collection.size());
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
    else {
      return collection.toArray(array);
    }
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
