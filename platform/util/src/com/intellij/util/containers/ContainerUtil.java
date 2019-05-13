// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.*;
import com.intellij.util.*;
import gnu.trove.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.HashSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

@SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
public class ContainerUtil extends ContainerUtilRt {
  private static final int INSERTION_SORT_THRESHOLD = 10;

  @NotNull
  @Contract(pure=true)
  public static <T> T[] ar(@NotNull T... elements) {
    return elements;
  }

  @NotNull
  @Contract(pure=true)
  public static <K, V> HashMap<K, V> newHashMap() {
    return new HashMap<K, V>();
  }

  @NotNull
  @Contract(pure=true)
  public static <K, V> HashMap<K, V> newHashMap(@NotNull Map<? extends K, ? extends V> map) {
    return new HashMap<K, V>(map);
  }

  @NotNull
  @Contract(pure=true)
  public static <K, V> Map<K, V> newHashMap(@NotNull Pair<? extends K, ? extends V> first, @NotNull Pair<? extends K, ? extends V>... entries) {
    return ContainerUtilRt.newHashMap(first, entries);
  }

  @NotNull
  @Contract(pure=true)
  public static <K, V> Map<K, V> newHashMap(@NotNull List<? extends K> keys, @NotNull List<? extends V> values) {
    return ContainerUtilRt.newHashMap(keys, values);
  }

  @NotNull
  @Contract(pure=true)
  public static <K extends Comparable<? super K>, V> TreeMap<K, V> newTreeMap() {
    return ContainerUtilRt.newTreeMap();
  }

  @NotNull
  @Contract(pure=true)
  public static <K extends Comparable<? super K>, V> TreeMap<K, V> newTreeMap(@NotNull Map<? extends K, ? extends V> map) {
    return ContainerUtilRt.newTreeMap(map);
  }

  @NotNull
  @Contract(pure=true)
  public static <K, V> LinkedHashMap<K, V> newLinkedHashMap() {
    return ContainerUtilRt.newLinkedHashMap();
  }

  @NotNull
  @Contract(pure=true)
  public static <K, V> LinkedHashMap<K, V> newLinkedHashMap(int capacity) {
    return ContainerUtilRt.newLinkedHashMap(capacity);
  }

  @NotNull
  @Contract(pure=true)
  public static <K, V> LinkedHashMap<K, V> newLinkedHashMap(@NotNull Map<? extends K, ? extends V> map) {
    return ContainerUtilRt.newLinkedHashMap(map);
  }

  @NotNull
  @Contract(pure=true)
  public static <K, V> LinkedHashMap<K, V> newLinkedHashMap(@NotNull Pair<? extends K, ? extends V> first, @NotNull Pair<? extends K, ? extends V>... entries) {
    return ContainerUtilRt.newLinkedHashMap(first, entries);
  }

  @NotNull
  @Contract(pure=true)
  public static <K, V> THashMap<K, V> newTroveMap() {
    return new THashMap<K, V>();
  }

  @NotNull
  @Contract(pure=true)
  public static <K, V> THashMap<K, V> newTroveMap(@NotNull TObjectHashingStrategy<K> strategy) {
    return new THashMap<K, V>(strategy);
  }

  @NotNull
  @Contract(pure=true)
  public static <K extends Enum<K>, V> EnumMap<K, V> newEnumMap(@NotNull Class<K> keyType) {
    return new EnumMap<K, V>(keyType);
  }

  @SuppressWarnings("unchecked")
  @NotNull
  @Contract(pure=true)
  public static <T> TObjectHashingStrategy<T> canonicalStrategy() {
    return TObjectHashingStrategy.CANONICAL;
  }

  @SuppressWarnings("unchecked")
  @NotNull
  @Contract(pure=true)
  public static <T> TObjectHashingStrategy<T> identityStrategy() {
    return TObjectHashingStrategy.IDENTITY;
  }

  @NotNull
  @Contract(pure=true)
  public static <K, V> IdentityHashMap<K, V> newIdentityHashMap() {
    return new IdentityHashMap<K, V>();
  }

  @NotNull
  @Contract(pure=true)
  public static <T> LinkedList<T> newLinkedList() {
    return ContainerUtilRt.newLinkedList();
  }

