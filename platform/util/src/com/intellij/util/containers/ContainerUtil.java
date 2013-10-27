/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Pair;
import com.intellij.util.*;
import gnu.trove.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

@SuppressWarnings({"UtilityClassWithoutPrivateConstructor", "MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
public class ContainerUtil extends ContainerUtilRt {
  private static final int INSERTION_SORT_THRESHOLD = 10;

  @NotNull
  public static <T> T[] ar(@NotNull T... elements) {
    return elements;
  }

  @NotNull
  public static <K, V> HashMap<K, V> newHashMap() {
    return ContainerUtilRt.newHashMap();
  }

  @NotNull
  public static <K, V> HashMap<K, V> newHashMap(@NotNull Map<K, V> map) {
    return ContainerUtilRt.newHashMap(map);
  }

  @NotNull
  public static <K, V> Map<K, V> newHashMap(Pair<K, V> first, Pair<K, V>... entries) {
    return ContainerUtilRt.newHashMap(first, entries);
  }

  @NotNull
  public static <K, V> Map<K, V> newHashMap(@NotNull List<K> keys, @NotNull List<V> values) {
    return ContainerUtilRt.newHashMap(keys, values);
  }

  @NotNull
  public static <K extends Comparable, V> TreeMap<K, V> newTreeMap() {
    return ContainerUtilRt.newTreeMap();
  }

  @NotNull
  public static <K extends Comparable, V> TreeMap<K, V> newTreeMap(@NotNull Map<K, V> map) {
    return ContainerUtilRt.newTreeMap(map);
  }

  @NotNull
  public static <K, V> LinkedHashMap<K, V> newLinkedHashMap() {
    return ContainerUtilRt.newLinkedHashMap();
  }

  @NotNull
  public static <K, V> LinkedHashMap<K, V> newLinkedHashMap(@NotNull Map<K, V> map) {
    return ContainerUtilRt.newLinkedHashMap(map);
  }

  @NotNull
  public static <K, V> THashMap<K, V> newTroveMap() {
    return new THashMap<K, V>();
  }

  @NotNull
  public static <K, V> THashMap<K, V> newTroveMap(@NotNull TObjectHashingStrategy<K> strategy) {
    return new THashMap<K, V>(strategy);
  }

  @SuppressWarnings("unchecked")
  @NotNull
  public static <T> TObjectHashingStrategy<T> canonicalStrategy() {
    return TObjectHashingStrategy.CANONICAL;
  }

  @SuppressWarnings("unchecked")
  @NotNull
  public static <T> TObjectHashingStrategy<T> identityStrategy() {
    return TObjectHashingStrategy.IDENTITY;
  }

  @NotNull
  public static <K, V> IdentityHashMap<K, V> newIdentityHashMap() {
    return new IdentityHashMap<K, V>();
  }

  @NotNull
  public static <T> LinkedList<T> newLinkedList() {
    return ContainerUtilRt.newLinkedList();
  }

  @NotNull
  public static <T> LinkedList<T> newLinkedList(@NotNull T... elements) {
    return ContainerUtilRt.newLinkedList(elements);
  }

  @NotNull
  public static <T> LinkedList<T> newLinkedList(@NotNull Iterable<? extends T> elements) {
    return ContainerUtilRt.newLinkedList(elements);
  }

  @NotNull
  public static <T> ArrayList<T> newArrayList() {
    return ContainerUtilRt.newArrayList();
  }

  @NotNull
  public static <E> ArrayList<E> newArrayList(@NotNull E... array) {
    return ContainerUtilRt.newArrayList(array);
  }

  @NotNull
  public static <E> ArrayList<E> newArrayList(@NotNull Iterable<? extends E> iterable) {
    return ContainerUtilRt.newArrayList(iterable);
  }

  @NotNull
  public static <T> ArrayList<T> newArrayListWithExpectedSize(int size) {
    return ContainerUtilRt.newArrayListWithExpectedSize(size);
  }

  @NotNull
  public static <T> ArrayList<T> newArrayListWithCapacity(int size) {
    return ContainerUtilRt.newArrayListWithCapacity(size);
  }

  @NotNull
  public static <T> List<T> newArrayList(@NotNull final T[] elements, final int start, final int end) {
    if (start < 0 || start > end || end > elements.length) {
      throw new IllegalArgumentException("start:" + start + " end:" + end + " length:" + elements.length);
    }

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
  public static <T> List<T> newSmartList(T element) {
    return new SmartList<T>(element);
  }

  @NotNull
  public static <T> List<T> newSmartList(@NotNull T... elements) {
    return new SmartList<T>(elements);
  }

  @NotNull
  public static <T> HashSet<T> newHashSet() {
    return ContainerUtilRt.newHashSet();
  }

  @NotNull
  public static <T> HashSet<T> newHashSet(@NotNull T... elements) {
    return ContainerUtilRt.newHashSet(elements);
  }

  @NotNull
  public static <T> HashSet<T> newHashSet(@NotNull Iterable<? extends T> iterable) {
    return ContainerUtilRt.newHashSet(iterable);
  }

  @NotNull
  public static <T> HashSet<T> newHashSet(@NotNull Iterator<? extends T> iterator) {
    return ContainerUtilRt.newHashSet(iterator);
  }

  @NotNull
  public static <T> LinkedHashSet<T> newLinkedHashSet() {
    return ContainerUtilRt.newLinkedHashSet();
  }

  @NotNull
  public static <T> LinkedHashSet<T> newLinkedHashSet(@NotNull Iterable<? extends T> elements) {
    return ContainerUtilRt.newLinkedHashSet(elements);
  }

  @NotNull
  public static <T> LinkedHashSet<T> newLinkedHashSet(@NotNull T... elements) {
    return ContainerUtilRt.newLinkedHashSet(elements);
  }

  @NotNull
  public static <T> THashSet<T> newTroveSet() {
    return new THashSet<T>();
  }

  @NotNull
  public static <T> THashSet<T> newTroveSet(@NotNull TObjectHashingStrategy<T> strategy) {
    return new THashSet<T>(strategy);
  }

  @NotNull
  public static <T> THashSet<T> newTroveSet(@NotNull T... elements) {
    return newTroveSet(Arrays.asList(elements));
  }

  @NotNull
  public static <T> THashSet<T> newTroveSet(@NotNull TObjectHashingStrategy<T> strategy, @NotNull T... elements) {
    return new THashSet<T>(Arrays.asList(elements), strategy);
  }

  @NotNull
  public static <T> THashSet<T> newTroveSet(@NotNull TObjectHashingStrategy<T> strategy, @NotNull Collection<T> elements) {
    return new THashSet<T>(elements, strategy);
  }

  @NotNull
  public static <T> THashSet<T> newTroveSet(@NotNull Collection<T> elements) {
    return new THashSet<T>(elements);
  }

  @NotNull
  public static <K> THashSet<K> newIdentityTroveSet() {
    return new THashSet<K>(ContainerUtil.<K>identityStrategy());
  }

  @NotNull
  public static <K> THashSet<K> newIdentityTroveSet(int initialCapacity) {
    return new THashSet<K>(initialCapacity, ContainerUtil.<K>identityStrategy());
  }
  @NotNull
  public static <K> THashSet<K> newIdentityTroveSet(@NotNull Collection<K> collection) {
    return new THashSet<K>(collection, ContainerUtil.<K>identityStrategy());
  }

  @NotNull
  public static <K,V> THashMap<K,V> newIdentityTroveMap() {
    return new THashMap<K,V>(ContainerUtil.<K>identityStrategy());
  }

  @NotNull
  public static <T> TreeSet<T> newTreeSet() {
    return ContainerUtilRt.newTreeSet();
  }

  @NotNull
  public static <T> TreeSet<T> newTreeSet(@NotNull Iterable<? extends T> elements) {
    return ContainerUtilRt.newTreeSet(elements);
  }

  @NotNull
  public static <T> TreeSet<T> newTreeSet(@NotNull T... elements) {
    return ContainerUtilRt.newTreeSet(elements);
  }

  @NotNull
  public static <T> TreeSet<T> newTreeSet(@Nullable Comparator<? super T> comparator) {
    return ContainerUtilRt.newTreeSet(comparator);
  }

  @NotNull
  public static <K, V> ConcurrentMap<K, V> newConcurrentMap() {
    return new ConcurrentHashMap<K, V>();
  }

  @NotNull
  public static <E> List<E> reverse(@NotNull final List<E> elements) {
    return new AbstractList<E>() {
      @Override
      public E get(int index) {
        return elements.get(elements.size() - 1 - index);
      }

      @Override
      public int size() {
        return elements.size();
      }
    };
  }

  @NotNull
  public static <K, V> Map<K, V> union(@NotNull Map<? extends K, ? extends V> map, @NotNull Map<? extends K, ? extends V> map2) {
    THashMap<K, V> result = new THashMap<K, V>(map.size() + map2.size());
    result.putAll(map);
    result.putAll(map2);
    return result;
  }

  @NotNull
  public static <T> Set<T> union(@NotNull Set<T> set, @NotNull Set<T> set2) {
    THashSet<T> result = new THashSet<T>(set.size() + set2.size());
    result.addAll(set);
    result.addAll(set2);
    return result;
  }

  @NotNull
  public static <E> Set<E> immutableSet(@NotNull E ... elements) {
    return Collections.unmodifiableSet(new THashSet<E>(Arrays.asList(elements)));
  }

  @NotNull
  public static <E> ImmutableList<E> immutableList(@NotNull E ... array) {
    return new ImmutableListBackedByArray<E>(array);
  }

  @NotNull
  public static <E> ImmutableList<E> immutableList(@NotNull List<E> list) {
    return new ImmutableListBackedByList<E>(list);
  }

  @NotNull
  public static <K, V> ImmutableMapBuilder<K, V> immutableMapBuilder() {
    return new ImmutableMapBuilder<K, V>();
  }

  public static class ImmutableMapBuilder<K, V> {
    private final Map<K, V> myMap = new THashMap<K, V>();

    public ImmutableMapBuilder<K, V> put(K key, V value) {
      myMap.put(key, value);
      return this;
    }

    public Map<K, V> build() {
      return Collections.unmodifiableMap(myMap);
    }
  }

  private static class ImmutableListBackedByList<E> extends ImmutableList<E> {
    private final List<E> myStore;

    private ImmutableListBackedByList(@NotNull List<E> list) {
      myStore = list;
    }

    @Override
    public E get(int index) {
      return myStore.get(index);
    }

    @Override
    public int size() {
      return myStore.size();
    }
  }

  private static class ImmutableListBackedByArray<E> extends ImmutableList<E> {
    private final E[] myStore;

    private ImmutableListBackedByArray(@NotNull E[] array) {
      myStore = array;
    }

    @Override
    public E get(int index) {
      return myStore[index];
    }

    @Override
    public int size() {
      return myStore.length;
    }
  }

  @NotNull
  public static <K, V> Map<K, V> intersection(@NotNull Map<K, V> map1, @NotNull Map<K, V> map2) {
    final Map<K, V> res = newHashMap();
    final Set<K> keys = newHashSet();
    keys.addAll(map1.keySet());
    keys.addAll(map2.keySet());
    for (K k : keys) {
      V v1 = map1.get(k);
      V v2 = map2.get(k);
      if (v1 == v2 || v1 != null && v1.equals(v2)) {
        res.put(k, v1);
      }
    }
    return res;
  }

  @NotNull
  public static <K, V> Map<K,Pair<V,V>> diff(@NotNull Map<K, V> map1, @NotNull Map<K, V> map2) {
    final Map<K, Pair<V,V>> res = newHashMap();
    final Set<K> keys = newHashSet();
    keys.addAll(map1.keySet());
    keys.addAll(map2.keySet());
    for (K k : keys) {
      V v1 = map1.get(k);
      V v2 = map2.get(k);
      if (!(v1 == v2 || v1 != null && v1.equals(v2))) {
        res.put(k, Pair.create(v1, v2));
      }
    }
    return res;
  }

  public static <T> boolean processSortedListsInOrder(@NotNull List<T> list1,
                                                      @NotNull List<T> list2,
                                                      @NotNull Comparator<? super T> comparator,
                                                      boolean mergeEqualItems,
                                                      @NotNull Processor<T> processor) {
    int index1 = 0;
    int index2 = 0;
    while (index1 < list1.size() || index2 < list2.size()) {
      T e;
      if (index1 >= list1.size()) {
        e = list2.get(index2++);
      }
      else if (index2 >= list2.size()) {
        e = list1.get(index1++);
      }
      else {
        T element1 = list1.get(index1);
        T element2 = list2.get(index2);
        int c = comparator.compare(element1, element2);
        if (c <= 0) {
          e = element1;
          index1++;
        }
        else {
          e = element2;
          index2++;
        }
        if (c == 0 && !mergeEqualItems) {
          if (!processor.process(e)) return false;
          index2++;
          e = element2;
        }
      }
      if (!processor.process(e)) return false;
    }

    return true;
  }

  @NotNull
  public static <T> List<T> mergeSortedLists(@NotNull List<T> list1,
                                             @NotNull List<T> list2,
                                             @NotNull Comparator<? super T> comparator,
                                             boolean mergeEqualItems) {
    final List<T> result = new ArrayList<T>(list1.size() + list2.size());
    processSortedListsInOrder(list1, list2, comparator, mergeEqualItems, new Processor<T>() {
      @Override
      public boolean process(T t) {
        result.add(t);
        return true;
      }
    });
    return result;
  }

  @NotNull
  public static <T> List<T> mergeSortedArrays(@NotNull T[] list1, @NotNull T[] list2, @NotNull Comparator<? super T> comparator, boolean mergeEqualItems, @Nullable Processor<? super T> filter) {
    int index1 = 0;
    int index2 = 0;
    List<T> result = new ArrayList<T>(list1.length + list2.length);

    while (index1 < list1.length || index2 < list2.length) {
      if (index1 >= list1.length) {
        T t = list2[index2++];
        if (filter != null && !filter.process(t)) continue;
        result.add(t);
      }
      else if (index2 >= list2.length) {
        T t = list1[index1++];
        if (filter != null && !filter.process(t)) continue;
        result.add(t);
      }
      else {
        T element1 = list1[index1];
        if (filter != null && !filter.process(element1)) {
          index1++;
          continue;
        }
        T element2 = list2[index2];
        if (filter != null && !filter.process(element2)) {
          index2++;
          continue;
        }
        int c = comparator.compare(element1, element2);
        if (c < 0) {
          result.add(element1);
          index1++;
        }
        else if (c > 0) {
          result.add(element2);
          index2++;
        }
        else {
          result.add(element1);
          if (!mergeEqualItems) {
            result.add(element2);
          }
          index1++;
          index2++;
        }
      }
    }

    return result;
  }

  @NotNull
  public static <T> List<T> subList(@NotNull List<T> list, int from) {
    return list.subList(from, list.size());
  }

  public static <T> void addAll(@NotNull Collection<T> collection, @NotNull Iterable<? extends T> appendix) {
    addAll(collection, appendix.iterator());
  }

  public static <T> void addAll(@NotNull Collection<T> collection, @NotNull Iterator<? extends T> iterator) {
    while (iterator.hasNext()) {
      T o = iterator.next();
      collection.add(o);
    }
  }

  /**
   * Adds all not-null elements from the {@code elements}, ignoring nulls
   */
  public static <T> void addAllNotNull(@NotNull Collection<T> collection, @NotNull Iterable<? extends T> elements) {
    addAllNotNull(collection, elements.iterator());
  }

  /**
   * Adds all not-null elements from the {@code elements}, ignoring nulls
   */
  public static <T> void addAllNotNull(@NotNull Collection<T> collection, @NotNull Iterator<? extends T> elements) {
    while (elements.hasNext()) {
      T o = elements.next();
      if (o != null) {
        collection.add(o);
      }
    }
  }

  @NotNull
  public static <T> List<T> collect(@NotNull Iterator<T> iterator) {
    if (!iterator.hasNext()) return emptyList();
    List<T> list = new ArrayList<T>();
    addAll(list, iterator);
    return list;
  }

  @NotNull
  public static <T> Set<T> collectSet(@NotNull Iterator<T> iterator) {
    if (!iterator.hasNext()) return Collections.emptySet();
    Set<T> hashSet = newHashSet();
    addAll(hashSet, iterator);
    return hashSet;
  }

  @NotNull
  public static <K, V> Map<K, V> newMapFromKeys(@NotNull Iterator<K> keys, @NotNull Convertor<K, V> valueConvertor) {
    Map<K, V> map = newHashMap();
    while (keys.hasNext()) {
      K key = keys.next();
      map.put(key, valueConvertor.convert(key));
    }
    return map;
  }

  @NotNull
  public static <K, V> Map<K, V> newMapFromValues(@NotNull Iterator<V> values, @NotNull Convertor<V, K> keyConvertor) {
    Map<K, V> map = newHashMap();
    while (values.hasNext()) {
      V value = values.next();
      map.put(keyConvertor.convert(value), value);
    }
    return map;
  }

  /** @deprecated use {@linkplain #newMapFromValues(java.util.Iterator, Convertor)} (to remove in IDEA 13) */
  @NotNull
  public static <K, V> com.intellij.util.containers.HashMap<K, V> assignKeys(@NotNull Iterator<V> iterator, @NotNull Convertor<V, K> keyConvertor) {
    com.intellij.util.containers.HashMap<K, V> hashMap = new com.intellij.util.containers.HashMap<K, V>();
    while (iterator.hasNext()) {
      V value = iterator.next();
      hashMap.put(keyConvertor.convert(value), value);
    }
    return hashMap;
  }

  /** @deprecated use {@linkplain #newMapFromKeys(java.util.Iterator, Convertor)} (to remove in IDEA 13) */
  @NotNull
  public static <K, V> com.intellij.util.containers.HashMap<K, V> assignValues(@NotNull Iterator<K> iterator, @NotNull Convertor<K, V> valueConvertor) {
    com.intellij.util.containers.HashMap<K, V> hashMap = new com.intellij.util.containers.HashMap<K, V>();
    while (iterator.hasNext()) {
      K key = iterator.next();
      hashMap.put(key, valueConvertor.convert(key));
    }
    return hashMap;
  }

  @NotNull
  public static <K, V> Map<K, Set<V>> classify(@NotNull Iterator<V> iterator, @NotNull Convertor<V, K> keyConvertor) {
    Map<K, Set<V>> hashMap = new LinkedHashMap<K, Set<V>>();
    while (iterator.hasNext()) {
      V value = iterator.next();
      final K key = keyConvertor.convert(value);
      Set<V> set = hashMap.get(key);
      if (set == null) {
        hashMap.put(key, set = new LinkedHashSet<V>()); // ordered set!!
      }
      set.add(value);
    }
    return hashMap;
  }

  @NotNull
  public static <T> Iterator<T> emptyIterator() {
    return EmptyIterator.getInstance();
  }

  @NotNull
  public static <T> Iterable<T> emptyIterable() {
    return EmptyIterable.getInstance();
  }

  @Nullable
  public static <T> T find(@NotNull T[] array, @NotNull Condition<T> condition) {
    for (T element : array) {
      if (condition.value(element)) return element;
    }
    return null;
  }

  public static <T> boolean process(@NotNull Iterable<? extends T> iterable, @NotNull Processor<T> processor) {
    for (final T t : iterable) {
      if (!processor.process(t)) {
        return false;
      }
    }
    return true;
  }

  public static <T> boolean process(@NotNull List<? extends T> list, @NotNull Processor<T> processor) {
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, size = list.size(); i < size; i++) {
      T t = list.get(i);
      if (!processor.process(t)) {
        return false;
      }
    }
    return true;
  }

  public static <T> boolean process(@NotNull T[] iterable, @NotNull Processor<? super T> processor) {
    for (final T t : iterable) {
      if (!processor.process(t)) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  public static <T, V extends T> V find(@NotNull Iterable<V> iterable, @NotNull Condition<T> condition) {
    return find(iterable.iterator(), condition);
  }

  @Nullable
  public static <T> T find(@NotNull Iterable<? extends T> iterable, final T equalTo) {
    return find(iterable, new Condition<T>() {
      @Override
      public boolean value(final T object) {
        return equalTo == object || equalTo.equals(object);
      }
    });
  }

  @Nullable
  public static <T, V extends T> V find(@NotNull Iterator<V> iterator, @NotNull Condition<T> condition) {
    while (iterator.hasNext()) {
      V value = iterator.next();
      if (condition.value(value)) return value;
    }
    return null;
  }

  @NotNull
  public static <T, KEY, VALUE> Map<KEY, VALUE> map2Map(@NotNull T[] collection, @NotNull Function<T, Pair<KEY, VALUE>> mapper) {
    return map2Map(Arrays.asList(collection), mapper);
  }

  @NotNull
  public static <T, KEY, VALUE> Map<KEY, VALUE> map2Map(@NotNull Collection<? extends T> collection,
                                                        @NotNull Function<T, Pair<KEY, VALUE>> mapper) {
    final Map<KEY, VALUE> set = new THashMap<KEY, VALUE>(collection.size());
    for (T t : collection) {
      Pair<KEY, VALUE> pair = mapper.fun(t);
      set.put(pair.first, pair.second);
    }
    return set;
  }

  @NotNull
  public static <KEY, VALUE> Map<KEY, VALUE> map2Map(@NotNull Collection<Pair<KEY, VALUE>> collection) {
    final Map<KEY, VALUE> result = new THashMap<KEY, VALUE>(collection.size());
    for (Pair<KEY, VALUE> pair : collection) {
      result.put(pair.first, pair.second);
    }
    return result;
  }

  @NotNull
  public static <T> Object[] map2Array(@NotNull T[] array, @NotNull Function<T, Object> mapper) {
    return map2Array(array, Object.class, mapper);
  }

  @NotNull
  public static <T> Object[] map2Array(@NotNull Collection<T> array, @NotNull Function<T, Object> mapper) {
    return map2Array(array, Object.class, mapper);
  }

  @NotNull
  public static <T, V> V[] map2Array(@NotNull T[] array, @NotNull Class<? extends V> aClass, @NotNull Function<T, V> mapper) {
    return map2Array(Arrays.asList(array), aClass, mapper);
  }

  @NotNull
  public static <T, V> V[] map2Array(@NotNull Collection<? extends T> collection, @NotNull Class<? extends V> aClass, @NotNull Function<T, V> mapper) {
    final List<V> list = map2List(collection, mapper);
    @SuppressWarnings("unchecked") V[] array = (V[])Array.newInstance(aClass, list.size());
    return list.toArray(array);
  }

  @NotNull
  public static <T, V> V[] map2Array(@NotNull Collection<? extends T> collection, @NotNull V[] to, @NotNull Function<T, V> mapper) {
    return map2List(collection, mapper).toArray(to);
  }

  @NotNull
  public static <T> List<T> filter(@NotNull T[] collection, @NotNull Condition<? super T> condition) {
    return findAll(collection, condition);
  }

  @NotNull
  public static int[] filter(@NotNull int[] collection, @NotNull TIntProcedure condition) {
    TIntArrayList result = new TIntArrayList();
    for (int t : collection) {
      if (condition.execute(t)) {
        result.add(t);
      }
    }
    return result.isEmpty() ? ArrayUtil.EMPTY_INT_ARRAY : result.toNativeArray();
  }

  @NotNull
  public static <T> List<T> filter(@NotNull Condition<? super T> condition, @NotNull T... collection) {
    return findAll(collection, condition);
  }

  @NotNull
  public static <T> List<T> findAll(@NotNull T[] collection, @NotNull Condition<? super T> condition) {
    final List<T> result = new SmartList<T>();
    for (T t : collection) {
      if (condition.value(t)) {
        result.add(t);
      }
    }
    return result;
  }

  @NotNull
  public static <T> List<T> filter(@NotNull Collection<? extends T> collection, @NotNull Condition<? super T> condition) {
    return findAll(collection, condition);
  }

  @NotNull
  public static <T> List<T> findAll(@NotNull Collection<? extends T> collection, @NotNull Condition<? super T> condition) {
    final List<T> result = new SmartList<T>();
    for (final T t : collection) {
      if (condition.value(t)) {
        result.add(t);
      }
    }
    return result;
  }

  @NotNull
  public static <T> List<T> skipNulls(@NotNull Collection<? extends T> collection) {
    return findAll(collection, Condition.NOT_NULL);
  }

  @NotNull
  public static <T, V> List<V> findAll(@NotNull T[] collection, @NotNull Class<V> instanceOf) {
    return findAll(Arrays.asList(collection), instanceOf);
  }

  @NotNull
  public static <T, V> V[] findAllAsArray(@NotNull T[] collection, @NotNull Class<V> instanceOf) {
    List<V> list = findAll(Arrays.asList(collection), instanceOf);
    @SuppressWarnings("unchecked") V[] array = (V[])Array.newInstance(instanceOf, list.size());
    return list.toArray(array);
  }

  @NotNull
  public static <T, V> V[] findAllAsArray(@NotNull Collection<? extends T> collection, @NotNull Class<V> instanceOf) {
    List<V> list = findAll(collection, instanceOf);
    @SuppressWarnings("unchecked") V[] array = (V[])Array.newInstance(instanceOf, list.size());
    return list.toArray(array);
  }

  @NotNull
  public static <T> T[] findAllAsArray(@NotNull T[] collection, @NotNull Condition<? super T> instanceOf) {
    List<T> list = findAll(collection, instanceOf);
    @SuppressWarnings("unchecked") T[] array = (T[])Array.newInstance(collection.getClass().getComponentType(), list.size());
    return list.toArray(array);
  }

  @NotNull
  public static <T, V> List<V> findAll(@NotNull Collection<? extends T> collection, @NotNull Class<V> instanceOf) {
    final List<V> result = new SmartList<V>();
    for (final T t : collection) {
      if (instanceOf.isInstance(t)) {
        @SuppressWarnings("unchecked") V v = (V)t;
        result.add(v);
      }
    }
    return result;
  }

  public static <T> void removeDuplicates(@NotNull Collection<T> collection) {
    Set<T> collected = newHashSet();
    for (Iterator<T> iterator = collection.iterator(); iterator.hasNext();) {
      T t = iterator.next();
      if (!collected.contains(t)) {
        collected.add(t);
      }
      else {
        iterator.remove();
      }
    }
  }

  @NotNull
  public static Map<String, String> stringMap(@NotNull final String... keyValues) {
    final Map<String, String> result = newHashMap();
    for (int i = 0; i < keyValues.length - 1; i+=2) {
      result.put(keyValues[i], keyValues[i+1]);
    }

    return result;
  }

  @NotNull
  public static <T> Iterator<T> iterate(@NotNull T[] arrays) {
    return Arrays.asList(arrays).iterator();
  }

  @NotNull
  public static <T> Iterator<T> iterate(@NotNull final Enumeration<T> enumeration) {
    return new Iterator<T>() {
      @Override
      public boolean hasNext() {
        return enumeration.hasMoreElements();
      }

      @Override
      public T next() {
        return enumeration.nextElement();
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  @NotNull
  public static <T> Iterable<T> iterate(@NotNull T[] arrays, @NotNull Condition<? super T> condition) {
    return iterate(Arrays.asList(arrays), condition);
  }

  @NotNull
  public static <T> Iterable<T> iterate(@NotNull final Collection<? extends T> collection, @NotNull final Condition<? super T> condition) {
    if (collection.isEmpty()) return emptyIterable();
    return new Iterable<T>() {
      @Override
      public Iterator<T> iterator() {
        return new Iterator<T>() {
          Iterator<? extends T> impl = collection.iterator();
          T next = findNext();

          @Override
          public boolean hasNext() {
            return next != null;
          }

          @Override
          public T next() {
            T result = next;
            next = findNext();
            return result;
          }

          @Nullable
          private T findNext() {
            while (impl.hasNext()) {
              T each = impl.next();
              if (condition.value(each)) {
                return each;
              }
            }
            return null;
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }

  @NotNull
  public static <T> Iterable<T> iterateBackward(@NotNull final List<? extends T> list) {
    return new Iterable<T>() {
      @Override
      public Iterator<T> iterator() {
        return new Iterator<T>() {
          ListIterator<? extends T> it = list.listIterator(list.size());

          @Override
          public boolean hasNext() {
            return it.hasPrevious();
          }

          @Override
          public T next() {
            return it.previous();
          }

          @Override
          public void remove() {
            it.remove();
          }
        };
      }
    };
  }

  public static <E> void swapElements(@NotNull List<E> list, int index1, int index2) {
    E e1 = list.get(index1);
    E e2 = list.get(index2);
    list.set(index1, e2);
    list.set(index2, e1);
  }

  @NotNull
  public static <T> List<T> collect(@NotNull Iterator<?> iterator, @NotNull FilteringIterator.InstanceOf<T> instanceOf) {
    @SuppressWarnings("unchecked") List<T> list = collect(FilteringIterator.create((Iterator<T>)iterator, instanceOf));
    return list;
  }

  public static <T> void addAll(@NotNull Collection<T> collection, @NotNull Enumeration<? extends T> enumeration) {
    while (enumeration.hasMoreElements()) {
      T element = enumeration.nextElement();
      collection.add(element);
    }
  }

  @NotNull
  public static <T, A extends T, C extends Collection<T>> C addAll(@NotNull C collection, @NotNull A... elements) {
    //noinspection ManualArrayToCollectionCopy
    for (T element : elements) {
      collection.add(element);
    }
    return collection;
  }

  /**
   * Adds all not-null elements from the {@code elements}, ignoring nulls
   */
  @NotNull
  public static <T, A extends T, C extends Collection<T>> C addAllNotNull(@NotNull C collection, @NotNull A... elements) {
    for (T element : elements) {
      if (element != null) {
        collection.add(element);
      }
    }
    return collection;
  }

  public static <T> boolean removeAll(@NotNull Collection<T> collection, @NotNull T... elements) {
    boolean modified = false;
    for (T element : elements) {
      modified |= collection.remove(element);
    }
    return modified;
  }

  public static <T, U extends T> U findInstance(@NotNull Iterable<T> iterable, @NotNull Class<U> aClass) {
    return findInstance(iterable.iterator(), aClass);
  }

  public static <T, U extends T> U findInstance(@NotNull Iterator<T> iterator, @NotNull Class<U> aClass) {
    @SuppressWarnings("unchecked") U u = (U)find(iterator, new FilteringIterator.InstanceOf<U>(aClass));
    return u;
  }

  @Nullable
  public static <T, U extends T> U findInstance(@NotNull T[] array, @NotNull Class<U> aClass) {
    return findInstance(Arrays.asList(array), aClass);
  }

  @NotNull
  public static <T, V> List<T> concat(@NotNull V[] array, @NotNull Function<V, Collection<? extends T>> fun) {
    return concat(Arrays.asList(array), fun);
  }

  /**
   * @return read-only list consisting of the elements from the collections stored in list added together
   */
  @NotNull
  public static <T> List<T> concat(@NotNull Iterable<? extends Collection<T>> list) {
    List<T> result = new ArrayList<T>();
    for (final Collection<T> ts : list) {
      result.addAll(ts);
    }
    return result.isEmpty() ? Collections.<T>emptyList() : result;
  }

  /**
   * @param appendTail specify whether additional values should be appended in front or after the list
   * @return read-only list consisting of the elements from specified list with some additional values
   */
  @NotNull
  public static <T> List<T> concat(boolean appendTail, @NotNull List<? extends T> list, T... values) {
    return appendTail ? concat(list, list(values)) : concat(list(values), list);
  }

  /**
   * @return read-only list consisting of the two lists added together
   */
  @NotNull
  public static <T> List<T> concat(@NotNull final List<? extends T> list1, @NotNull final List<? extends T> list2) {
    final int size1 = list1.size();
    final int size = size1 + list2.size();

    return new AbstractList<T>() {
      @Override
      public T get(int index) {
        if (index < size1) {
          return list1.get(index);
        }

        return list2.get(index - size1);
      }

      @Override
      public int size() {
        return size;
      }
    };
  }

  @NotNull
  public static <T> Iterable<T> concat(@NotNull final Iterable<? extends T>... iterables) {
    return new Iterable<T>() {
      @Override
      public Iterator<T> iterator() {
        Iterator[] iterators = new Iterator[iterables.length];
        for (int i = 0; i < iterables.length; i++) {
          Iterable<? extends T> iterable = iterables[i];
          iterators[i] = iterable.iterator();
        }
        @SuppressWarnings("unchecked") Iterator<T> i = concatIterators(iterators);
        return i;
      }
    };
  }

  @NotNull
  public static <T> Iterator<T> concatIterators(@NotNull Iterator<T>... iterators) {
    return new SequenceIterator<T>(iterators);
  }

  @NotNull
  public static <T> Iterator<T> concatIterators(@NotNull Collection<Iterator<T>> iterators) {
    return new SequenceIterator<T>(iterators);
  }

  @NotNull
  public static <T> Iterable<T> concat(@NotNull final T[]... iterables) {
    return new Iterable<T>() {
      @Override
      public Iterator<T> iterator() {
        Iterator[] iterators = new Iterator[iterables.length];
        for (int i = 0, iterablesLength = iterables.length; i < iterablesLength; i++) {
          T[] iterable = iterables[i];
          iterators[i] = Arrays.asList(iterable).iterator();
        }
        @SuppressWarnings("unchecked") Iterator<T> i = concatIterators(iterators);
        return i;
      }
    };
  }

  /**
   * @return read-only list consisting of the lists added together
   */
  @NotNull
  public static <T> List<T> concat(@NotNull final List<? extends T>... lists) {
    int size = 0;
    for (List<? extends T> each : lists) {
      size += each.size();
    }
    if (size == 0) return emptyList();
    final int finalSize = size;
    return new AbstractList<T>() {
      @Override
      public T get(final int index) {
        if (index >= 0 && index < finalSize) {
          int from = 0;
          for (List<? extends T> each : lists) {
            if (from <= index && index < from + each.size()) return each.get(index - from);
            from += each.size();
          }
        }
        throw new IndexOutOfBoundsException("index: " + index + "size: " + size());
      }

      @Override
      public int size() {
        return finalSize;
      }
    };
  }

  /**
   * @return read-only list consisting of the lists added together
   */
  @NotNull
  public static <T> List<T> concat(@NotNull final List<List<? extends T>> lists) {
    @SuppressWarnings("unchecked") List<? extends T>[] array = lists.toArray(new List[lists.size()]);
    return concat(array);
  }

  /**
   * @return read-only list consisting of the lists (made by listGenerator) added together
   */
  @NotNull
  public static <T, V> List<T> concat(@NotNull Iterable<? extends V> list, @NotNull Function<V, Collection<? extends T>> listGenerator) {
    List<T> result = new ArrayList<T>();
    for (final V v : list) {
      result.addAll(listGenerator.fun(v));
    }
    return result.isEmpty() ? ContainerUtil.<T>emptyList() : result;
  }

  public static <T> boolean intersects(@NotNull Collection<? extends T> collection1, @NotNull Collection<? extends T> collection2) {
    for (T t : collection1) {
      //noinspection SuspiciousMethodCalls
      if (collection2.contains(t)) {
        return true;
      }
    }
    return false;
  }

  /**
   * @return read-only collection consisting of elements from both collections
   */
  @NotNull
  public static <T> Collection<T> intersection(@NotNull Collection<? extends T> collection1, @NotNull Collection<? extends T> collection2) {
    List<T> result = new ArrayList<T>();
    for (T t : collection1) {
      if (collection2.contains(t)) {
        result.add(t);
      }
    }
    return result.isEmpty() ? ContainerUtil.<T>emptyList() : result;
  }

  @Nullable
  public static <T> T getFirstItem(@Nullable Collection<T> items) {
    return getFirstItem(items, null);
  }

  public static <T> T getFirstItem(@Nullable final Collection<T> items, @Nullable final T def) {
    return items == null || items.isEmpty() ? def : items.iterator().next();
  }

  /**
   * The main difference from <code>subList</code> is that <code>getFirstItems</code> does not
   * throw any exceptions, even if maxItems is greater than size of the list
   *
   * @param items list
   * @param maxItems size of the result will be equal or less than <code>maxItems</code>
   * @param <T> type of list
   * @return new list with no more than <code>maxItems</code> first elements
   */
  @NotNull
  public static <T> List<T> getFirstItems(@NotNull final List<T> items, int maxItems) {
    return items.subList(0, Math.min(maxItems, items.size()));
  }

  @Nullable
  public static <T> T iterateAndGetLastItem(@NotNull Iterable<T> items) {
    Iterator<T> itr = items.iterator();
    T res = null;
    while (itr.hasNext()) {
      res = itr.next();
    }

    return res;
  }

  @Nullable
  public static <T, L extends List<T>> T getLastItem(@NotNull L list, @Nullable T def) {
    return list.isEmpty() ? def : list.get(list.size() - 1);
  }

  @Nullable
  public static <T, L extends List<T>> T getLastItem(@NotNull L list) {
    return getLastItem(list, null);
  }

  /**
   * @return read-only collection consisting of elements from the 'from' collection which are absent from the 'what' collection
   */
  @NotNull
  public static <T> Collection<T> subtract(@NotNull Collection<T> from, @NotNull Collection<T> what) {
    final Set<T> set = newHashSet(from);
    set.removeAll(what);
    return set.isEmpty() ? ContainerUtil.<T>emptyList() : set;
  }

  @NotNull
  public static <T> T[] toArray(@Nullable Collection<T> c, @NotNull ArrayFactory<T> factory) {
    return c != null ? c.toArray(factory.create(c.size())) : factory.create(0);
  }

  @NotNull
  public static <T> T[] toArray(@NotNull Collection<? extends T> c1, @NotNull Collection<? extends T> c2, @NotNull ArrayFactory<T> factory) {
    return ArrayUtil.mergeCollections(c1, c2, factory);
  }

  @NotNull
  public static <T> T[] mergeCollectionsToArray(@NotNull Collection<? extends T> c1, @NotNull Collection<? extends T> c2, @NotNull ArrayFactory<T> factory) {
    return ArrayUtil.mergeCollections(c1, c2, factory);
  }

  public static <T extends Comparable<T>> void sort(@NotNull List<T> list) {
    int size = list.size();

    if (size < 2) return;
    if (size == 2) {
      T t0 = list.get(0);
      T t1 = list.get(1);

      if (t0.compareTo(t1) > 0) {
        list.set(0, t1);
        list.set(1, t0);
      }
    }
    else if (size < INSERTION_SORT_THRESHOLD) {
      for (int i = 0; i < size; i++) {
        for (int j = 0; j < i; j++) {
          T ti = list.get(i);
          T tj = list.get(j);

          if (ti.compareTo(tj) < 0) {
            list.set(i, tj);
            list.set(j, ti);
          }
        }
      }
    }
    else {
      Collections.sort(list);
    }
  }

  public static <T> void sort(@NotNull List<T> list, @NotNull Comparator<T> comparator) {
    int size = list.size();

    if (size < 2) return;
    if (size == 2) {
      T t0 = list.get(0);
      T t1 = list.get(1);

      if (comparator.compare(t0, t1) > 0) {
        list.set(0, t1);
        list.set(1, t0);
      }
    }
    else if (size < INSERTION_SORT_THRESHOLD) {
      for (int i = 0; i < size; i++) {
        for (int j = 0; j < i; j++) {
          T ti = list.get(i);
          T tj = list.get(j);

          if (comparator.compare(ti, tj) < 0) {
            list.set(i, tj);
            list.set(j, ti);
          }
        }
      }
    }
    else {
      Collections.sort(list, comparator);
    }
  }

  public static <T extends Comparable<T>> void sort(@NotNull T[] a) {
    int size = a.length;

    if (size < 2) return;
    if (size == 2) {
      T t0 = a[0];
      T t1 = a[1];

      if (t0.compareTo(t1) > 0) {
        a[0] = t1;
        a[1] = t0;
      }
    }
    else if (size < INSERTION_SORT_THRESHOLD) {
      for (int i = 0; i < size; i++) {
        for (int j = 0; j < i; j++) {
          T ti = a[i];
          T tj = a[j];

          if (ti.compareTo(tj) < 0) {
            a[i] = tj;
            a[j] = ti;
          }
        }
      }
    }
    else {
      Arrays.sort(a);
    }
  }

  public static <T> void sort(@NotNull T[] a, @NotNull Comparator<T> comparator) {
    int size = a.length;

    if (size < 2) return;
    if (size == 2) {
      T t0 = a[0];
      T t1 = a[1];

      if (comparator.compare(t0, t1) > 0) {
        a[0] = t1;
        a[1] = t0;
      }
    }
    else if (size < INSERTION_SORT_THRESHOLD) {
      for (int i = 0; i < size; i++) {
        for (int j = 0; j < i; j++) {
          T ti = a[i];
          T tj = a[j];

          if (comparator.compare(ti, tj) < 0) {
            a[i] = tj;
            a[j] = ti;
          }
        }
      }
    }
    else {
      Arrays.sort(a, comparator);
    }
  }

  /**
   * @return read-only list consisting of the elements from the iterable converted by mapping
   */
  @NotNull
  public static <T,V> List<V> map(@NotNull Iterable<? extends T> iterable, @NotNull Function<T, V> mapping) {
    List<V> result = new ArrayList<V>();
    for (T t : iterable) {
      result.add(mapping.fun(t));
    }
    return result.isEmpty() ? ContainerUtil.<V>emptyList() : result;
  }

  /**
   * @return read-only list consisting of the elements from the iterable converted by mapping
   */
  @NotNull
  public static <T,V> List<V> map(@NotNull Collection<? extends T> iterable, @NotNull Function<T, V> mapping) {
    if (iterable.isEmpty()) return emptyList();
    List<V> result = new ArrayList<V>(iterable.size());
    for (T t : iterable) {
      result.add(mapping.fun(t));
    }
    return result;
  }

  /**
   * @return read-only list consisting of the elements from the array converted by mapping with nulls filtered out
   */
  @NotNull
  public static <T, V> List<V> mapNotNull(@NotNull T[] array, @NotNull Function<T, V> mapping) {
    return mapNotNull(Arrays.asList(array), mapping);
  }

  /**
   * @return read-only list consisting of the elements from the array converted by mapping with nulls filtered out
   */
  @NotNull
  public static <T, V> V[] mapNotNull(@NotNull T[] array, @NotNull Function<T, V> mapping, @NotNull V[] emptyArray) {
    List<V> result = new ArrayList<V>(array.length);
    for (T t : array) {
      V v = mapping.fun(t);
      if (v != null) {
        result.add(v);
      }
    }
    if (result.isEmpty()) {
      assert emptyArray.length == 0 : "You must pass an empty array";
      return emptyArray;
    }
    return result.toArray(emptyArray);
  }

  /**
   * @return read-only list consisting of the elements from the iterable converted by mapping with nulls filtered out
   */
  @NotNull
  public static <T, V> List<V> mapNotNull(@NotNull Iterable<? extends T> iterable, @NotNull Function<T, V> mapping) {
    List<V> result = new ArrayList<V>();
    for (T t : iterable) {
      final V o = mapping.fun(t);
      if (o != null) {
        result.add(o);
      }
    }
    return result.isEmpty() ? ContainerUtil.<V>emptyList() : result;
  }

  /**
   * @return read-only list consisting of the elements from the array converted by mapping with nulls filtered out
   */
  @NotNull
  public static <T, V> List<V> mapNotNull(@NotNull Collection<? extends T> iterable, @NotNull Function<T, V> mapping) {
    if (iterable.isEmpty()) {
      return emptyList();
    }

    List<V> result = new ArrayList<V>(iterable.size());
    for (T t : iterable) {
      final V o = mapping.fun(t);
      if (o != null) {
        result.add(o);
      }
    }
    return result.isEmpty() ? ContainerUtil.<V>emptyList() : result;
  }

  /**
   * @return read-only list consisting of the elements with nulls filtered out
   */
  @NotNull
  public static <T> List<T> packNullables(@NotNull T... elements) {
    List<T> list = new ArrayList<T>();
    for (T element : elements) {
      addIfNotNull(element, list);
    }
    return list.isEmpty() ? ContainerUtil.<T>emptyList() : list;
  }

  /**
   * @return read-only list consisting of the elements from the array converted by mapping
   */
  @NotNull
  public static <T, V> List<V> map(@NotNull T[] array, @NotNull Function<T, V> mapping) {
    List<V> result = new ArrayList<V>(array.length);
    for (T t : array) {
      result.add(mapping.fun(t));
    }
    return result.isEmpty() ? ContainerUtil.<V>emptyList() : result;
  }

  @NotNull
  public static <T, V> V[] map(@NotNull T[] arr, @NotNull Function<T, V> mapping, @NotNull V[] emptyArray) {
    if (arr.length==0) {
      assert emptyArray.length == 0 : "You must pass an empty array";
      return emptyArray;
    }

    List<V> result = new ArrayList<V>(arr.length);
    for (T t : arr) {
      result.add(mapping.fun(t));
    }
    return result.toArray(emptyArray);
  }

  @NotNull
  public static <T> Set<T> set(@NotNull T ... items) {
    return newHashSet(items);
  }

  public static <K, V> void putIfNotNull(final K key, @Nullable V value, @NotNull final Map<K, V> result) {
    if (value != null) {
      result.put(key, value);
    }
  }

  public static <T> void add(final T element, @NotNull final Collection<T> result, @NotNull final Disposable parentDisposable) {
    if (result.add(element)) {
      Disposer.register(parentDisposable, new Disposable() {
        @Override
        public void dispose() {
          result.remove(element);
        }
      });
    }
  }

  @NotNull
  public static <T> List<T> createMaybeSingletonList(@Nullable T element) {
    return element == null ? ContainerUtil.<T>emptyList() : Collections.singletonList(element);
  }

  @NotNull
  public static <T, V> V getOrCreate(@NotNull Map<T, V> result, final T key, @NotNull V defaultValue) {
    V value = result.get(key);
    if (value == null) {
      result.put(key, value = defaultValue);
    }
    return value;
  }

  public static <T, V> V getOrCreate(@NotNull Map<T, V> result, final T key, @NotNull Factory<V> factory) {
    V value = result.get(key);
    if (value == null) {
      result.put(key, value = factory.create());
    }
    return value;
  }

  @NotNull
  public static <T, V> V getOrElse(@NotNull Map<T, V> result, final T key, @NotNull V defValue) {
    V value = result.get(key);
    return value == null ? defValue : value;
  }

  public static <T> boolean and(@NotNull T[] iterable, @NotNull Condition<T> condition) {
    return and(Arrays.asList(iterable), condition);
  }

  public static <T> boolean and(@NotNull Iterable<T> iterable, @NotNull Condition<T> condition) {
    for (final T t : iterable) {
      if (!condition.value(t)) return false;
    }
    return true;
  }

  public static <T> boolean exists(@NotNull T[] iterable, @NotNull Condition<T> condition) {
    return or(Arrays.asList(iterable), condition);
  }

  public static <T> boolean exists(@NotNull Iterable<T> iterable, @NotNull Condition<T> condition) {
    return or(iterable, condition);
  }

  public static <T> boolean or(@NotNull T[] iterable, @NotNull Condition<T> condition) {
    return or(Arrays.asList(iterable), condition);
  }

  public static <T> boolean or(@NotNull Iterable<T> iterable, @NotNull Condition<T> condition) {
    for (final T t : iterable) {
      if (condition.value(t)) return true;
    }
    return false;
  }

  @NotNull
  public static <T> List<T> unfold(@Nullable T t, @NotNull NullableFunction<T, T> next) {
    if (t == null) return emptyList();

    final ArrayList<T> list = new ArrayList<T>();
    while (t != null) {
      list.add(t);
      t = next.fun(t);
    }
    return list;
  }

  @NotNull
  public static <T> List<T> dropTail(@NotNull List<T> items) {
    return items.subList(0, items.size() - 1);
  }

  @NotNull
  public static <T> List<T> list(@NotNull T... items) {
    return Arrays.asList(items);
  }

  // Generalized Quick Sort. Does neither array.clone() nor list.toArray()

  public static <T> void quickSort(@NotNull List<T> list, @NotNull Comparator<? super T> comparator) {
    quickSort(list, comparator, 0, list.size());
  }

  private static <T> void quickSort(@NotNull List<T> x, @NotNull Comparator<? super T> comparator, int off, int len) {
    // Insertion sort on smallest arrays
    if (len < 7) {
      for (int i = off; i < len + off; i++) {
        for (int j = i; j > off && comparator.compare(x.get(j), x.get(j - 1)) < 0; j--) {
          swapElements(x, j, j - 1);
        }
      }
      return;
    }

    // Choose a partition element, v
    int m = off + (len >> 1);       // Small arrays, middle element
    if (len > 7) {
      int l = off;
      int n = off + len - 1;
      if (len > 40) {        // Big arrays, pseudomedian of 9
        int s = len / 8;
        l = med3(x, comparator, l, l + s, l + 2 * s);
        m = med3(x, comparator, m - s, m, m + s);
        n = med3(x, comparator, n - 2 * s, n - s, n);
      }
      m = med3(x, comparator, l, m, n); // Mid-size, med of 3
    }
    T v = x.get(m);

    // Establish Invariant: v* (<v)* (>v)* v*
    int a = off;
    int b = a;
    int c = off + len - 1;
    int d = c;
    while (true) {
      while (b <= c && comparator.compare(x.get(b), v) <= 0) {
        if (comparator.compare(x.get(b), v) == 0) {
          swapElements(x, a++, b);
        }
        b++;
      }
      while (c >= b && comparator.compare(v, x.get(c)) <= 0) {
        if (comparator.compare(x.get(c), v) == 0) {
          swapElements(x, c, d--);
        }
        c--;
      }
      if (b > c) break;
      swapElements(x, b++, c--);
    }

    // Swap partition elements back to middle
    int n = off + len;
    int s = Math.min(a - off, b - a);
    vecswap(x, off, b - s, s);
    s = Math.min(d - c, n - d - 1);
    vecswap(x, b, n - s, s);

    // Recursively sort non-partition-elements
    if ((s = b - a) > 1) quickSort(x, comparator, off, s);
    if ((s = d - c) > 1) quickSort(x, comparator, n - s, s);
  }

  /*
   * Returns the index of the median of the three indexed longs.
   */
  private static <T> int med3(@NotNull List<T> x, Comparator<? super T> comparator, int a, int b, int c) {
    return comparator.compare(x.get(a), x.get(b)) < 0 ? comparator.compare(x.get(b), x.get(c)) < 0
                                                        ? b
                                                        : comparator.compare(x.get(a), x.get(c)) < 0 ? c : a
                                                      : comparator.compare(x.get(c), x.get(b)) < 0
                                                        ? b
                                                        : comparator.compare(x.get(c), x.get(a)) < 0 ? c : a;
  }

  /*
   * Swaps x[a .. (a+n-1)] with x[b .. (b+n-1)].
   */
  private static <T> void vecswap(List<T> x, int a, int b, int n) {
    for (int i = 0; i < n; i++, a++, b++) {
      swapElements(x, a, b);
    }
  }

  /**
   * Merge sorted points, which are sorted by x and with equal x by y.
   * Result is put to x1 y1.
   */
  public static void mergeSortedArrays(@NotNull TIntArrayList x1,
                                       @NotNull TIntArrayList y1,
                                       @NotNull TIntArrayList x2,
                                       @NotNull TIntArrayList y2) {
    TIntArrayList newX = new TIntArrayList();
    TIntArrayList newY = new TIntArrayList();

    int i = 0;
    int j = 0;

    while (i < x1.size() && j < x2.size()) {
      if (x1.get(i) < x2.get(j) || x1.get(i) == x2.get(j) && y1.get(i) < y2.get(j)) {
        newX.add(x1.get(i));
        newY.add(y1.get(i));
        i++;
      }
      else if (x1.get(i) > x2.get(j) || x1.get(i) == x2.get(j) && y1.get(i) > y2.get(j)) {
        newX.add(x2.get(j));
        newY.add(y2.get(j));
        j++;
      }
      else { //equals
        newX.add(x1.get(i));
        newY.add(y1.get(i));
        i++;
        j++;
      }
    }

    while (i < x1.size()) {
      newX.add(x1.get(i));
      newY.add(y1.get(i));
      i++;
    }

    while (j < x2.size()) {
      newX.add(x2.get(j));
      newY.add(y2.get(j));
      j++;
    }

    x1.clear();
    y1.clear();
    x1.add(newX.toNativeArray());
    y1.add(newY.toNativeArray());
  }


  /**
   * @return read-only set consisting of the only element o
   */
  @NotNull
  public static <T> Set<T> singleton(final T o, @NotNull final TObjectHashingStrategy<T> strategy) {
    return new SingletonSet<T>(o, strategy);
  }

  /**
   * @return read-only list consisting of the elements from all of the collections
   */
  @NotNull
  public static <E> List<E> flatten(@NotNull Collection<E>[] collections) {
    return flatten(Arrays.asList(collections));
  }

  /**
   * @return read-only list consisting of the elements from all of the collections
   */
  @NotNull
  public static <E> List<E> flatten(@NotNull Iterable<? extends Collection<E>> collections) {
    List<E> result = new ArrayList<E>();
    for (Collection<E> list : collections) {
      result.addAll(list);
    }

    return result.isEmpty() ? ContainerUtil.<E>emptyList() : result;
  }

  /**
   * @return read-only list consisting of the elements from all of the collections
   */
  @NotNull
  public static <E> List<E> flattenIterables(@NotNull Iterable<? extends Iterable<E>> collections) {
    List<E> result = new ArrayList<E>();
    for (Iterable<E> list : collections) {
      for (E e : list) {
        result.add(e);
      }
    }
    return result.isEmpty() ? ContainerUtil.<E>emptyList() : result;
  }

  @NotNull
  public static <K,V> V[] convert(@NotNull K[] from, @NotNull V[] to, @NotNull Function<K,V> fun) {
    if (to.length < from.length) {
      @SuppressWarnings("unchecked") V[] array = (V[])Array.newInstance(to.getClass().getComponentType(), from.length);
      to = array;
    }
    for (int i = 0; i < from.length; i++) {
      to[i] = fun.fun(from[i]);
    }
    return to;
  }

  public static <T> boolean containsIdentity(@NotNull Iterable<T> list, T element) {
    for (T t : list) {
      if (t == element) {
        return true;
      }
    }
    return false;
  }

  public static <T> int indexOfIdentity(@NotNull List<T> list, T element) {
    for (int i = 0, listSize = list.size(); i < listSize; i++) {
      if (list.get(i) == element) {
        return i;
      }
    }
    return -1;
  }

  public static <T> boolean equalsIdentity(@NotNull List<T> list1, @NotNull List<T> list2) {
    int listSize = list1.size();
    if (list2.size() != listSize) {
      return false;
    }

    for (int i = 0; i < listSize; i++) {
      if (list1.get(i) != list2.get(i)) {
        return false;
      }
    }
    return true;
  }

  public static <T> int indexOf(@NotNull List<T> list, @NotNull Condition<T> condition) {
    for (int i = 0, listSize = list.size(); i < listSize; i++) {
      T t = list.get(i);
      if (condition.value(t)) {
        return i;
      }
    }
    return -1;
  }

  @NotNull
  public static <A,B> Map<B,A> reverseMap(@NotNull Map<A,B> map) {
    final Map<B,A> result = newHashMap();
    for (Map.Entry<A, B> entry : map.entrySet()) {
      result.put(entry.getValue(), entry.getKey());
    }
    return result;
  }

  public static <T> boolean processRecursively(final T root, @NotNull PairProcessor<T, List<T>> processor) {
    final LinkedList<T> list = new LinkedList<T>();
    list.add(root);
    while (!list.isEmpty()) {
      final T o = list.removeFirst();
      if (!processor.process(o, list)) return false;
    }
    return true;
  }

  @Nullable
  public static <T> List<T> trimToSize(@Nullable List<T> list) {
    if (list == null) return null;
    if (list.isEmpty()) return emptyList();

    if (list instanceof ArrayList) {
      ((ArrayList)list).trimToSize();
    }

    return list;
  }

  @NotNull
  public static <T> Stack<T> newStack() {
    return ContainerUtilRt.newStack();
  }

  @NotNull
  public static <T> Stack<T> newStack(@NotNull Collection<T> initial) {
    return ContainerUtilRt.newStack(initial);
  }

  @NotNull
  public static <T> Stack<T> newStack(@NotNull T... initial) {
    return ContainerUtilRt.newStack(initial);
  }

  @NotNull
  public static <T> List<T> emptyList() {
    return ContainerUtilRt.emptyList();
  }

  @NotNull
  public static <T> CopyOnWriteArrayList<T> createEmptyCOWList() {
    return ContainerUtilRt.createEmptyCOWList();
  }

  /**
   * Creates List which is thread-safe to modify and iterate.
   * It differs from the java.util.concurrent.CopyOnWriteArrayList in the following:
   * - faster modification in the uncontended case
   * - less memory
   * - slower modification in highly contented case (which is the kind of situation you shouldn't use COWAL anyway)
   */
  @NotNull
  public static <T> List<T> createLockFreeCopyOnWriteList() {
    return new LockFreeCopyOnWriteArrayList<T>();
  }

  @NotNull
  public static <T> List<T> createLockFreeCopyOnWriteList(@NotNull Collection<? extends T> c) {
    return new LockFreeCopyOnWriteArrayList<T>(c);
  }

  public static <T> void addIfNotNull(@Nullable T element, @NotNull Collection<T> result) {
    ContainerUtilRt.addIfNotNull(element, result);
  }

  public static <T> void addIfNotNull(@NotNull Collection<T> result, @Nullable T element) {
    ContainerUtilRt.addIfNotNull(result, element);
  }

  @NotNull
  public static <T, V> List<V> map2List(@NotNull T[] array, @NotNull Function<T, V> mapper) {
    return ContainerUtilRt.map2List(array, mapper);
  }

  @NotNull
  public static <T, V> List<V> map2List(@NotNull Collection<? extends T> collection, @NotNull Function<T, V> mapper) {
    return ContainerUtilRt.map2List(collection, mapper);
  }

  @NotNull
  public static <T, V> Set<V> map2Set(@NotNull T[] collection, @NotNull Function<T, V> mapper) {
    return ContainerUtilRt.map2Set(collection, mapper);
  }

  @NotNull
  public static <T, V> Set<V> map2Set(@NotNull Collection<? extends T> collection, @NotNull Function<T, V> mapper) {
    return ContainerUtilRt.map2Set(collection, mapper);
  }

  @NotNull
  public static <T> T[] toArray(@NotNull List<T> collection, @NotNull T[] array) {
    return ContainerUtilRt.toArray(collection, array);
  }

  @NotNull
  public static <T> T[] toArray(@NotNull Collection<T> c, @NotNull T[] sample) {
    return ContainerUtilRt.toArray(c, sample);
  }

  @NotNull
  public static <T> T[] copyAndClear(@NotNull Collection<T> collection, @NotNull ArrayFactory<T> factory, boolean clear) {
    int size = collection.size();
    T[] a = factory.create(size);
    if (size > 0) {
      a = collection.toArray(a);
      if (clear) collection.clear();
    }
    return a;
  }

  @NotNull
  public static <T> Collection<T> toCollection(@NotNull Iterable<T> iterable) {
    return iterable instanceof Collection ? (Collection<T>)iterable : newArrayList(iterable);
  }

  @NotNull
  public static <T> List<T> toList(@NotNull Enumeration<T> enumeration) {
    if (!enumeration.hasMoreElements()) {
      return Collections.emptyList();
    }

    List<T> result = new SmartList<T>();
    while (enumeration.hasMoreElements()) {
      result.add(enumeration.nextElement());
    }
    return result;
  }

  @Contract("null -> null")
  public static <T> boolean isEmpty(List<T> list) {
    return list == null || list.isEmpty();
  }
}
