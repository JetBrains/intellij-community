/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.Stack;
import java.util.HashSet;

/**
 * @author peter
 */
public class CollectionFactory {
  private CollectionFactory() {
  }

  @NotNull
  public static <T> HashSet<T> hashSet() {
    return new HashSet<T>();
  }

  @NotNull
  public static <T> HashSet<T> hashSet(@NotNull Collection<T> elements) {
    return new HashSet<T>(elements);
  }

  @NotNull
  public static <T> HashSet<T> hashSet(@NotNull T... elements) {
    return hashSet(Arrays.asList(elements));
  }

  @NotNull
  public static <T> LinkedHashSet<T> linkedHashSet() {
    return new LinkedHashSet<T>();
  }

  @NotNull
  public static <T> LinkedHashSet<T> linkedHashSet(@NotNull Collection<T> elements) {
    return new LinkedHashSet<T>(elements);
  }

  @NotNull
  public static <T> LinkedHashSet<T> linkedHashSet(@NotNull T... elements) {
    return linkedHashSet(Arrays.asList(elements));
  }

  @NotNull
  public static <T> THashSet<T> troveSet(@NotNull T... elements) {
    return troveSet(Arrays.asList(elements));
  }

  @NotNull
  public static <T> THashSet<T> troveSet(@NotNull Collection<T> elements) {
    return new THashSet<T>(elements);
  }

  @NotNull
  public static <T> TreeSet<T> treeSet() {
    return new TreeSet<T>();
  }

  @NotNull
  public static <T> TreeSet<T> treeSet(@NotNull Collection<T> elements) {
    return new TreeSet<T>(elements);
  }

  @NotNull
  public static <T> TreeSet<T> treeSet(@NotNull T... elements) {
    return treeSet(Arrays.asList(elements));
  }

  @NotNull
  public static <T> TreeSet<T> treeSet(@NotNull Comparator<? super T> comparator) {
    return new TreeSet<T>(comparator);
  }

  @NotNull
  public static <T> Set<T> unmodifiableHashSet(@NotNull T... elements) {
    return unmodifiableHashSet(Arrays.asList(elements));
  }

  @NotNull
  public static <T> Set<T> unmodifiableHashSet(@NotNull Collection<T> elements) {
    return Collections.unmodifiableSet(hashSet(elements));
  }

  @NotNull
  public static <K, V> HashMap<K, V> hashMap() {
    return new HashMap<K, V>();
  }

  @NotNull
  public static <K, V> HashMap<K, V> hashMap(@NotNull Map<K, V> map) {
    return new HashMap<K, V>(map);
  }

  @NotNull
  public static <K, V> TreeMap<K, V> treeMap() {
    return new TreeMap<K, V>();
  }

  @NotNull
  public static <K, V> THashMap<K, V> troveMap() {
    return new THashMap<K, V>();
  }

  @NotNull
  public static <K, V> IdentityHashMap<K, V> identityHashMap() {
    return new IdentityHashMap<K, V>();
  }

  @NotNull
  public static <K> THashSet<K> identityTroveSet() {
    //noinspection unchecked
    return new THashSet<K>(TObjectHashingStrategy.IDENTITY);
  }

  @NotNull
  public static <K> THashSet<K> identityTroveSet(int initialCapacity) {
    //noinspection unchecked
    return new THashSet<K>(initialCapacity, TObjectHashingStrategy.IDENTITY);
  }

  @NotNull
  public static <K, V> LinkedHashMap<K, V> linkedHashMap() {
    return new LinkedHashMap<K, V>();
  }

  @NotNull
  public static <K extends Enum<K>, V> EnumMap<K, V> enumMap(@NotNull Class<K> klass) {
    return new EnumMap<K, V>(klass);
  }

  @NotNull
  public static <K extends Enum<K>, V> EnumMap<K, V> enumMap(@NotNull Map<K, V> map) {
    return new EnumMap<K, V>(map);
  }

  @NotNull
  public static <K, V> Map<K, V> hashMap(@NotNull final List<K> keys, @NotNull final List<V> values) {
    if (keys.size() != values.size()) {
      throw new IllegalArgumentException(keys + " should have same length as " + values);
    }

    final HashMap<K, V> map = new HashMap<K, V>();
    for (int i = 0; i < keys.size(); ++i) {
      map.put(keys.get(i), values.get(i));
    }
    return map;
  }

  @NotNull
  public static <T> ArrayList<T> arrayList() {
    return new ArrayList<T>();
  }

  @NotNull
  public static <T> ArrayList<T> arrayList(int initialCapacity) {
    return new ArrayList<T>(initialCapacity);
  }

  @NotNull
  public static <T> ArrayList<T> arrayList(@NotNull Collection<T> elements) {
    return new ArrayList<T>(elements);
  }

  @NotNull
  public static <T> ArrayList<T> arrayList(T... elements) {
    return arrayList(Arrays.asList(elements));
  }

  @NotNull
  public static <T> ArrayList<T> arrayList(@NotNull Iterable<T> elements) {
    if (elements instanceof Collection) {
      return arrayList((Collection<T>)elements);
    }

    return copy(elements, CollectionFactory.<T>arrayList());
  }

  @NotNull
  public static <T> List<T> arrayList(@NotNull final T[] elements, final int start, final int end) {
    if (start < 0 || start > end || end > elements.length) throw new IllegalArgumentException("start:" + start + " end:" + end + " length:" + elements.length);

    return new AbstractList<T>() {
      private final int size = end - start;

      @Override
      public T get(final int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException("index:" + index + " size:" + size);
        return elements[start + index];
      }

      @Override
      public int size() {
        return size;
      }
    };
  }

  @NotNull
  public static <T> LinkedList<T> linkedList() {
    return new LinkedList<T>();
  }

  @NotNull
  public static <T> LinkedList<T> linkedList(@NotNull Collection<T> elements) {
    return new LinkedList<T>(elements);
  }

  @NotNull
  public static <T> LinkedList<T> linkedList(T... elements) {
    return linkedList(Arrays.asList(elements));
  }

  @NotNull
  public static <T> LinkedList<T> linkedList(@NotNull Iterable<T> elements) {
    if (elements instanceof Collection) {
      return linkedList((Collection<T>)elements);
    }

    return copy(elements, CollectionFactory.<T>linkedList());
  }

  @NotNull
  private static <P, C extends Collection<P>> C copy(@NotNull Iterable<P> source, @NotNull C target) {
    for (P e : source) {
      target.add(e);
    }
    return target;
  }

  @NotNull
  public static <T> Stack<T> stack() {
    return new Stack<T>();
  }

  @NotNull
  public static <T> T[] ar(@NotNull T... elements) {
    return elements;
  }
}