  @NotNull
  @Contract(pure=true)
  public static <T> LinkedList<T> newLinkedList(@NotNull T... elements) {
    return ContainerUtilRt.newLinkedList(elements);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> LinkedList<T> newLinkedList(@NotNull Iterable<? extends T> elements) {
    return ContainerUtilRt.newLinkedList(elements);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> ArrayList<T> newArrayList() {
    return new ArrayList<T>();
  }

  @NotNull
  @Contract(pure=true)
  public static <E> ArrayList<E> newArrayList(@NotNull E... array) {
    return ContainerUtilRt.newArrayList(array);
  }

  @NotNull
  @Contract(pure=true)
  public static <E> ArrayList<E> newArrayList(@NotNull Iterable<? extends E> iterable) {
    return ContainerUtilRt.newArrayList(iterable);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> ArrayList<T> newArrayListWithCapacity(int size) {
    return new ArrayList<T>(size);
  }

  @NotNull
  @Contract(pure=true)
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
  @Contract(pure = true)
  public static <T> List<T> newUnmodifiableList(List<? extends T> originalList) {
    int size = originalList.size();
    if (size == 0) {
      return emptyList();
    }
    if (size == 1) {
      return Collections.singletonList(originalList.get(0));
    }
    return Collections.unmodifiableList(new ArrayList<T>(originalList));
  }

  @NotNull
  @Contract(pure = true)
  public static <T> Collection<T> unmodifiableOrEmptyCollection(@NotNull Collection<? extends T> original) {
    int size = original.size();
    if (size == 0) {
      return emptyList();
    }
    if (size == 1) {
      return Collections.singletonList(original.iterator().next());
    }
    return Collections.unmodifiableCollection(original);
  }

  @NotNull
  @Contract(pure = true)
  public static <T> List<T> unmodifiableOrEmptyList(@NotNull List<? extends T> original) {
    int size = original.size();
    if (size == 0) {
      return emptyList();
    }
    if (size == 1) {
      return Collections.singletonList(original.iterator().next());
    }
    return Collections.unmodifiableList(original);
  }

  @NotNull
  @Contract(pure = true)
  public static <T> Set<T> unmodifiableOrEmptySet(@NotNull Set<? extends T> original) {
    int size = original.size();
    if (size == 0) {
      return Collections.emptySet();
    }
    if (size == 1) {
      return Collections.singleton(original.iterator().next());
    }
    return Collections.unmodifiableSet(original);
  }

  @NotNull
  @Contract(pure = true)
  public static <K,V> Map<K,V> unmodifiableOrEmptyMap(@NotNull Map<? extends K, ? extends V> original) {
    int size = original.size();
    if (size == 0) {
      return Collections.emptyMap();
    }
    if (size == 1) {
      Map.Entry<? extends K, ? extends V> entry = original.entrySet().iterator().next();
      return Collections.singletonMap(entry.getKey(), entry.getValue());
    }
    else {
      return Collections.unmodifiableMap(original);
    }
  }

  @NotNull
  @Contract(pure=true)
  public static <T> List<T> newSmartList() {
    return new SmartList<T>();
  }

  @NotNull
  @Contract(pure=true)
  public static <T> List<T> newSmartList(T element) {
    return new SmartList<T>(element);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> List<T> newSmartList(@NotNull T... elements) {
    return new SmartList<T>(elements);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> HashSet<T> newHashSet() {
    return new HashSet<T>();
  }

  @NotNull
  @Contract(pure=true)
  public static <T> HashSet<T> newHashSet(int initialCapacity) {
    return new HashSet<T>(initialCapacity);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> HashSet<T> newHashSet(@NotNull T... elements) {
    return ContainerUtilRt.newHashSet(elements);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> HashSet<T> newHashSet(@NotNull Iterable<? extends T> iterable) {
    return ContainerUtilRt.newHashSet(iterable);
  }

  @NotNull
  public static <T> HashSet<T> newHashSet(@NotNull Iterator<? extends T> iterator) {
    return ContainerUtilRt.newHashSet(iterator);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> Set<T> newHashOrEmptySet(@Nullable Iterable<? extends T> iterable) {
    boolean empty = iterable == null || iterable instanceof Collection && ((Collection)iterable).isEmpty();
    return empty ? Collections.<T>emptySet() : ContainerUtilRt.newHashSet(iterable);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> LinkedHashSet<T> newLinkedHashSet() {
    return ContainerUtilRt.newLinkedHashSet();
  }

  @NotNull
  @Contract(pure=true)
  public static <T> LinkedHashSet<T> newLinkedHashSet(@NotNull Iterable<? extends T> elements) {
    return ContainerUtilRt.newLinkedHashSet(elements);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> LinkedHashSet<T> newLinkedHashSet(@NotNull T... elements) {
    return ContainerUtilRt.newLinkedHashSet(elements);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> THashSet<T> newTroveSet() {
    return new THashSet<T>();
  }

  @NotNull
  @Contract(pure=true)
  public static <T> THashSet<T> newTroveSet(@NotNull TObjectHashingStrategy<T> strategy) {
    return new THashSet<T>(strategy);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> THashSet<T> newTroveSet(@NotNull T... elements) {
    return newTroveSet(Arrays.asList(elements));
  }

  @NotNull
  @Contract(pure=true)
  public static <T> THashSet<T> newTroveSet(@NotNull TObjectHashingStrategy<T> strategy, @NotNull T... elements) {
    return new THashSet<T>(Arrays.asList(elements), strategy);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> THashSet<T> newTroveSet(@NotNull TObjectHashingStrategy<T> strategy, @NotNull Collection<? extends T> elements) {
    return new THashSet<T>(elements, strategy);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> THashSet<T> newTroveSet(@NotNull Collection<? extends T> elements) {
    return new THashSet<T>(elements);
  }

  @NotNull
  @Contract(pure=true)
  public static <K> THashSet<K> newIdentityTroveSet() {
    return new THashSet<K>(ContainerUtil.<K>identityStrategy());
  }

  @NotNull
  @Contract(pure=true)
  public static <K> THashSet<K> newIdentityTroveSet(int initialCapacity) {
    return new THashSet<K>(initialCapacity, ContainerUtil.<K>identityStrategy());
  }
  @NotNull
  @Contract(pure=true)
  public static <K> THashSet<K> newIdentityTroveSet(@NotNull Collection<? extends K> collection) {
    return new THashSet<K>(collection, ContainerUtil.<K>identityStrategy());
  }

  @NotNull
  @Contract(pure=true)
  public static <K,V> THashMap<K,V> newIdentityTroveMap() {
    return new THashMap<K,V>(ContainerUtil.<K>identityStrategy());
  }

  @NotNull
  @Contract(pure=true)
  public static <T extends Comparable<? super T>> TreeSet<T> newTreeSet() {
    return ContainerUtilRt.newTreeSet();
  }

  @NotNull
  @Contract(pure=true)
  public static <T extends Comparable<? super T>> TreeSet<T> newTreeSet(@NotNull Iterable<? extends T> elements) {
    return ContainerUtilRt.newTreeSet(elements);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> TreeSet<T> newTreeSet(@Nullable Comparator<? super T> comparator) {
    return ContainerUtilRt.newTreeSet(comparator);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> Set<T> newConcurrentSet() {
    return Collections.newSetFromMap(ContainerUtil.<T, Boolean>newConcurrentMap());
  }

  @NotNull
  @Contract(pure=true)
  public static <K, V> ConcurrentMap<K, V> newConcurrentMap() {
    return new ConcurrentHashMap<K, V>();
  }

  @Contract(pure=true)
  public static <K, V> ConcurrentMap<K,V> newConcurrentMap(int initialCapacity) {
    return new ConcurrentHashMap<K, V>(initialCapacity);
  }

  @Contract(pure=true)
  public static <K, V> ConcurrentMap<K,V> newConcurrentMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
    return new ConcurrentHashMap<K, V>(initialCapacity, loadFactor, concurrencyLevel);
  }

  @NotNull
  @Contract(pure=true)
  public static <E> List<E> reverse(@NotNull final List<? extends E> elements) {
    if (elements.isEmpty()) {
      return ContainerUtilRt.emptyList();
    }

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
  @Contract(pure=true)
  public static <K, V> Map<K, V> union(@NotNull Map<? extends K, ? extends V> map, @NotNull Map<? extends K, ? extends V> map2) {
    Map<K, V> result = new THashMap<K, V>(map.size() + map2.size());
    result.putAll(map);
    result.putAll(map2);
    return result;
  }

  @NotNull
  @Contract(pure=true)
  public static <T> Set<T> union(@NotNull Set<? extends T> set, @NotNull Set<? extends T> set2) {
    return union((Collection<? extends T>)set, set2);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> Set<T> union(@NotNull Collection<? extends T> set, @NotNull Collection<? extends T> set2) {
    Set<T> result = new THashSet<T>(set.size() + set2.size());
    result.addAll(set);
    result.addAll(set2);
    return result;
  }

  @NotNull
  @Contract(pure=true)
  public static <E> Set<E> immutableSet(@NotNull E... elements) {
    switch (elements.length) {
      case 0:
        return Collections.emptySet();
      case 1:
        return Collections.singleton(elements[0]);
      default:
        return Collections.unmodifiableSet(new THashSet<E>(Arrays.asList(elements)));
    }
  }

  @NotNull
  @Contract(pure=true)
  public static <E> ImmutableList<E> immutableList(@NotNull E ... array) {
    return new ImmutableListBackedByArray<E>(array);
  }

  @NotNull
  @Contract(pure=true)
  public static <E> ImmutableList<E> immutableSingletonList(final E element) {
    return ImmutableList.singleton(element);
  }

  @NotNull
  @Contract(pure=true)
  public static <E> ImmutableList<E> immutableList(@NotNull List<? extends E> list) {
    return new ImmutableListBackedByList<E>(list);
  }

  @NotNull
  @Contract(pure=true)
  public static <K, V> ImmutableMapBuilder<K, V> immutableMapBuilder() {
    return new ImmutableMapBuilder<K, V>();
  }

  @NotNull
  @Contract(pure = true)
  public static <K, V> MultiMap<K, V> groupBy(@NotNull Iterable<? extends V> collection, @NotNull NullableFunction<? super V, ? extends K> grouper) {
    MultiMap<K, V> result = MultiMap.createLinked();
    for (V data : collection) {
      K key = grouper.fun(data);
      if (key == null) {
        continue;
      }
      result.putValue(key, data);
    }

    if (!result.isEmpty() && result.keySet().iterator().next() instanceof Comparable) {
      //noinspection unchecked
      return new KeyOrderedMultiMap(result);
    }
    return result;
  }

  @Contract(pure = true)
  public static <T> T getOrElse(@NotNull List<? extends T> elements, int i, T defaultValue) {
    return elements.size() > i ? elements.get(i) : defaultValue;
  }

  public static class ImmutableMapBuilder<K, V> {
    private final Map<K, V> myMap = new THashMap<K, V>();

    public ImmutableMapBuilder<K, V> put(K key, V value) {
      myMap.put(key, value);
      return this;
    }

    @Contract(pure=true)
    public Map<K, V> build() {
      return Collections.unmodifiableMap(myMap);
    }
  }

  private static class ImmutableListBackedByList<E> extends ImmutableList<E> {
    private final List<? extends E> myStore;

    private ImmutableListBackedByList(@NotNull List<? extends E> list) {
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

    // override for more efficient arraycopy vs. iterator() creation/traversing
    @NotNull
    @Override
    public <T> T[] toArray(@NotNull T[] a) {
      int size = size();
      //noinspection unchecked
      T[] result = a.length >= size ? a : (T[])Array.newInstance(a.getClass().getComponentType(), size);
      System.arraycopy(myStore, 0, result, 0, size);
      if (result.length > size) {
        result[size] = null;
      }
      return result;
    }
  }

  @NotNull
  @Contract(pure=true)
  public static <K, V> Map<K, V> intersection(@NotNull Map<? extends K, ? extends V> map1, @NotNull Map<? extends K, ? extends V> map2) {
    if (map2.size() < map1.size()) {
      Map<? extends K, ? extends V> t = map1;
      map1 = map2;
      map2 = t;
    }
    final Map<K, V> res = new HashMap<K, V>(map1);
    for (Map.Entry<? extends K, ? extends V> entry : map2.entrySet()) {
      K key = entry.getKey();
      V v2 = entry.getValue();
      V v1 = map1.get(key);
      if (!(v1 == v2 || v1 != null && v1.equals(v2))) {
        res.remove(key);
      }
    }
    return res;
  }

  @NotNull
  @Contract(pure=true)
  public static <K, V> Map<K,Couple<V>> diff(@NotNull Map<? extends K, ? extends V> map1, @NotNull Map<? extends K, ? extends V> map2) {
    Set<K> keys = union(map1.keySet(), map2.keySet());
    Map<K, Couple<V>> res = new HashMap<K, Couple<V>>();
    for (K k : keys) {
      V v1 = map1.get(k);
      V v2 = map2.get(k);
      if (!(v1 == v2 || v1 != null && v1.equals(v2))) {
        res.put(k, Couple.of(v1, v2));
      }
    }
    return res;
  }

  public static <T> void processSortedListsInOrder(@NotNull List<? extends T> list1,
                                                   @NotNull List<? extends T> list2,
                                                   @NotNull Comparator<? super T> comparator,
                                                   boolean mergeEqualItems,
                                                   @NotNull Consumer<? super T> processor) {
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
        if (c == 0) {
          index1++;
          index2++;
          if (mergeEqualItems) {
            e = element1;
          }
          else {
            processor.consume(element1);
            e = element2;
          }
        }
        else if (c < 0) {
          e = element1;
          index1++;
        }
        else {
          e = element2;
          index2++;
        }
      }
      processor.consume(e);
    }
  }

  @NotNull
  @Contract(pure=true)
  public static <T> List<T> mergeSortedLists(@NotNull List<? extends T> list1,
                                             @NotNull List<? extends T> list2,
                                             @NotNull Comparator<? super T> comparator,
                                             boolean mergeEqualItems) {
    final List<T> result = new ArrayList<T>(list1.size() + list2.size());
    processSortedListsInOrder(list1, list2, comparator, mergeEqualItems, new Consumer<T>() {
      @Override
      public void consume(T t) {
        result.add(t);
      }
    });
    return result;
  }

  @NotNull
  @Contract(pure=true)
  public static <T> List<T> subList(@NotNull List<T> list, int from) {
    return list.subList(from, list.size());
  }

  public static <T> void addAll(@NotNull Collection<? super T> collection, @NotNull Iterable<? extends T> appendix) {
    addAll(collection, appendix.iterator());
  }

  public static <T> void addAll(@NotNull Collection<? super T> collection, @NotNull Iterator<? extends T> iterator) {
    while (iterator.hasNext()) {
      T o = iterator.next();
      collection.add(o);
    }
  }

  /**
   * Adds all not-null elements from the {@code elements}, ignoring nulls
   */
  public static <T> void addAllNotNull(@NotNull Collection<? super T> collection, @NotNull Iterable<? extends T> elements) {
    addAllNotNull(collection, elements.iterator());
  }

  /**
   * Adds all not-null elements from the {@code elements}, ignoring nulls
   */
  public static <T> void addAllNotNull(@NotNull Collection<? super T> collection, @NotNull Iterator<? extends T> elements) {
    while (elements.hasNext()) {
      T o = elements.next();
      if (o != null) {
        collection.add(o);
      }
    }
  }

  @NotNull
  public static <T> List<T> collect(@NotNull Iterator<? extends T> iterator) {
    if (!iterator.hasNext()) return emptyList();
    List<T> list = new ArrayList<T>();
    addAll(list, iterator);
    return list;
  }

  @NotNull
  public static <T> Set<T> collectSet(@NotNull Iterator<? extends T> iterator) {
    if (!iterator.hasNext()) return Collections.emptySet();
    Set<T> hashSet = new HashSet<T>();
    addAll(hashSet, iterator);
    return hashSet;
  }

  @NotNull
  @Contract(pure = true)
  public static <K, V> Map<K, V> newMapFromKeys(@NotNull Iterator<? extends K> keys, @NotNull Convertor<? super K, ? extends V> valueConvertor) {
    Map<K, V> map = new HashMap<K, V>();
    while (keys.hasNext()) {
      K key = keys.next();
      map.put(key, valueConvertor.convert(key));
    }
    return map;
  }

  @NotNull
  @Contract(pure = true)
  public static <K, V> Map<K, V> newMapFromValues(@NotNull Iterator<? extends V> values, @NotNull Convertor<? super V, ? extends K> keyConvertor) {
    Map<K, V> map = new HashMap<K, V>();
    fillMapWithValues(map, values, keyConvertor);
    return map;
  }

  public static <K, V> void fillMapWithValues(@NotNull Map<? super K, ? super V> map,
                                              @NotNull Iterator<? extends V> values,
                                              @NotNull Convertor<? super V, ? extends K> keyConvertor) {
    while (values.hasNext()) {
      V value = values.next();
      map.put(keyConvertor.convert(value), value);
    }
  }

  @NotNull
  @Contract(pure = true)
  public static <K, V> Map<K, Set<V>> classify(@NotNull Iterator<? extends V> iterator, @NotNull Convertor<? super V, ? extends K> keyConvertor) {
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
  @Contract(pure=true)
  public static <T> Iterator<T> emptyIterator() {
    return EmptyIterator.getInstance();
  }

  @NotNull
  @Contract(pure=true)
  public static <T> Iterable<T> emptyIterable() {
    return EmptyIterable.getInstance();
  }

  @Nullable
  @Contract(pure=true)
  public static <T> T find(@NotNull T[] array, @NotNull Condition<? super T> condition) {
    for (T element : array) {
      if (condition.value(element)) return element;
    }
    return null;
  }

  public static <T> boolean process(@NotNull Iterable<? extends T> iterable, @NotNull Processor<? super T> processor) {
    for (final T t : iterable) {
      if (!processor.process(t)) {
        return false;
      }
    }
    return true;
  }

  public static <T> boolean process(@NotNull List<? extends T> list, @NotNull Processor<? super T> processor) {
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

  public static <T> boolean process(@NotNull Iterator<? extends T> iterator, @NotNull Processor<? super T> processor) {
    while (iterator.hasNext()) {
      if (!processor.process(iterator.next())) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  @Contract(pure=true)
  public static <T, V extends T> V find(@NotNull Iterable<? extends V> iterable, @NotNull Condition<? super T> condition) {
    return ContainerUtilRt.find(iterable, condition);
  }

  @Nullable
  @Contract(pure=true)
  public static <T> T find(@NotNull Iterable<? extends T> iterable, @NotNull final T equalTo) {
    return find(iterable, new Condition<T>() {
      @Override
      public boolean value(final T object) {
        return equalTo == object || equalTo.equals(object);
      }
    });
  }

  @Nullable
  @Contract(pure=true)
  public static <T> T find(@NotNull Iterator<? extends T> iterator, @NotNull final T equalTo) {
    return find(iterator, new Condition<T>() {
      @Override
      public boolean value(final T object) {
        return equalTo == object || equalTo.equals(object);
      }
    });
  }

  @Nullable
  public static <T, V extends T> V find(@NotNull Iterator<? extends V> iterator, @NotNull Condition<? super T> condition) {
    return ContainerUtilRt.find(iterator, condition);
  }

  @Nullable
  @Contract(pure = true)
  public static <T, V extends T> V findLast(@NotNull List<? extends V> list, @NotNull Condition<? super T> condition) {
    int index = lastIndexOf(list, condition);
    if (index < 0) return null;
    return list.get(index);
  }

  @NotNull
  @Contract(pure=true)
  public static <T, K, V> Map<K, V> map2Map(@NotNull T[] collection, @NotNull Function<? super T, ? extends Pair<K, V>> mapper) {
    return map2Map(Arrays.asList(collection), mapper);
  }

  @NotNull
  @Contract(pure=true)
  public static <T, K, V> Map<K, V> map2Map(@NotNull Collection<? extends T> collection,
                                            @NotNull Function<? super T, ? extends Pair<K, V>> mapper) {
    final Map<K, V> set = new THashMap<K, V>(collection.size());
    for (T t : collection) {
      Pair<K, V> pair = mapper.fun(t);
      set.put(pair.first, pair.second);
    }
    return set;
  }

  @NotNull
  @Contract(pure = true)
  public static <T, K, V> Map<K, V> map2MapNotNull(@NotNull T[] collection,
                                                   @NotNull Function<? super T, ? extends Pair<K, V>> mapper) {
    return map2MapNotNull(Arrays.asList(collection), mapper);
  }

  @NotNull
  @Contract(pure = true)
  public static <T, K, V> Map<K, V> map2MapNotNull(@NotNull Collection<? extends T> collection,
                                                   @NotNull Function<? super T, ? extends Pair<K, V>> mapper) {
    final Map<K, V> set = new THashMap<K, V>(collection.size());
    for (T t : collection) {
      Pair<K, V> pair = mapper.fun(t);
      if (pair != null) {
        set.put(pair.first, pair.second);
      }
    }
    return set;
  }

  @NotNull
  @Contract(pure=true)
  public static <K, V> Map<K, V> map2Map(@NotNull Collection<? extends Pair<K, V>> collection) {
    final Map<K, V> result = new THashMap<K, V>(collection.size());
    for (Pair<K, V> pair : collection) {
      result.put(pair.first, pair.second);
    }
    return result;
  }

  @NotNull
  @Contract(pure=true)
  public static <T> Object[] map2Array(@NotNull T[] array, @NotNull Function<? super T, Object> mapper) {
    return map2Array(array, Object.class, mapper);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> Object[] map2Array(@NotNull Collection<? extends T> array, @NotNull Function<? super T, Object> mapper) {
    return map2Array(array, Object.class, mapper);
  }

  @NotNull
  @Contract(pure=true)
  public static <T, V> V[] map2Array(@NotNull T[] array, @NotNull Class<? super V> aClass, @NotNull Function<? super T, ? extends V> mapper) {
    @SuppressWarnings("unchecked") V[] result = (V[])Array.newInstance(aClass, array.length);
    for (int i = 0; i < array.length; i++) {
      result[i] = mapper.fun(array[i]);
    }
    return result;
  }

  @NotNull
  @Contract(pure=true)
  public static <T, V> V[] map2Array(@NotNull Collection<? extends T> collection, @NotNull Class<? super V> aClass, @NotNull Function<? super T, ? extends V> mapper) {
    @SuppressWarnings("unchecked") V[] result = (V[])Array.newInstance(aClass, collection.size());
    int i = 0;
    for (T t : collection) {
      result[i++] = mapper.fun(t);
    }
    return result;
  }

  @NotNull
  @Contract(pure=true)
  public static <T, V> V[] map2Array(@NotNull Collection<? extends T> collection, @NotNull V[] to, @NotNull Function<? super T, V> mapper) {
    return map2List(collection, mapper).toArray(to);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> List<T> filter(@NotNull T[] collection, @NotNull Condition<? super T> condition) {
    return findAll(collection, condition);
  }

  @NotNull
  @Contract(pure=true)
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
  @Contract(pure=true)
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
  @Contract(pure=true)
  public static <T> List<T> filter(@NotNull Collection<? extends T> collection, @NotNull Condition<? super T> condition) {
    return findAll(collection, condition);
  }

  @NotNull
  @Contract(pure = true)
  public static <K, V> Map<K, V> filter(@NotNull Map<? extends K, ? extends V> map, @NotNull Condition<? super K> keyFilter) {
    Map<K, V> result = new HashMap<K, V>();
    for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
      if (keyFilter.value(entry.getKey())) {
        result.put(entry.getKey(), entry.getValue());
      }
    }
    return result;
  }

  @NotNull
  @Contract(pure=true)
  public static <T> List<T> findAll(@NotNull Collection<? extends T> collection, @NotNull Condition<? super T> condition) {
    if (collection.isEmpty()) return emptyList();
    final List<T> result = new SmartList<T>();
    for (final T t : collection) {
      if (condition.value(t)) {
        result.add(t);
      }
    }
    return result;
  }

  @NotNull
  @Contract(pure=true)
  public static <T> List<T> skipNulls(@NotNull Collection<? extends T> collection) {
    return findAll(collection, Condition.NOT_NULL);
  }

  @NotNull
  @Contract(pure=true)
  public static <T, V> List<V> findAll(@NotNull T[] collection, @NotNull Class<V> instanceOf) {
    return findAll(Arrays.asList(collection), instanceOf);
  }

  @NotNull
  @Contract(pure=true)
  public static <T, V> V[] findAllAsArray(@NotNull T[] collection, @NotNull Class<V> instanceOf) {
    List<V> list = findAll(Arrays.asList(collection), instanceOf);
    @SuppressWarnings("unchecked") V[] array = (V[])Array.newInstance(instanceOf, list.size());
    return list.toArray(array);
  }

  @NotNull
  @Contract(pure=true)
  public static <T, V> V[] findAllAsArray(@NotNull Collection<? extends T> collection, @NotNull Class<V> instanceOf) {
    List<V> list = findAll(collection, instanceOf);
    @SuppressWarnings("unchecked") V[] array = (V[])Array.newInstance(instanceOf, list.size());
    return list.toArray(array);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> T[] findAllAsArray(@NotNull T[] collection, @NotNull Condition<? super T> instanceOf) {
    List<T> list = findAll(collection, instanceOf);
    if (list.size() == collection.length) {
      return collection;
    }
    @SuppressWarnings("unchecked") T[] array = (T[])Array.newInstance(collection.getClass().getComponentType(), list.size());
    return list.toArray(array);
  }

  @NotNull
  @Contract(pure=true)
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

  public static <T> boolean all(@NotNull Collection<? extends T> collection, @NotNull Condition<? super T> condition) {
    for (T t : collection) {
      if (!condition.value(t)) {
        return false;
      }
    }
    return true;
  }

  public static <T> void removeDuplicates(@NotNull Collection<T> collection) {
    Set<T> collected = new HashSet<T>();
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
  @Contract(pure=true)
  public static Map<String, String> stringMap(@NotNull final String... keyValues) {
    final Map<String, String> result = new HashMap<String, String>();
    for (int i = 0; i < keyValues.length - 1; i+=2) {
      result.put(keyValues[i], keyValues[i+1]);
    }

    return result;
  }

  @NotNull
  @Contract(pure=true)
  public static <T> Iterator<T> iterate(@NotNull T[] array) {
    return array.length == 0 ? EmptyIterator.<T>getInstance() : Arrays.asList(array).iterator();
  }

  @NotNull
  @Contract(pure=true)
  public static <T> Iterator<T> iterate(@NotNull final Enumeration<? extends T> enumeration) {
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
  @Contract(pure=true)
  public static <T> Iterable<T> iterate(@NotNull T[] arrays, @NotNull Condition<? super T> condition) {
    return iterate(Arrays.asList(arrays), condition);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> Iterable<T> iterate(@NotNull final Collection<? extends T> collection, @NotNull final Condition<? super T> condition) {
    if (collection.isEmpty()) return emptyIterable();
    return new Iterable<T>() {
      @NotNull
      @Override
      public Iterator<T> iterator() {
        return new Iterator<T>() {
          private final Iterator<? extends T> impl = collection.iterator();
          private T next = findNext();

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
  @Contract(pure=true)
  public static <T> Iterable<T> iterateBackward(@NotNull final List<? extends T> list) {
    return new Iterable<T>() {
      @NotNull
      @Override
      public Iterator<T> iterator() {
        return new Iterator<T>() {
          private final ListIterator<? extends T> it = list.listIterator(list.size());

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

  @NotNull
  @Contract(pure=true)
  public static <T, E> Iterable<Pair<T, E>> zip(@NotNull final Iterable<? extends T> iterable1, @NotNull final Iterable<? extends E> iterable2) {
    return new Iterable<Pair<T, E>>() {
      @NotNull
      @Override
      public Iterator<Pair<T, E>> iterator() {
        return new Iterator<Pair<T, E>>() {
          private final Iterator<? extends T> i1 = iterable1.iterator();
          private final Iterator<? extends E> i2 = iterable2.iterator();

          @Override
          public boolean hasNext() {
            return i1.hasNext() && i2.hasNext();
          }

          @Override
          public Pair<T, E> next() {
            return Pair.create(i1.next(), i2.next());
          }

          @Override
          public void remove() {
            i1.remove();
            i2.remove();
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

  public static <T> void addAll(@NotNull Collection<? super T> collection, @NotNull Enumeration<? extends T> enumeration) {
    while (enumeration.hasMoreElements()) {
      T element = enumeration.nextElement();
      collection.add(element);
    }
  }

  /**
   * Add all supplied elements to the supplied collection and returns the modified collection.
   * Unlike {@link Collections#addAll(Collection, Object[])} this method does not track whether collection
   * was modified, so it could be marginally faster.
   *
   * @param collection collection to add elements to
   * @param elements elements to add
   * @param <T> type of collection elements
   * @param <A> type of elements to add (subtype of collection elements)
   * @param <C> type of the collection
   * @return the collection passed as first argument
   */
  @SuppressWarnings({"UseBulkOperation", "ManualArrayToCollectionCopy"})
  @NotNull
  public static <T, A extends T, C extends Collection<T>> C addAll(@NotNull C collection, @NotNull A... elements) {
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

  // returns true if the collection was modified
  public static <T> boolean retainAll(@NotNull Collection<T> collection, @NotNull Condition<? super T> condition) {
    boolean modified = false;

    for (Iterator<T> iterator = collection.iterator(); iterator.hasNext(); ) {
      T next = iterator.next();
      if (!condition.value(next)) {
        iterator.remove();
        modified = true;
      }
    }

    return modified;
  }

  @Contract(pure=true)
  public static <T, U extends T> U findInstance(@NotNull Iterable<? extends T> iterable, @NotNull Class<? extends U> aClass) {
    return findInstance(iterable.iterator(), aClass);
  }

  public static <T, U extends T> U findInstance(@NotNull Iterator<? extends T> iterator, @NotNull Class<? extends U> aClass) {
    //noinspection unchecked
    return (U)find(iterator, FilteringIterator.instanceOf(aClass));
  }

  @Nullable
  @Contract(pure=true)
  public static <T, U extends T> U findInstance(@NotNull T[] array, @NotNull Class<? extends U> aClass) {
    return findInstance(Arrays.asList(array), aClass);
  }

  @NotNull
  @Contract(pure=true)
  public static <T, V> List<T> concat(@NotNull V[] array, @NotNull Function<? super V, ? extends Collection<? extends T>> fun) {
    return concat(Arrays.asList(array), fun);
  }

  /**
   * @return read-only list consisting of the elements from the collections stored in list added together
   */
  @NotNull
  @Contract(pure=true)
  public static <T> List<T> concat(@NotNull Iterable<? extends Collection<? extends T>> list) {
    int totalSize = 0;
    for (final Collection<? extends T> ts : list) {
      totalSize += ts.size();
    }
    List<T> result = new ArrayList<T>(totalSize);
    for (final Collection<? extends T> ts : list) {
      result.addAll(ts);
    }
    return result.isEmpty() ? Collections.<T>emptyList() : result;
  }

  @NotNull
  @Contract(pure=true)
  public static <T> List<T> append(@NotNull List<? extends T> list, @NotNull T... values) {
    return concat(list, list(values));
  }

  /**
   * prepend values in front of the list
   * @return read-only list consisting of values and the elements from specified list
   */
  @NotNull
  @Contract(pure=true)
  public static <T> List<T> prepend(@NotNull List<? extends T> list, @NotNull T... values) {
    return concat(list(values), list);
  }

  /**
   * @return read-only list consisting of the two lists added together
   */
  @NotNull
  @Contract(pure=true)
  public static <T> List<T> concat(@NotNull final List<? extends T> list1, @NotNull final List<? extends T> list2) {
    if (list1.isEmpty() && list2.isEmpty()) {
      return Collections.emptyList();
    }
    if (list1.isEmpty()) {
      //noinspection unchecked
      return (List<T>)list2;
    }
    if (list2.isEmpty()) {
      //noinspection unchecked
      return (List<T>)list1;
    }

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

  @SuppressWarnings("unchecked")
  @NotNull
  @Contract(pure=true)
  public static <T> Iterable<T> concat(@NotNull final Iterable<? extends T>... iterables) {
    if (iterables.length == 0) return emptyIterable();
    if (iterables.length == 1) return (Iterable<T>)iterables[0];
    return new Iterable<T>() {
      @NotNull
      @Override
      public Iterator<T> iterator() {
        Iterator[] iterators = new Iterator[iterables.length];
        for (int i = 0; i < iterables.length; i++) {
          Iterable<? extends T> iterable = iterables[i];
          iterators[i] = iterable.iterator();
        }
        return concatIterators(iterators);
      }
    };
  }

  @NotNull
  @Contract(pure=true)
  public static <T> Iterator<T> concatIterators(@NotNull Iterator<T>... iterators) {
    return new SequenceIterator<T>(iterators);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> Iterator<T> concatIterators(@NotNull Collection<? extends Iterator<? extends T>> iterators) {
    return new SequenceIterator<T>(iterators);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> Iterable<T> concat(@NotNull final T[]... iterables) {
    return new Iterable<T>() {
      @NotNull
      @Override
      public Iterator<T> iterator() {
        Iterator[] iterators = new Iterator[iterables.length];
        for (int i = 0; i < iterables.length; i++) {
          T[] iterable = iterables[i];
          iterators[i] = iterate(iterable);
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
  @Contract(pure=true)
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
            if (from <= index && index < from + each.size()) {
              return each.get(index - from);
            }
            from += each.size();
          }
          if (from != finalSize) {
            throw new ConcurrentModificationException("The list has changed. Its size was " + finalSize + "; now it's " + from);
          }
        }
        throw new IndexOutOfBoundsException("index: " + index + "; size: " + size());
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
  @Contract(pure=true)
  public static <T> List<T> concat(@NotNull final List<List<? extends T>> lists) {
    @SuppressWarnings("unchecked") List<? extends T>[] array = lists.toArray(new List[0]);
    return concat(array);
  }

  /**
   * @return read-only list consisting of the lists (made by listGenerator) added together
   */
  @NotNull
  @Contract(pure=true)
  public static <T, V> List<T> concat(@NotNull Iterable<? extends V> list, @NotNull Function<? super V, ? extends Collection<? extends T>> listGenerator) {
    List<T> result = new ArrayList<T>();
    for (final V v : list) {
      result.addAll(listGenerator.fun(v));
    }
    return result.isEmpty() ? ContainerUtil.<T>emptyList() : result;
  }

  @Contract(pure=true)
  public static <T> boolean intersects(@NotNull Collection<? extends T> collection1, @NotNull Collection<? extends T> collection2) {
    if (collection1.size() <= collection2.size()) {
      for (T t : collection1) {
        if (collection2.contains(t)) {
          return true;
        }
      }
    }
    else {
      for (T t : collection2) {
        if (collection1.contains(t)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * @return read-only collection consisting of elements from both collections
   */
  @NotNull
  @Contract(pure=true)
  public static <T> Collection<T> intersection(@NotNull Collection<? extends T> collection1, @NotNull Collection<? extends T> collection2) {
    List<T> result = new ArrayList<T>();
    for (T t : collection1) {
      if (collection2.contains(t)) {
        result.add(t);
      }
    }
    return result.isEmpty() ? ContainerUtil.<T>emptyList() : result;
  }

  @NotNull
  @Contract(pure=true)
  public static <E extends Enum<E>> EnumSet<E> intersection(@NotNull EnumSet<E> collection1, @NotNull EnumSet<E> collection2) {
    EnumSet<E> result = EnumSet.copyOf(collection1);
    result.retainAll(collection2);
    return result;
  }

  @Nullable
  @Contract(pure=true)
  public static <T> T getFirstItem(@Nullable Collection<? extends T> items) {
    return getFirstItem(items, null);
  }

  @Nullable
  @Contract(pure=true)
  public static <T> T getFirstItem(@Nullable List<? extends T> items) {
    return items == null || items.isEmpty() ? null : items.get(0);
  }

  @Contract(pure=true)
  public static <T> T getFirstItem(@Nullable final Collection<? extends T> items, @Nullable final T defaultResult) {
    return items == null || items.isEmpty() ? defaultResult : items.iterator().next();
  }

  /**
   * Returns the only item from the collection or null if collection is empty or contains more than one item
   *
   * @param items collection to get the item from
   * @param <T> type of collection element
   * @return the only collection element or null
   */
  @Nullable
  @Contract(pure=true)
  public static <T> T getOnlyItem(@Nullable final Collection<? extends T> items) {
    return getOnlyItem(items, null);
  }

  @Contract(pure=true)
  public static <T> T getOnlyItem(@Nullable final Collection<? extends T> items, @Nullable final T defaultResult) {
    return items == null || items.size() != 1 ? defaultResult : items.iterator().next();
  }

  /**
   * The main difference from {@code subList} is that {@code getFirstItems} does not
   * throw any exceptions, even if maxItems is greater than size of the list
   *
   * @param items list
   * @param maxItems size of the result will be equal or less than {@code maxItems}
   * @param <T> type of list
   * @return list with no more than {@code maxItems} first elements
   */
  @NotNull
  @Contract(pure=true)
  public static <T> List<T> getFirstItems(@NotNull final List<T> items, int maxItems) {
    if (maxItems < 0) {
      throw new IllegalArgumentException("Expected non-negative maxItems; got: "+maxItems);
    }
    return maxItems >= items.size() ? items : items.subList(0, maxItems);
  }

  @Nullable
  @Contract(pure=true)
  public static <T> T iterateAndGetLastItem(@NotNull Iterable<? extends T> items) {
    Iterator<? extends T> itr = items.iterator();
    T res = null;
    while (itr.hasNext()) {
      res = itr.next();
    }

    return res;
  }

  @NotNull
  @Contract(pure=true)
  public static <T,U> Iterator<U> mapIterator(@NotNull final Iterator<? extends T> iterator, @NotNull final Function<? super T, ? extends U> mapper) {
    return new Iterator<U>() {
      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public U next() {
        return mapper.fun(iterator.next());
      }

      @Override
      public void remove() {
        iterator.remove();
      }
    };
  }

  /**
   * @return iterator with elements from the original {@param iterator} which are valid according to {@param filter} predicate.
   */
  @NotNull
  @Contract(pure=true)
  public static <T> Iterator<T> filterIterator(@NotNull final Iterator<? extends T> iterator, @NotNull final Condition<? super T> filter) {
    return new Iterator<T>() {
      T next;
      boolean hasNext;
      {
        findNext();
      }
      @Override
      public boolean hasNext() {
        return hasNext;
      }

      private void findNext() {
        hasNext = false;
        while (iterator.hasNext()) {
          T t = iterator.next();
          if (filter.value(t)) {
            next = t;
            hasNext = true;
            break;
          }
        }
      }

      @Override
      public T next() {
        T result;
        if (hasNext) {
          result = next;
          findNext();
        }
        else {
          throw new NoSuchElementException();
        }
        return result;
      }

      @Override
      public void remove() {
        iterator.remove();
      }
    };
  }

  @Nullable
  @Contract(pure=true)
  public static <T, L extends List<T>> T getLastItem(@Nullable L list, @Nullable T def) {
    return ContainerUtilRt.getLastItem(list, def);
  }

  @Nullable
  @Contract(pure=true)
  public static <T, L extends List<T>> T getLastItem(@Nullable L list) {
    return ContainerUtilRt.getLastItem(list);
  }

  /**
   * @return read-only collection consisting of elements from the 'from' collection which are absent from the 'what' collection
   */
  @NotNull
  @Contract(pure=true)
  public static <T> Collection<T> subtract(@NotNull Collection<? extends T> from, @NotNull Collection<? extends T> what) {
    final Set<T> set = ContainerUtilRt.newHashSet(from);
    set.removeAll(what);
    return set.isEmpty() ? ContainerUtil.<T>emptyList() : set;
  }

  @NotNull
  @Contract(pure=true)
  public static <T> T[] toArray(@NotNull Collection<T> c, @NotNull ArrayFactory<? extends T> factory) {
    T[] a = factory.create(c.size());
    return c.toArray(a);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> T[] toArray(@NotNull Collection<? extends T> c1, @NotNull Collection<? extends T> c2, @NotNull ArrayFactory<? extends T> factory) {
    return ArrayUtil.mergeCollections(c1, c2, factory);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> T[] mergeCollectionsToArray(@NotNull Collection<? extends T> c1, @NotNull Collection<? extends T> c2, @NotNull ArrayFactory<? extends T> factory) {
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

  public static <T> void sort(@NotNull List<T> list, @NotNull Comparator<? super T> comparator) {
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

  @NotNull
  @Contract(pure=true)
  public static <T> List<T> sorted(@NotNull Collection<? extends T> list, @NotNull Comparator<? super T> comparator) {
    return sorted((Iterable<? extends T>)list, comparator);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> List<T> sorted(@NotNull Iterable<? extends T> list, @NotNull Comparator<? super T> comparator) {
    List<T> sorted = newArrayList(list);
    sort(sorted, comparator);
    return sorted;
  }

  @NotNull
  @Contract(pure=true)
  public static <T extends Comparable<? super T>> List<T> sorted(@NotNull Collection<? extends T> list) {
    return sorted(list, new Comparator<T>() {
      @Override
      public int compare(T o1, T o2) {
        return o1.compareTo(o2);
      }
    });
  }

  public static <T> void sort(@NotNull T[] a, @NotNull Comparator<? super T> comparator) {
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
   * @param iterable an input iterable to process
   * @param mapping a side-effect free function which transforms iterable elements
   * @return read-only list consisting of the elements from the iterable converted by mapping
   */
  @NotNull
  @Contract(pure=true)
  public static <T,V> List<V> map(@NotNull Iterable<? extends T> iterable, @NotNull Function<? super T, ? extends V> mapping) {
    List<V> result = new ArrayList<V>(iterable instanceof Collection ? ((Collection)iterable).size() : 10);
    for (T t : iterable) {
      result.add(mapping.fun(t));
    }
    return result.isEmpty() ? ContainerUtil.<V>emptyList() : result;
  }

  /**
   * @param collection an input collection to process
   * @param mapping a side-effect free function which transforms iterable elements
   * @return read-only list consisting of the elements from the input collection converted by mapping
   */
  @NotNull
  @Contract(pure=true)
  public static <T,V> List<V> map(@NotNull Collection<? extends T> collection, @NotNull Function<? super T, ? extends V> mapping) {
    return ContainerUtilRt.map2List(collection, mapping);
  }

  /**
   * @param array an input array to process
   * @param mapping a side-effect free function which transforms array elements
   * @return read-only list consisting of the elements from the input array converted by mapping with nulls filtered out
   */
  @NotNull
  @Contract(pure=true)
  public static <T, V> List<V> mapNotNull(@NotNull T[] array, @NotNull Function<? super T, ? extends V> mapping) {
    return mapNotNull(Arrays.asList(array), mapping);
  }

  /**
   * @param array an input array to process
   * @param mapping a side-effect free function which transforms array elements
   * @param emptyArray an empty array of desired result type (may be returned if the result is also empty)
   * @return array consisting of the elements from the input array converted by mapping with nulls filtered out
   */
  @NotNull
  @Contract(pure=true)
  public static <T, V> V[] mapNotNull(@NotNull T[] array, @NotNull Function<? super T, ? extends V> mapping, @NotNull V[] emptyArray) {
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
   * @param iterable an input iterable to process
   * @param mapping a side-effect free function which transforms iterable elements
   * @return read-only list consisting of the elements from the iterable converted by mapping with nulls filtered out
   */
  @NotNull
  @Contract(pure=true)
  public static <T, V> List<V> mapNotNull(@NotNull Iterable<? extends T> iterable, @NotNull Function<? super T, ? extends V> mapping) {
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
   * @param collection an input collection to process
   * @param mapping a side-effect free function which transforms collection elements
   * @return read-only list consisting of the elements from the array converted by mapping with nulls filtered out
   */
  @NotNull
  @Contract(pure=true)
  public static <T, V> List<V> mapNotNull(@NotNull Collection<? extends T> collection, @NotNull Function<? super T, ? extends V> mapping) {
    return ContainerUtilRt.mapNotNull(collection, mapping);
  }

  /**
   * @return read-only list consisting of the elements with nulls filtered out
   */
  @NotNull
  @Contract(pure=true)
  public static <T> List<T> packNullables(@NotNull T... elements) {
    List<T> list = new ArrayList<T>();
    for (T element : elements) {
      addIfNotNull(list, element);
    }
    return list.isEmpty() ? ContainerUtil.<T>emptyList() : list;
  }

  /**
   * @return read-only list consisting of the elements from the array converted by mapping
   */
  @NotNull
  @Contract(pure=true)
  public static <T, V> List<V> map(@NotNull T[] array, @NotNull Function<? super T, ? extends V> mapping) {
    List<V> result = new ArrayList<V>(array.length);
    for (T t : array) {
      result.add(mapping.fun(t));
    }
    return result.isEmpty() ? ContainerUtil.<V>emptyList() : result;
  }

  @NotNull
  @Contract(pure=true)
  public static <T, V> V[] map(@NotNull T[] arr, @NotNull Function<? super T, ? extends V> mapping, @NotNull V[] emptyArray) {
    if (arr.length==0) {
      assert emptyArray.length == 0 : "You must pass an empty array";
      return emptyArray;
    }

    V[] result = emptyArray.length < arr.length ? Arrays.copyOf(emptyArray, arr.length) : emptyArray;

    for (int i = 0; i < arr.length; i++) {
      result[i] = mapping.fun(arr[i]);
    }
    return result;
  }

  @NotNull
  @Contract(pure=true)
  public static <T> Set<T> set(@NotNull T ... items) {
    return ContainerUtilRt.newHashSet(items);
  }

  public static <K, V> void putIfAbsent(final K key, @Nullable V value, @NotNull final Map<? super K, ? super V> result) {
    if (!result.containsKey(key)) {
      result.put(key, value);
    }
  }

  public static <K, V> void putIfNotNull(final K key, @Nullable V value, @NotNull final Map<? super K, ? super V> result) {
    if (value != null) {
      result.put(key, value);
    }
  }

  public static <K, V> void putIfNotNull(final K key, @Nullable Collection<? extends V> value, @NotNull final MultiMap<? super K, ? super V> result) {
    if (value != null) {
      result.putValues(key, value);
    }
  }

  public static <K, V> void putIfNotNull(final K key, @Nullable V value, @NotNull final MultiMap<? super K, ? super V> result) {
    if (value != null) {
      result.putValue(key, value);
    }
  }

  public static <T> void add(final T element, @NotNull final Collection<? super T> result, @NotNull final Disposable parentDisposable) {
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
  @Contract(pure=true)
  public static <T> List<T> createMaybeSingletonList(@Nullable T element) {
    return element == null ? ContainerUtil.<T>emptyList() : Collections.singletonList(element);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> Set<T> createMaybeSingletonSet(@Nullable T element) {
    return element == null ? Collections.<T>emptySet() : Collections.singleton(element);
  }

  @NotNull
  public static <T, V> V getOrCreate(@NotNull Map<T, V> result, final T key, @NotNull V defaultValue) {
    V value = result.get(key);
    if (value == null) {
      result.put(key, value = defaultValue);
    }
    return value;
  }

  public static <T, V> V getOrCreate(@NotNull Map<T, V> result, final T key, @NotNull Factory<? extends V> factory) {
    V value = result.get(key);
    if (value == null) {
      result.put(key, value = factory.create());
    }
    return value;
  }

  @NotNull
  @Contract(pure=true)
  public static <T, V> V getOrElse(@NotNull Map<T, V> result, final T key, @NotNull V defValue) {
    V value = result.get(key);
    return value == null ? defValue : value;
  }

  @Contract(pure=true)
  public static <T> boolean and(@NotNull T[] iterable, @NotNull Condition<? super T> condition) {
    return and(Arrays.asList(iterable), condition);
  }

  @Contract(pure=true)
  public static <T> boolean and(@NotNull Iterable<? extends T> iterable, @NotNull Condition<? super T> condition) {
    for (final T t : iterable) {
      if (!condition.value(t)) return false;
    }
    return true;
  }

  @Contract(pure=true)
  public static <T> boolean exists(@NotNull T[] array, @NotNull Condition<? super T> condition) {
    for (final T t : array) {
      if (condition.value(t)) return true;
    }
    return false;
  }

  @Contract(pure=true)
  public static <T> boolean exists(@NotNull Iterable<? extends T> iterable, @NotNull Condition<? super T> condition) {
    return or(iterable, condition);
  }

  @Contract(pure=true)
  public static <T> boolean or(@NotNull T[] iterable, @NotNull Condition<? super T> condition) {
    return exists(iterable, condition);
  }

  @Contract(pure=true)
  public static <T> boolean or(@NotNull Iterable<? extends T> iterable, @NotNull Condition<? super T> condition) {
    for (final T t : iterable) {
      if (condition.value(t)) return true;
    }
    return false;
  }

  @Contract(pure=true)
  public static <T> int count(@NotNull Iterable<? extends T> iterable, @NotNull Condition<? super T> condition) {
    int count = 0;
    for (final T t : iterable) {
      if (condition.value(t)) count++;
    }
    return count;
  }

  @NotNull
  @Contract(pure=true)
  public static <T> List<T> unfold(@Nullable T t, @NotNull NullableFunction<? super T, ? extends T> next) {
    if (t == null) return emptyList();

    List<T> list = new ArrayList<T>();
    while (t != null) {
      list.add(t);
      t = next.fun(t);
    }
    return list;
  }

  @NotNull
  @Contract(pure=true)
  public static <T> List<T> dropTail(@NotNull List<T> items) {
    return items.subList(0, items.size() - 1);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> List<T> list(@NotNull T... items) {
    return Arrays.asList(items);
  }

  // Generalized Quick Sort. Does neither array.clone() nor list.toArray()

  public static <T> void quickSort(@NotNull List<? extends T> list, @NotNull Comparator<? super T> comparator) {
    quickSort(list, comparator, 0, list.size());
  }

  private static <T> void quickSort(@NotNull List<? extends T> x, @NotNull Comparator<? super T> comparator, int off, int len) {
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
  private static <T> int med3(@NotNull List<? extends T> x, Comparator<? super T> comparator, int a, int b, int c) {
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
   * @return read-only set consisting of the only element o
   */
  @NotNull
  @Contract(pure=true)
  public static <T> Set<T> singleton(final T o, @NotNull final TObjectHashingStrategy<T> strategy) {
    return strategy == TObjectHashingStrategy.CANONICAL ? new SingletonSet<T>(o) : SingletonSet.withCustomStrategy(o, strategy);
  }

  /**
   * @return read-only list consisting of the elements from all of the collections
   */
  @NotNull
  @Contract(pure=true)
  public static <E> List<E> flatten(@NotNull Collection<E>[] collections) {
    return flatten(Arrays.asList(collections));
  }

  /**
   * Processes the list, remove all duplicates and return the list with unique elements.
   * @param list must be sorted (according to the comparator), all elements must be not-null
   */
  @NotNull
  public static <T> List<T> removeDuplicatesFromSorted(@NotNull List<T> list, @NotNull Comparator<? super T> comparator) {
    T prev = null;
    List<T> result = null;
    for (int i = 0; i < list.size(); i++) {
      T t = list.get(i);
      if (t == null) {
        throw new IllegalArgumentException("get(" + i + ") = null");
      }
      int cmp = prev == null ? -1 : comparator.compare(prev, t);
      if (cmp < 0) {
        if (result != null) result.add(t);
      }
      else if (cmp == 0) {
        if (result == null) {
          result = new ArrayList<T>(list.size());
          result.addAll(list.subList(0, i));
        }
      }
      else {
        throw new IllegalArgumentException("List must be sorted but get(" + (i - 1) + ")=" + list.get(i - 1) + " > get(" + i + ")=" + t);
      }
      prev = t;
    }
    return result == null ? list : result;
  }

  /**
   * @return read-only list consisting of the elements from all of the collections
   */
  @NotNull
  @Contract(pure=true)
  public static <E> List<E> flatten(@NotNull Iterable<? extends Collection<? extends E>> collections) {
    int totalSize = 0;
    for (Collection<? extends E> list : collections) {
      totalSize += list.size();
    }
    List<E> result = new ArrayList<E>(totalSize);
    for (Collection<? extends E> list : collections) {
      result.addAll(list);
    }

    return result.isEmpty() ? ContainerUtil.<E>emptyList() : result;
  }

  /**
   * @return read-only list consisting of the elements from all of the collections
   */
  @NotNull
  @Contract(pure=true)
  public static <E> List<E> flattenIterables(@NotNull Iterable<? extends Iterable<E>> collections) {
    int totalSize = 0;
    for (Iterable<E> list : collections) {
      totalSize += list instanceof Collection ? ((Collection)list).size() : 10;
    }
    List<E> result = new ArrayList<E>(totalSize);
    for (Iterable<E> list : collections) {
      for (E e : list) {
        result.add(e);
      }
    }
    return result.isEmpty() ? ContainerUtil.<E>emptyList() : result;
  }

  @NotNull
  public static <K,V> V[] convert(@NotNull K[] from, @NotNull V[] to, @NotNull Function<? super K, ? extends V> fun) {
    if (to.length < from.length) {
      @SuppressWarnings("unchecked") V[] array = (V[])Array.newInstance(to.getClass().getComponentType(), from.length);
      to = array;
    }
    for (int i = 0; i < from.length; i++) {
      to[i] = fun.fun(from[i]);
    }
    return to;
  }

  @Contract(pure=true)
  public static <T> boolean containsIdentity(@NotNull Iterable<? extends T> list, T element) {
    for (T t : list) {
      if (t == element) {
        return true;
      }
    }
    return false;
  }

  @Contract(pure=true)
  public static <T> int indexOfIdentity(@NotNull List<? extends T> list, T element) {
    for (int i = 0, listSize = list.size(); i < listSize; i++) {
      if (list.get(i) == element) {
        return i;
      }
    }
    return -1;
  }

  @Contract(pure=true)
  public static <T> boolean equalsIdentity(@NotNull List<? extends T> list1, @NotNull List<? extends T> list2) {
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

  @Contract(pure=true)
  public static <T> int indexOf(@NotNull List<? extends T> list, @NotNull Condition<? super T> condition) {
    return ContainerUtilRt.indexOf(list, condition);
  }

  @Contract(pure=true)
  public static <T> int lastIndexOf(@NotNull List<? extends T> list, @NotNull Condition<? super T> condition) {
    for (int i = list.size() - 1; i >= 0; i--) {
      T t = list.get(i);
      if (condition.value(t)) {
        return i;
      }
    }
    return -1;
  }

  @Nullable
  @Contract(pure = true)
  public static <T, U extends T> U findLastInstance(@NotNull List<? extends T> list, @NotNull final Class<? extends U> clazz) {
    int i = lastIndexOf(list, new Condition<T>() {
      @Override
      public boolean value(T t) {
        return clazz.isInstance(t);
      }
    });
    //noinspection unchecked
    return i < 0 ? null : (U)list.get(i);
  }

  @Contract(pure = true)
  public static <T, U extends T> int lastIndexOfInstance(@NotNull List<? extends T> list, @NotNull final Class<U> clazz) {
    return lastIndexOf(list, new Condition<T>() {
      @Override
      public boolean value(T t) {
        return clazz.isInstance(t);
      }
    });
  }

  @NotNull
  @Contract(pure=true)
  public static <A,B> Map<B,A> reverseMap(@NotNull Map<A,B> map) {
    final Map<B,A> result = new HashMap<B, A>();
    for (Map.Entry<A, B> entry : map.entrySet()) {
      result.put(entry.getValue(), entry.getKey());
    }
    return result;
  }

  @Contract("null -> null; !null -> !null")
  public static <T> List<T> trimToSize(@Nullable List<T> list) {
    if (list == null) return null;
    if (list.isEmpty()) return emptyList();

    if (list instanceof ArrayList) {
      ((ArrayList)list).trimToSize();
    }

    return list;
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <T> Stack<T> newStack() {
    return ContainerUtilRt.newStack();
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> Stack<T> newStack(@NotNull Collection<? extends T> initial) {
    return ContainerUtilRt.newStack(initial);
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> Stack<T> newStack(@NotNull T... initial) {
    return ContainerUtilRt.newStack(initial);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> List<T> emptyList() {
    return ContainerUtilRt.emptyList();
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <T> CopyOnWriteArrayList<T> createEmptyCOWList() {
    // does not create garbage new Object[0]
    return new CopyOnWriteArrayList<T>(ContainerUtilRt.<T>emptyList());
  }

  /**
   * Creates List which is thread-safe to modify and iterate.
   * It differs from the java.util.concurrent.CopyOnWriteArrayList in the following:
   * - faster modification in the uncontended case
   * - less memory
   * - slower modification in highly contented case (which is the kind of situation you shouldn't use COWAL anyway)
   *
   * N.B. Avoid using {@code list.toArray(new T[list.size()])} on this list because it is inherently racey and
   * therefore can return array with null elements at the end.
   */
  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <T> List<T> createLockFreeCopyOnWriteList() {
    return createConcurrentList();
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> List<T> createLockFreeCopyOnWriteList(@NotNull Collection<? extends T> c) {
    return new LockFreeCopyOnWriteArrayList<T>(c);
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <V> ConcurrentIntObjectMap<V> createConcurrentIntObjectMap() {
    return new ConcurrentIntObjectHashMap<V>();
  }

  @NotNull
  @Contract(value = "_,_,_ -> new", pure = true)
  public static <V> ConcurrentIntObjectMap<V> createConcurrentIntObjectMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
    return new ConcurrentIntObjectHashMap<V>(initialCapacity, loadFactor, concurrencyLevel);
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <V> ConcurrentIntObjectMap<V> createConcurrentIntObjectSoftValueMap() {
    return new ConcurrentIntKeySoftValueHashMap<V>();
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <V> ConcurrentLongObjectMap<V> createConcurrentLongObjectMap() {
    return new ConcurrentLongObjectHashMap<V>();
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <V> ConcurrentLongObjectMap<V> createConcurrentLongObjectMap(int initialCapacity) {
    return new ConcurrentLongObjectHashMap<V>(initialCapacity);
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <K,V> ConcurrentMap<K,V> createConcurrentWeakValueMap() {
    return new ConcurrentWeakValueHashMap<K, V>();
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <V> ConcurrentIntObjectMap<V> createConcurrentIntObjectWeakValueMap() {
    return new ConcurrentIntKeyWeakValueHashMap<V>();
  }

  @NotNull
  @Contract(value = "_,_,_,_ -> new", pure = true)
  public static <K,V> ConcurrentMap<K,V> createConcurrentWeakKeySoftValueMap(int initialCapacity,
                                                                             float loadFactor,
                                                                             int concurrencyLevel,
                                                                             @NotNull final TObjectHashingStrategy<? super K> hashingStrategy) {
    //noinspection deprecation
    return new ConcurrentWeakKeySoftValueHashMap<K, V>(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
  }

  @NotNull
  @Contract(value = "_,_,_,_ -> new", pure = true)
  public static <K,V> ConcurrentMap<K,V> createConcurrentSoftKeySoftValueMap(int initialCapacity,
                                                                             float loadFactor,
                                                                             int concurrencyLevel,
                                                                             @NotNull final TObjectHashingStrategy<? super K> hashingStrategy) {
    return new ConcurrentSoftKeySoftValueHashMap<K, V>(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <K,V> ConcurrentMap<K,V> createConcurrentWeakKeySoftValueMap() {
    return createConcurrentWeakKeySoftValueMap(100, 0.75f, Runtime.getRuntime().availableProcessors(), ContainerUtil.<K>canonicalStrategy());
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <K,V> ConcurrentMap<K,V> createConcurrentWeakKeyWeakValueMap() {
    return createConcurrentWeakKeyWeakValueMap(ContainerUtil.<K>canonicalStrategy());
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <K,V> ConcurrentMap<K,V> createConcurrentWeakKeyWeakValueMap(@NotNull TObjectHashingStrategy<? super K> strategy) {
    return new ConcurrentWeakKeyWeakValueHashMap<K, V>(100, 0.75f, Runtime.getRuntime().availableProcessors(),
                                                       strategy);
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <K, V> ConcurrentMap<K,V> createConcurrentSoftValueMap() {
    return new ConcurrentSoftValueHashMap<K, V>();
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <K,V> ConcurrentMap<K,V> createConcurrentSoftMap() {
    return new ConcurrentSoftHashMap<K, V>();
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <K,V> ConcurrentMap<K,V> createConcurrentWeakMap() {
    //noinspection deprecation
    return new ConcurrentWeakHashMap<K, V>(0.75f);
  }

  @NotNull
  @Contract(value = "_,_,_,_ -> new", pure = true)
  public static <K,V> ConcurrentMap<K,V> createConcurrentSoftMap(int initialCapacity,
                                                                 float loadFactor,
                                                                 int concurrencyLevel,
                                                                 @NotNull TObjectHashingStrategy<? super K> hashingStrategy) {
    return new ConcurrentSoftHashMap<K, V>(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
  }

  @NotNull
  @Contract(value = "_,_,_,_ -> new", pure = true)
  public static <K,V> ConcurrentMap<K,V> createConcurrentWeakMap(int initialCapacity,
                                                                 float loadFactor,
                                                                 int concurrencyLevel,
                                                                 @NotNull TObjectHashingStrategy<? super K> hashingStrategy) {
    //noinspection deprecation
    return new ConcurrentWeakHashMap<K, V>(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <K,V> ConcurrentMap<K,V> createConcurrentWeakMap(@NotNull TObjectHashingStrategy<? super K> hashingStrategy) {
    //noinspection deprecation
    return new ConcurrentWeakHashMap<K, V>(hashingStrategy);
  }

  /**
   * @see #createLockFreeCopyOnWriteList()
   */
  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <T> ConcurrentList<T> createConcurrentList() {
    return new LockFreeCopyOnWriteArrayList<T>();
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> ConcurrentList<T> createConcurrentList(@NotNull Collection <? extends T> collection) {
    return new LockFreeCopyOnWriteArrayList<T>(collection);
  }

  /**
   * @see #addIfNotNull(Collection, Object) instead
   */
  @Deprecated
  public static <T> void addIfNotNull(@Nullable T element, @NotNull Collection<? super T> result) {
    addIfNotNull(result,element);
  }

  public static <T> void addIfNotNull(@NotNull Collection<? super T> result, @Nullable T element) {
    ContainerUtilRt.addIfNotNull(result, element);
  }

  @NotNull
  @Contract(pure=true)
  public static <T, V> List<V> map2List(@NotNull T[] array, @NotNull Function<? super T, ? extends V> mapper) {
    return ContainerUtilRt.map2List(array, mapper);
  }

  @NotNull
  @Contract(pure=true)
  public static <T, V> List<V> map2List(@NotNull Collection<? extends T> collection, @NotNull Function<? super T, ? extends V> mapper) {
    return ContainerUtilRt.map2List(collection, mapper);
  }

  @NotNull
  @Contract(pure=true)
  public static <K, V> List<Pair<K, V>> map2List(@NotNull Map<? extends K, ? extends V> map) {
    return ContainerUtilRt.map2List(map);
  }

  @NotNull
  @Contract(pure=true)
  public static <T, V> Set<V> map2Set(@NotNull T[] collection, @NotNull Function<? super T, ? extends V> mapper) {
    return ContainerUtilRt.map2Set(collection, mapper);
  }

  @NotNull
  @Contract(pure=true)
  public static <T, V> Set<V> map2Set(@NotNull Collection<? extends T> collection, @NotNull Function<? super T, ? extends V> mapper) {
    return ContainerUtilRt.map2Set(collection, mapper);
  }

  @NotNull
  @Contract(pure=true)
  public static <T, V> Set<V> map2LinkedSet(@NotNull Collection<? extends T> collection, @NotNull Function<? super T, ? extends V> mapper) {
    if (collection.isEmpty()) return Collections.emptySet();
    Set <V> set = new LinkedHashSet<V>(collection.size());
    for (final T t : collection) {
      set.add(mapper.fun(t));
    }
    return set;
  }

  @NotNull
  @Contract(pure=true)
  public static <T, V> Set<V> map2SetNotNull(@NotNull Collection<? extends T> collection, @NotNull Function<? super T, ? extends V> mapper) {
    if (collection.isEmpty()) return Collections.emptySet();
    Set <V> set = new HashSet<V>(collection.size());
    for (T t : collection) {
      V value = mapper.fun(t);
      if (value != null) {
        set.add(value);
      }
    }
    return set.isEmpty() ? Collections.<V>emptySet() : set;
  }

  /**
   * @deprecated use {@link List#toArray(Object[])} instead
   */
  @Deprecated
  @NotNull
  @Contract(pure=true)
  public static <T> T[] toArray(@NotNull List<T> collection, @NotNull T[] array) {
    return collection.toArray(array);
  }

  /**
   * @deprecated use {@link Collection#toArray(Object[])} instead
   */
  @Deprecated
  @NotNull
  @Contract(pure=true)
  public static <T> T[] toArray(@NotNull Collection<? extends T> c, @NotNull T[] sample) {
    return c.toArray(sample);
  }

  @NotNull
  public static <T> T[] copyAndClear(@NotNull Collection<? extends T> collection, @NotNull ArrayFactory<? extends T> factory, boolean clear) {
    int size = collection.size();
    T[] a = factory.create(size);
    if (size > 0) {
      a = collection.toArray(a);
      if (clear) collection.clear();
    }
    return a;
  }

  @Contract("null -> null")
  public static <T> List<T> copyList(@Nullable List<? extends T> list) {
    if (list == null) {
      return null;
    }
    else if (list == Collections.emptyList()) {
      return Collections.emptyList();
    }
    else if (list.size() == 1) {
      return new SmartList<T>(list.get(0));
    }
    else if (list.isEmpty()) {
      return new SmartList<T>();
    }
    else {
      return new ArrayList<T>(list);
    }
  }

  @NotNull
  @Contract(pure=true)
  public static <T> Collection<T> toCollection(@NotNull Iterable<? extends T> iterable) {
    //noinspection unchecked
    return iterable instanceof Collection ? (Collection<T>)iterable : newArrayList(iterable);
  }

  @NotNull
  public static <T> List<T> toList(@NotNull Enumeration<? extends T> enumeration) {
    if (!enumeration.hasMoreElements()) {
      return Collections.emptyList();
    }

    List<T> result = new SmartList<T>();
    while (enumeration.hasMoreElements()) {
      result.add(enumeration.nextElement());
    }
    return result;
  }

  @Contract(value = "null -> true", pure = true)
  public static <T> boolean isEmpty(@Nullable Collection<? extends T> collection) {
    return ContainerUtilRt.isEmpty(collection);
  }

  @Contract(value = "null -> true", pure = true)
  public static boolean isEmpty(@Nullable Map map) {
    return map == null || map.isEmpty();
  }

  @NotNull
  @Contract(pure=true)
  public static <T> List<T> notNullize(@Nullable List<T> list) {
    return list == null ? ContainerUtilRt.<T>emptyList() : list;
  }

  @NotNull
  @Contract(pure=true)
  public static <T> Set<T> notNullize(@Nullable Set<T> set) {
    return set == null ? Collections.<T>emptySet() : set;
  }

  @NotNull
  @Contract(pure = true)
  public static <K, V> Map<K, V> notNullize(@Nullable Map<K, V> map) {
    return map == null ? Collections.<K, V>emptyMap() : map;
  }

  @Contract(pure = true)
  public static <T> boolean startsWith(@NotNull List<? extends T> list, @NotNull List<? extends T> prefix) {
    return list.size() >= prefix.size() && list.subList(0, prefix.size()).equals(prefix);
  }

  @Nullable
  @Contract(pure=true)
  public static <T, C extends Collection<T>> C nullize(@Nullable C collection) {
    return isEmpty(collection) ? null : collection;
  }

  @Contract(pure=true)
  public static <T extends Comparable<T>> int compareLexicographically(@NotNull List<? extends T> o1, @NotNull List<? extends T> o2) {
    for (int i = 0; i < Math.min(o1.size(), o2.size()); i++) {
      int result = Comparing.compare(o1.get(i), o2.get(i));
      if (result != 0) {
        return result;
      }
    }
    return o1.size() < o2.size() ? -1 : o1.size() == o2.size() ? 0 : 1;
  }

  @Contract(pure=true)
  public static <T> int compareLexicographically(@NotNull List<? extends T> o1, @NotNull List<? extends T> o2, @NotNull Comparator<? super T> comparator) {
    for (int i = 0; i < Math.min(o1.size(), o2.size()); i++) {
      int result = comparator.compare(o1.get(i), o2.get(i));
      if (result != 0) {
        return result;
      }
    }
    return o1.size() < o2.size() ? -1 : o1.size() == o2.size() ? 0 : 1;
  }

  /**
   * Returns a String representation of the given map, by listing all key-value pairs contained in the map.
   */
  @NotNull
  @Contract(pure = true)
  public static String toString(@NotNull Map<?, ?> map) {
    StringBuilder sb = new StringBuilder("{");
    for (Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator(); iterator.hasNext(); ) {
      Map.Entry<?, ?> entry = iterator.next();
      sb.append(entry.getKey()).append('=').append(entry.getValue());
      if (iterator.hasNext()) {
        sb.append(", ");
      }
    }
    sb.append('}');
    return sb.toString();
  }

  public static class KeyOrderedMultiMap<K extends Comparable<K>, V> extends MultiMap<K, V> {
    public KeyOrderedMultiMap() {
    }

    public KeyOrderedMultiMap(@NotNull MultiMap<? extends K, ? extends V> toCopy) {
      super(toCopy);
    }

    @NotNull
    @Override
    protected Map<K, Collection<V>> createMap() {
      return new TreeMap<K, Collection<V>>();
    }

    @NotNull
    @Override
    protected Map<K, Collection<V>> createMap(int initialCapacity, float loadFactor) {
      return new TreeMap<K, Collection<V>>();
    }

    @NotNull
    public NavigableSet<K> navigableKeySet() {
      return ((TreeMap<K, Collection<V>>)myMap).navigableKeySet();
    }
  }

  @Contract(value = " -> new", pure = true)
  @NotNull
  public static <K,V> Map<K,V> createWeakKeySoftValueMap() {
    return new WeakKeySoftValueHashMap<K, V>();
  }

  @Contract(value = " -> new", pure = true)
  @NotNull
  public static <K,V> Map<K,V> createWeakKeyWeakValueMap() {
    //noinspection deprecation
    return new WeakKeyWeakValueHashMap<K, V>(true);
  }

  @Contract(value = " -> new", pure = true)
  @NotNull
  public static <K,V> Map<K,V> createSoftKeySoftValueMap() {
    return new SoftKeySoftValueHashMap<K, V>(true);
  }

  /**
   * Hard keys soft values hash map.
   * Null keys are NOT allowed
   * Null values are allowed
   */
  @Contract(value = " -> new", pure = true)
  @NotNull
  public static <K,V> Map<K,V> createSoftValueMap() {
    //noinspection deprecation
    return new SoftValueHashMap<K, V>(ContainerUtil.<K>canonicalStrategy());
  }

  /**
   * Hard keys weak values hash map.
   * Null keys are NOT allowed
   * Null values are allowed
   */
  @Contract(value = " -> new", pure = true)
  @NotNull
  public static <K,V> Map<K,V> createWeakValueMap() {
    //noinspection deprecation
    return new WeakValueHashMap<K, V>(ContainerUtil.<K>canonicalStrategy());
  }

  /**
   * Soft keys hard values hash map.
   * Null keys are NOT allowed
   * Null values are allowed
   */
  @Contract(value = " -> new", pure = true)
  @NotNull
  public static <K,V> Map<K,V> createSoftMap() {
    //noinspection deprecation
    return new SoftHashMap<K, V>(4);
  }

  @Contract(value = "_ -> new", pure = true)
  @NotNull
  public static <K,V> Map<K,V> createSoftMap(@NotNull TObjectHashingStrategy<? super K> strategy) {
    //noinspection deprecation
    return new SoftHashMap<K, V>(strategy);
  }

  /**
   * Weak keys hard values hash map.
   * Null keys are NOT allowed
   * Null values are allowed
   */
  @Contract(value = " -> new", pure = true)
  @NotNull
  public static <K,V> Map<K,V> createWeakMap() {
    return createWeakMap(4);
  }

  @Contract(value = "_ -> new", pure = true)
  @NotNull
  public static <K,V> Map<K,V> createWeakMap(int initialCapacity) {
    return createWeakMap(initialCapacity, 0.8f, ContainerUtil.<K>canonicalStrategy());
  }

  @Contract(value = "_, _, _ -> new", pure = true)
  @NotNull
  public static <K,V> Map<K,V> createWeakMap(int initialCapacity, float loadFactor, @NotNull TObjectHashingStrategy<? super K> strategy) {
    //noinspection deprecation
    return new WeakHashMap<K, V>(initialCapacity, loadFactor, strategy);
  }

  @Contract(value = " -> new", pure = true)
  @NotNull
  public static <T> Set<T> createWeakSet() {
    return new WeakHashSet<T>();
  }

  @Contract(value = " -> new", pure = true)
  @NotNull
  public static <T> IntObjectMap<T> createIntKeyWeakValueMap() {
    return new IntKeyWeakValueHashMap<T>();
  }

  @Contract(value = " -> new", pure = true)
  @NotNull
  public static <T> ObjectIntMap<T> createWeakKeyIntValueMap() {
    return new WeakKeyIntValueHashMap<T>();
  }
}

