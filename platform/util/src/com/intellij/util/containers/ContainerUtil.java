// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.*;
import com.intellij.util.*;
import gnu.trove.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntFunction;

@SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
@ApiStatus.NonExtendable
// cannot be final because of https://plugins.jetbrains.com/plugin/7831-illuminated-cloud
public class ContainerUtil {
  private static final int INSERTION_SORT_THRESHOLD = 10;

  @SafeVarargs
  @Contract(pure=true)
  public static <T> T @NotNull [] ar(T @NotNull ... elements) {
    return elements;
  }

  /**
   * @deprecated Use {@link HashMap#HashMap()}
   */
  @Contract(pure = true)
  @Deprecated
  public static @NotNull <K, V> HashMap<K, V> newHashMap() {
    return new HashMap<>();
  }

  /**
   * @deprecated Use {@link HashMap#HashMap(Map)}
   */
  @Contract(pure = true)
  @Deprecated
  public static @NotNull <K, V> HashMap<K, V> newHashMap(@NotNull Map<? extends K, ? extends V> map) {
    return new HashMap<>(map);
  }

  @SafeVarargs
  @Contract(pure = true)
  public static @NotNull <K, V> Map<K, V> newHashMap(@NotNull Pair<? extends K, ? extends V> first, Pair<? extends K,? extends V> @NotNull ... entries) {
    Map<K, V> map = new HashMap<>(entries.length + 1);
    map.put(first.getFirst(), first.getSecond());
    for (Pair<? extends K, ? extends V> entry : entries) {
      map.put(entry.getFirst(), entry.getSecond());
    }
    return map;
  }

  @Contract(pure = true)
  public static @NotNull <K, V> Map<K, V> newHashMap(@NotNull List<? extends K> keys, @NotNull List<? extends V> values) {
    if (keys.size() != values.size()) {
      throw new IllegalArgumentException(keys + " must have same length as " + values);
    }

    Map<K, V> map = new HashMap<>(keys.size());
    for (int i = 0; i < keys.size(); ++i) {
      map.put(keys.get(i), values.get(i));
    }
    return map;
  }

  /**
   * @deprecated Use {@link TreeMap#TreeMap()}
   */
  @Contract(pure = true)
  @Deprecated
  public static @NotNull <K extends Comparable<? super K>, V> TreeMap<K, V> newTreeMap() {
    return new TreeMap<>();
  }

  /**
   * @deprecated Use {@link TreeMap#TreeMap(Map)}
   */
  @SuppressWarnings("unused")
  @Contract(pure = true)
  @Deprecated
  public static @NotNull <K extends Comparable<? super K>, V> TreeMap<K, V> newTreeMap(@NotNull Map<? extends K, ? extends V> map) {
    return new TreeMap<>(map);
  }

  /**
   * @deprecated Use {@link LinkedHashMap#LinkedHashMap()}
   */
  @Contract(pure = true)
  @Deprecated
  public static @NotNull <K, V> LinkedHashMap<K, V> newLinkedHashMap() {
    return new LinkedHashMap<>();
  }

  /**
   * @deprecated Use {@link LinkedHashMap#LinkedHashMap(Map)}
   */
  @Contract(pure = true)
  @Deprecated
  public static @NotNull <K, V> LinkedHashMap<K, V> newLinkedHashMap(@NotNull Map<? extends K, ? extends V> map) {
    return new LinkedHashMap<>(map);
  }

  @SafeVarargs
  @Contract(pure = true)
  public static @NotNull <K, V> LinkedHashMap<K, V> newLinkedHashMap(@NotNull Pair<? extends K, ? extends V> first, Pair<? extends K,? extends V> @NotNull ... entries) {
    LinkedHashMap<K, V> map = new LinkedHashMap<>();
    map.put(first.getFirst(), first.getSecond());
    for (Pair<? extends K, ? extends V> entry : entries) {
      map.put(entry.getFirst(), entry.getSecond());
    }
    return map;
  }

  /**
   * @deprecated Use {@link THashMap#THashMap(Map)}
   */
  @Deprecated
  @Contract(pure = true)
  public static @NotNull <K, V> THashMap<K, V> newTroveMap() {
    return new THashMap<>();
  }

  /**
   * @deprecated Use {@link THashMap#THashMap(TObjectHashingStrategy)}
   */
  @Deprecated
  @Contract(pure = true)
  public static @NotNull <K, V> THashMap<K, V> newTroveMap(@NotNull TObjectHashingStrategy<K> strategy) {
    return new THashMap<>(strategy);
  }

  @Contract(pure = true)
  public static @NotNull <T> TObjectHashingStrategy<T> canonicalStrategy() {
    //noinspection unchecked
    return TObjectHashingStrategy.CANONICAL;
  }

  @Contract(pure = true)
  public static @NotNull <T> TObjectHashingStrategy<T> identityStrategy() {
    //noinspection unchecked
    return TObjectHashingStrategy.IDENTITY;
  }

  /**
   * @deprecated Use {@link IdentityHashMap#IdentityHashMap()}
   */
  @Deprecated
  public static @NotNull <K, V> IdentityHashMap<K, V> newIdentityHashMap() {
    return new IdentityHashMap<>();
  }

  /**
   * @deprecated Use {@link LinkedList#LinkedList()}
   */
  @Contract(pure = true)
  @Deprecated
  public static @NotNull <T> LinkedList<T> newLinkedList() {
    return new LinkedList<>();
  }

  @SafeVarargs
  @Contract(pure = true)
  public static @NotNull <T> LinkedList<T> newLinkedList(T @NotNull ... elements) {
    final LinkedList<T> list = new LinkedList<>();
    Collections.addAll(list, elements);
    return list;
  }

  /**
   * @deprecated Use {@link ArrayList#ArrayList()}
   */
  @Deprecated
  @Contract(pure = true)
  public static @NotNull <T> ArrayList<T> newArrayList() {
    return new ArrayList<>();
  }

  @SafeVarargs
  @Contract(pure = true)
  public static @NotNull <E> ArrayList<E> newArrayList(E @NotNull ... array) {
    return new ArrayList<>(Arrays.asList(array));
  }

  /**
   * @deprecated Use {@link ArrayList#ArrayList(Collection)} instead
   */
  @Deprecated
  @Contract(pure = true)
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  public static @NotNull <E> ArrayList<E> newArrayList(@NotNull Collection<? extends E> iterable) {
    DeprecatedMethodException.report("Use `new ArrayList(Collection)` instead. "+iterable.getClass());
    return new ArrayList<>(iterable);
  }

  @Contract(pure = true)
  public static @NotNull <E> ArrayList<E> newArrayList(@NotNull Iterable<? extends E> iterable) {
    return ContainerUtilRt.copy(new ArrayList<>(), iterable);
  }

  /**
   * @deprecated Use {@link ArrayList#ArrayList(int)}
   */
  @Contract(pure = true)
  @Deprecated
  public static @NotNull <T> ArrayList<T> newArrayListWithCapacity(int size) {
    return new ArrayList<>(size);
  }

  @Contract(pure = true)
  public static @NotNull <T> List<T> newArrayList(final T @NotNull [] elements, final int start, final int end) {
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

  @Contract(pure = true)
  public static @NotNull <T> List<T> newUnmodifiableList(@NotNull List<? extends T> originalList) {
    int size = originalList.size();
    if (size == 0) {
      return emptyList();
    }
    if (size == 1) {
      return Collections.singletonList(originalList.get(0));
    }
    return Collections.unmodifiableList(new ArrayList<>(originalList));
  }

  @Contract(pure = true)
  public static @NotNull <T> Collection<T> unmodifiableOrEmptyCollection(@NotNull Collection<? extends T> original) {
    int size = original.size();
    if (size == 0) {
      return emptyList();
    }
    return Collections.unmodifiableCollection(original);
  }

  @Contract(pure = true)
  public static @NotNull <T> List<T> unmodifiableOrEmptyList(@NotNull List<? extends T> original) {
    int size = original.size();
    if (size == 0) {
      return emptyList();
    }
    return Collections.unmodifiableList(original);
  }

  @Contract(pure = true)
  public static @NotNull <T> Set<T> unmodifiableOrEmptySet(@NotNull Set<? extends T> original) {
    int size = original.size();
    if (size == 0) {
      return Collections.emptySet();
    }
    return Collections.unmodifiableSet(original);
  }

  @Contract(pure = true)
  public static @NotNull <K,V> Map<K,V> unmodifiableOrEmptyMap(@NotNull Map<? extends K, ? extends V> original) {
    int size = original.size();
    if (size == 0) {
      return Collections.emptyMap();
    }
    return Collections.unmodifiableMap(original);
  }

  /**
   * @deprecated Use {@link SmartList()}
   */
  @Deprecated
  public static @NotNull <T> List<T> newSmartList() {
    return new SmartList<>();
  }

  /**
   * @deprecated Use {@link SmartList(T)}
   */
  @Deprecated
  public static @NotNull <T> List<T> newSmartList(T element) {
    return new SmartList<>(element);
  }

  /**
   * @deprecated Use {@link HashSet#HashSet()}
   */
  @Contract(pure = true)
  @Deprecated
  public static @NotNull <T> HashSet<T> newHashSet() {
    return new HashSet<>();
  }

  /**
   * @deprecated Use {@link HashSet#HashSet(int)}
   */
  @Contract(pure = true)
  @Deprecated
  public static @NotNull <T> HashSet<T> newHashSet(int initialCapacity) {
    return new HashSet<>(initialCapacity);
  }

  @SafeVarargs
  @Contract(pure = true)
  public static @NotNull <T> HashSet<T> newHashSet(T @NotNull ... elements) {
    return new HashSet<>(Arrays.asList(elements));
  }

  @Contract(pure = true)
  public static @NotNull <T> HashSet<T> newHashSet(@NotNull Iterable<? extends T> iterable) {
    return ContainerUtilRt.newHashSet(iterable);
  }

  /**
   * @deprecated Use {@link HashSet#HashSet(Collection)}
   */
  @Contract(pure = true)
  @Deprecated
  public static @NotNull <T> HashSet<T> newHashSet(@NotNull Collection<? extends T> collection) {
    return new HashSet<>(collection);
  }

  public static @NotNull <T> HashSet<T> newHashSet(@NotNull Iterator<? extends T> iterator) {
    HashSet<T> set = new HashSet<>();
    while (iterator.hasNext()) set.add(iterator.next());
    return set;
  }

  @Contract(pure = true)
  public static @NotNull <T> Set<T> newHashOrEmptySet(@Nullable Iterable<? extends T> iterable) {
    boolean empty = iterable == null || iterable instanceof Collection && ((Collection<?>)iterable).isEmpty();
    return empty ? Collections.emptySet() : ContainerUtilRt.newHashSet(iterable);
  }

  /**
   * @deprecated Use {@link LinkedHashSet#LinkedHashSet()}
   */
  @Contract(pure = true)
  @Deprecated
  public static @NotNull <T> LinkedHashSet<T> newLinkedHashSet() {
    return new LinkedHashSet<>();
  }

  @Contract(pure = true)
  public static @NotNull <T> LinkedHashSet<T> newLinkedHashSet(@NotNull Iterable<? extends T> elements) {
    return ContainerUtilRt.copy(new LinkedHashSet<>(), elements);
  }

  /**
   * @deprecated Use {@link LinkedHashSet#LinkedHashSet(Collection)} instead
   */
  @Deprecated
  @Contract(pure = true)
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  public static @NotNull <T> LinkedHashSet<T> newLinkedHashSet(@NotNull Collection<? extends T> elements) {
    DeprecatedMethodException.report("Use `new LinkedHashSet(Collection)` instead. "+elements.getClass());
    return new LinkedHashSet<>(elements);
  }

  @SafeVarargs
  @Contract(pure = true)
  public static @NotNull <T> LinkedHashSet<T> newLinkedHashSet(T @NotNull ... elements) {
    return new LinkedHashSet<>(Arrays.asList(elements));
  }

  /**
   * @deprecated Use {@link HashSet#HashSet()}
   */
  @Deprecated
  @Contract(pure = true)
  public static @NotNull <T> THashSet<T> newTroveSet() {
    return new THashSet<>();
  }

  /**
   * @deprecated Use {@link HashSet}
   */
  @SafeVarargs
  @Deprecated
  @Contract(pure = true)
  public static @NotNull <T> THashSet<T> newTroveSet(T @NotNull ... elements) {
    return new THashSet<>(Arrays.asList(elements));
  }

  /**
   * @deprecated Use {@link THashSet#THashSet(Collection, TObjectHashingStrategy)}
   */
  @Deprecated
  @SafeVarargs
  @Contract(pure = true)
  public static @NotNull <T> THashSet<T> newTroveSet(@NotNull TObjectHashingStrategy<T> strategy, T @NotNull ... elements) {
    return new THashSet<>(Arrays.asList(elements), strategy);
  }

  /**
   * @deprecated Use {@link HashSet}.
   */
  @Contract(pure = true)
  @Deprecated
  public static @NotNull <T> THashSet<T> newTroveSet(@NotNull TObjectHashingStrategy<T> strategy, @NotNull Collection<? extends T> elements) {
    return new THashSet<>(elements, strategy);
  }

  /**
   * @deprecated Use {@link THashSet#THashSet(Collection)}
   */
  @Contract(pure = true)
  @Deprecated
  public static @NotNull <T> THashSet<T> newTroveSet(@NotNull Collection<? extends T> elements) {
    return new THashSet<>(elements);
  }

  /**
   * @deprecated Use {@link it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet}.
   */
  @Deprecated
  @Contract(pure = true)
  public static @NotNull <K> THashSet<K> newIdentityTroveSet() {
    return new THashSet<>(identityStrategy());
  }

  /**
   * @deprecated Use {@link it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet}.
   */
  @Deprecated
  @Contract(pure = true)
  public static @NotNull <K> THashSet<K> newIdentityTroveSet(@NotNull Collection<? extends K> collection) {
    return new THashSet<>(collection, identityStrategy());
  }

  /**
   * @deprecated Use {@link it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap}
   */
  @Deprecated
  @Contract(pure = true)
  public static @NotNull <K,V> THashMap<K,V> newIdentityTroveMap() {
    return new THashMap<>(identityStrategy());
  }

  /**
   * @deprecated Use {@link TreeSet#TreeSet()}
   */
  @Deprecated
  @Contract(pure = true)
  public static @NotNull <T extends Comparable<? super T>> TreeSet<T> newTreeSet() {
    return new TreeSet<>();
  }

  /**
   * @deprecated Use {@link TreeSet#TreeSet(Comparator)}
   */
  @Contract(pure = true)
  @Deprecated
  public static @NotNull <T> TreeSet<T> newTreeSet(@Nullable Comparator<? super T> comparator) {
    return new TreeSet<>(comparator);
  }

  @Contract(pure = true)
  public static @NotNull <T> Set<T> newConcurrentSet() {
    return Collections.newSetFromMap(new ConcurrentHashMap<>());
  }

  /**
   * @deprecated Use {@link ConcurrentHashMap#ConcurrentHashMap()}
   */
  @Deprecated
  @Contract(pure = true)
  public static @NotNull <K, V> ConcurrentMap<K, V> newConcurrentMap() {
    return new ConcurrentHashMap<>();
  }

  @Contract(pure = true)
  public static @NotNull <K, V> ConcurrentMap<K,V> newConcurrentMap(int initialCapacity) {
    return new ConcurrentHashMap<>(initialCapacity);
  }

  @Contract(pure = true)
  public static @NotNull <K, V> ConcurrentMap<K,V> newConcurrentMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
    return new ConcurrentHashMap<>(initialCapacity, loadFactor, concurrencyLevel);
  }

  @Contract(pure = true)
  public static @NotNull <E> List<E> reverse(final @NotNull List<? extends E> elements) {
    if (elements.isEmpty()) {
      return emptyList();
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

  @Contract(pure = true)
  public static @NotNull <K, V> Map<K, V> union(@NotNull Map<? extends K, ? extends V> map, @NotNull Map<? extends K, ? extends V> map2) {
    Map<K, V> result = new THashMap<>(map.size() + map2.size());
    result.putAll(map);
    result.putAll(map2);
    return result;
  }

  @Contract(pure = true)
  public static @NotNull <T> Set<T> union(@NotNull Set<? extends T> set, @NotNull Set<? extends T> set2) {
    return union((Collection<? extends T>)set, set2);
  }

  @Contract(pure = true)
  public static @NotNull <T> Set<T> union(@NotNull Collection<? extends T> set, @NotNull Collection<? extends T> set2) {
    Set<T> result = new HashSet<>(set.size() + set2.size());
    result.addAll(set);
    result.addAll(set2);
    return result;
  }

  @SafeVarargs
  @Contract(pure = true)
  public static @NotNull <E> Set<E> immutableSet(E @NotNull ... elements) {
    switch (elements.length) {
      case 0:
        return Collections.emptySet();
      case 1:
        return Collections.singleton(elements[0]);
      default:
        return Collections.unmodifiableSet(newTroveSet(elements));
    }
  }

  @SafeVarargs
  @Contract(pure = true)
  public static @NotNull <E> ImmutableList<E> immutableList(E @NotNull ... array) {
    return new ImmutableListBackedByArray<>(array);
  }

  @Contract(pure = true)
  public static @NotNull <E> ImmutableList<E> immutableSingletonList(final E element) {
    return ImmutableList.singleton(element);
  }

  @Contract(pure = true)
  public static @NotNull <E> ImmutableList<E> immutableList(@NotNull List<? extends E> list) {
    return new ImmutableListBackedByList<>(list);
  }

  @Contract(pure = true)
  public static @NotNull <K, V> ImmutableMapBuilder<K, V> immutableMapBuilder() {
    return new ImmutableMapBuilder<>();
  }

  @Contract(pure = true)
  public static @NotNull <K, V> MultiMap<K, V> groupBy(@NotNull Iterable<? extends V> collection, @NotNull NullableFunction<? super V, ? extends K> grouper) {
    MultiMap<K, V> result = MultiMap.createLinked();
    for (V data : collection) {
      K key = grouper.fun(data);
      if (key == null) {
        continue;
      }
      result.putValue(key, data);
    }

    if (!result.isEmpty() && result.keySet().iterator().next() instanceof Comparable) {
      //noinspection unchecked,rawtypes
      return new KeyOrderedMultiMap(result);
    }
    return result;
  }

  @Contract(pure = true)
  public static <T> T getOrElse(@NotNull List<? extends T> elements, int i, T defaultValue) {
    return elements.size() > i ? elements.get(i) : defaultValue;
  }

  @Contract(pure = true)
  public static @NotNull <U> Iterator<U> mapIterator(@NotNull TIntIterator iterator, @NotNull IntFunction<? extends U> mapper) {
    return new Iterator<U>() {
      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public U next() {
        return mapper.apply(iterator.next());
      }

      @Override
      public void remove() {
        iterator.remove();
      }
    };
  }

  public static final class ImmutableMapBuilder<K, V> {
    private final Map<K, V> myMap = new HashMap<>();

    public @NotNull ImmutableMapBuilder<K, V> put(K key, V value) {
      myMap.put(key, value);
      return this;
    }

    @Contract(pure = true)
    public @NotNull Map<K, V> build() {
      return Collections.unmodifiableMap(myMap);
    }
  }

  private static final class ImmutableListBackedByList<E> extends ImmutableList<E> {
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

    @Override
    public void forEach(java.util.function.Consumer<? super E> action) {
      myStore.forEach(action);
    }
  }

  private static final class ImmutableListBackedByArray<E> extends ImmutableList<E> {
    private final E[] myStore;

    private ImmutableListBackedByArray(E @NotNull [] array) {
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

    // overridden for more efficient arraycopy vs. iterator() creation/traversing
    @Override
    public <T> T @NotNull [] toArray(T @NotNull [] a) {
      int size = size();
      T[] result = a.length >= size ? a : ArrayUtil.newArray(ArrayUtil.getComponentType(a), size);
      System.arraycopy(myStore, 0, result, 0, size);
      if (result.length > size) {
        result[size] = null;
      }
      return result;
    }

    @Override
    public void forEach(java.util.function.Consumer<? super E> action) {
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0, length = myStore.length; i < length; i++) {
        action.accept(myStore[i]);
      }
    }
  }

  @Contract(pure = true)
  public static @NotNull <K, V> Map<K, V> intersection(@NotNull Map<? extends K, ? extends V> map1, @NotNull Map<? extends K, ? extends V> map2) {
    if (map2.size() < map1.size()) {
      Map<? extends K, ? extends V> t = map1;
      map1 = map2;
      map2 = t;
    }
    final Map<K, V> res = new HashMap<>(map1);
    for (Map.Entry<? extends K, ? extends V> entry : map1.entrySet()) {
      K key = entry.getKey();
      V v1 = entry.getValue();
      V v2 = map2.get(key);
      if (!Objects.equals(v1, v2)) {
        res.remove(key);
      }
    }
    return res;
  }

  @Contract(pure = true)
  public static @NotNull <K, V> Map<K,Couple<V>> diff(@NotNull Map<? extends K, ? extends V> map1, @NotNull Map<? extends K, ? extends V> map2) {
    Set<K> keys = union(map1.keySet(), map2.keySet());
    Map<K, Couple<V>> res = new HashMap<>();
    for (K k : keys) {
      V v1 = map1.get(k);
      V v2 = map2.get(k);
      if (!Objects.equals(v1, v2)) {
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

  @Contract(pure = true)
  public static @NotNull <T> List<T> mergeSortedLists(@NotNull List<? extends T> list1,
                                                      @NotNull List<? extends T> list2,
                                                      @NotNull Comparator<? super T> comparator,
                                                      boolean mergeEqualItems) {
    final List<T> result = new ArrayList<>(list1.size() + list2.size());
    processSortedListsInOrder(list1, list2, comparator, mergeEqualItems, result::add);
    return result;
  }

  @Contract(pure = true)
  public static @NotNull <T> List<T> subList(@NotNull List<T> list, int from) {
    return list.subList(from, list.size());
  }

  /**
   * @deprecated Use {@link Collection#addAll(Collection)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  public static <T> void addAll(@NotNull Collection<? super T> collection, @NotNull Collection<? extends T> elements) {
    DeprecatedMethodException.report("Use `collection.addAll(elements)` instead. "+collection.getClass()+"."+elements.getClass());
    collection.addAll(elements);
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

  public static @NotNull <T> List<T> collect(@NotNull Iterator<? extends T> iterator) {
    if (!iterator.hasNext()) return emptyList();
    List<T> list = new ArrayList<>();
    addAll(list, iterator);
    return list;
  }

  @Contract(pure = true)
  public static @NotNull <K, V> Map<K, V> newMapFromKeys(@NotNull Iterator<? extends K> keys, @NotNull Convertor<? super K, ? extends V> valueConvertor) {
    Map<K, V> map = new HashMap<>();
    while (keys.hasNext()) {
      K key = keys.next();
      map.put(key, valueConvertor.convert(key));
    }
    return map;
  }

  @Contract(pure = true)
  public static @NotNull <K, V> Map<K, V> newMapFromValues(@NotNull Iterator<? extends V> values, @NotNull Convertor<? super V, ? extends K> keyConvertor) {
    Map<K, V> map = new HashMap<>();
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

  @Contract(pure = true)
  public static @NotNull <K, V> Map<K, Set<V>> classify(@NotNull Iterator<? extends V> iterator, @NotNull Convertor<? super V, ? extends K> keyConvertor) {
    Map<K, Set<V>> hashMap = new LinkedHashMap<>();
    while (iterator.hasNext()) {
      V value = iterator.next();
      final K key = keyConvertor.convert(value);
      // ordered set!!
      Set<V> set = hashMap.computeIfAbsent(key, __ -> new LinkedHashSet<>());
      set.add(value);
    }
    return hashMap;
  }

  /**
   * @deprecated Use {@link Collections#emptyIterator()} instead
   */
  @Deprecated
  @Contract(pure = true)
  public static @NotNull <T> Iterator<T> emptyIterator() {
    return Collections.emptyIterator();
  }

  /**
   * @deprecated Use {@link Collections#emptyList()} instead
   */
  @Deprecated
  @Contract(pure = true)
  public static @NotNull <T> Iterable<T> emptyIterable() {
    return Collections.emptyList();
  }

  @Contract(pure=true)
  public static <T> T find(T @NotNull [] array, @NotNull Condition<? super T> condition) {
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

  public static <T> boolean process(T @NotNull [] iterable, @NotNull Processor<? super T> processor) {
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

  @Contract(pure=true)
  public static <T> T find(@NotNull Iterable<? extends T> iterable, @NotNull Condition<? super T> condition) {
    return find(iterable.iterator(), condition);
  }

  @Contract(pure=true)
  public static <T> T find(@NotNull Iterable<? extends T> iterable, final @NotNull T equalTo) {
    return find(iterable, (Condition<T>)object -> equalTo == object || equalTo.equals(object));
  }

  @Contract(pure=true)
  public static <T> T find(@NotNull Iterator<? extends T> iterator, final @NotNull T equalTo) {
    return find(iterator, (Condition<T>)object -> equalTo == object || equalTo.equals(object));
  }

  public static <T> T find(@NotNull Iterator<? extends T> iterator, @NotNull Condition<? super T> condition) {
    while (iterator.hasNext()) {
      T value = iterator.next();
      if (condition.value(value)) return value;
    }
    return null;
  }

  @Contract(pure = true)
  public static <T> T findLast(@NotNull List<? extends T> list, @NotNull Condition<? super T> condition) {
    int index = lastIndexOf(list, condition);
    if (index < 0) return null;
    return list.get(index);
  }

  @Contract(pure = true)
  public static @NotNull <T, K, V> Map<K, V> map2Map(T @NotNull [] collection, @NotNull Function<? super T, ? extends Pair<K, V>> mapper) {
    return map2Map(Arrays.asList(collection), mapper);
  }

  @Contract(pure = true)
  public static @NotNull <T, K, V> Map<K, V> map2Map(@NotNull Collection<? extends T> collection,
                                                     @NotNull Function<? super T, ? extends Pair<K, V>> mapper) {
    final Map<K, V> set = new THashMap<>(collection.size());
    for (T t : collection) {
      Pair<K, V> pair = mapper.fun(t);
      set.put(pair.first, pair.second);
    }
    return set;
  }

  @Contract(pure = true)
  public static @NotNull <T, K, V> Map<K, V> map2MapNotNull(T @NotNull [] collection,
                                                            @NotNull Function<? super T, ? extends Pair<K, V>> mapper) {
    return map2MapNotNull(Arrays.asList(collection), mapper);
  }

  @Contract(pure = true)
  public static @NotNull <T, K, V> Map<K, V> map2MapNotNull(@NotNull Collection<? extends T> collection,
                                                            @NotNull Function<? super T, ? extends Pair<K, V>> mapper) {
    final Map<K, V> set = new THashMap<>(collection.size());
    for (T t : collection) {
      Pair<K, V> pair = mapper.fun(t);
      if (pair != null) {
        set.put(pair.first, pair.second);
      }
    }
    return set;
  }

  @Contract(pure = true)
  public static @NotNull <K, V> Map<K, V> map2Map(@NotNull Collection<? extends Pair<? extends K, ? extends V>> collection) {
    Map<K, V> result = new THashMap<>(collection.size());
    for (Pair<? extends K, ? extends V> pair : collection) {
      result.put(pair.first, pair.second);
    }
    return result;
  }

  @Contract(pure=true)
  public static <T> Object @NotNull [] map2Array(T @NotNull [] array, @NotNull Function<? super T, Object> mapper) {
    return map2Array(array, Object.class, mapper);
  }

  @Contract(pure=true)
  public static <T> Object @NotNull [] map2Array(@NotNull Collection<? extends T> array, @NotNull Function<? super T, Object> mapper) {
    return map2Array(array, Object.class, mapper);
  }

  @Contract(pure=true)
  public static <T, V> V @NotNull [] map2Array(T @NotNull [] array, @NotNull Class<V> aClass, @NotNull Function<? super T, ? extends V> mapper) {
    V[] result = ArrayUtil.newArray(aClass, array.length);
    for (int i = 0; i < array.length; i++) {
      result[i] = mapper.fun(array[i]);
    }
    return result;
  }

  @Contract(pure=true)
  public static <T, V> V @NotNull [] map2Array(@NotNull Collection<? extends T> collection, @NotNull Class<V> aClass, @NotNull Function<? super T, ? extends V> mapper) {
    V[] result = ArrayUtil.newArray(aClass, collection.size());
    int i = 0;
    for (T t : collection) {
      result[i++] = mapper.fun(t);
    }
    return result;
  }

  @Contract(pure=true)
  public static <T, V> V @NotNull [] map2Array(@NotNull Collection<? extends T> collection, V @NotNull [] to, @NotNull Function<? super T, V> mapper) {
    return map2List(collection, mapper).toArray(to);
  }

  @Contract(pure = true)
  public static @NotNull <T> List<T> filter(T @NotNull [] collection, @NotNull Condition<? super T> condition) {
    return findAll(collection, condition);
  }

  @Contract(pure=true)
  public static int @NotNull [] filter(int @NotNull [] collection, @NotNull TIntProcedure condition) {
    TIntArrayList result = new TIntArrayList();
    for (int t : collection) {
      if (condition.execute(t)) {
        result.add(t);
      }
    }
    return result.isEmpty() ? ArrayUtilRt.EMPTY_INT_ARRAY : result.toNativeArray();
  }

  @Contract(pure = true)
  public static @NotNull <T> List<T> findAll(T @NotNull [] collection, @NotNull Condition<? super T> condition) {
    final List<T> result = new SmartList<>();
    for (T t : collection) {
      if (condition.value(t)) {
        result.add(t);
      }
    }
    return result;
  }

  @Contract(pure = true)
  public static @NotNull <T> List<T> filterIsInstance(@NotNull Collection<?> collection, final @NotNull Class<? extends T> aClass) {
    //noinspection unchecked
    return filter((Collection<T>)collection, Conditions.instanceOf(aClass));
  }

  @Contract(pure = true)
  public static @NotNull <T> List<T> filter(@NotNull Collection<? extends T> collection, @NotNull Condition<? super T> condition) {
    return findAll(collection, condition);
  }

  @Contract(pure = true)
  public static @NotNull <K, V> Map<K, V> filter(@NotNull Map<? extends K, ? extends V> map, @NotNull Condition<? super K> keyFilter) {
    Map<K, V> result = new HashMap<>();
    for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
      if (keyFilter.value(entry.getKey())) {
        result.put(entry.getKey(), entry.getValue());
      }
    }
    return result;
  }

  @Contract(pure = true)
  public static @NotNull <T> List<T> findAll(@NotNull Collection<? extends T> collection, @NotNull Condition<? super T> condition) {
    if (collection.isEmpty()) return emptyList();
    final List<T> result = new SmartList<>();
    for (final T t : collection) {
      if (condition.value(t)) {
        result.add(t);
      }
    }
    return result;
  }

  @Contract(pure = true)
  public static @NotNull <T> List<T> skipNulls(@NotNull Collection<? extends T> collection) {
    return findAll(collection, Conditions.notNull());
  }

  @Contract(pure = true)
  public static @NotNull <T, V> List<V> findAll(T @NotNull [] array, @NotNull Class<V> instanceOf) {
    List<V> result = new SmartList<>();
    for (final T t : array) {
      if (instanceOf.isInstance(t)) {
        //noinspection unchecked
        result.add((V)t);
      }
    }
    return result;
  }

  @Contract(pure=true)
  public static <T, V> V @NotNull [] findAllAsArray(T @NotNull [] collection, @NotNull Class<V> instanceOf) {
    List<V> list = findAll(collection, instanceOf);
    V[] array = ArrayUtil.newArray(instanceOf, list.size());
    return list.toArray(array);
  }

  @Contract(pure=true)
  public static <T, V> V @NotNull [] findAllAsArray(@NotNull Collection<? extends T> collection, @NotNull Class<V> instanceOf) {
    List<V> list = findAll(collection, instanceOf);
    V[] array = ArrayUtil.newArray(instanceOf, list.size());
    return list.toArray(array);
  }

  @Contract(pure=true)
  public static <T> T @NotNull [] findAllAsArray(T @NotNull [] collection, @NotNull Condition<? super T> instanceOf) {
    List<T> list = findAll(collection, instanceOf);
    if (list.size() == collection.length) {
      return collection;
    }
    T[] array = ArrayUtil.newArray(ArrayUtil.getComponentType(collection), list.size());
    return list.toArray(array);
  }

  @Contract(pure = true)
  public static @NotNull <T, V> List<V> findAll(@NotNull Collection<? extends T> collection, @NotNull Class<V> instanceOf) {
    final List<V> result = new SmartList<>();
    for (final T t : collection) {
      if (instanceOf.isInstance(t)) {
        //noinspection unchecked
        result.add((V)t);
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
    Set<T> collected = new HashSet<>();
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

  @Contract(pure = true)
  public static @NotNull Map<String, String> stringMap(final String @NotNull ... keyValues) {
    final Map<String, String> result = new HashMap<>();
    for (int i = 0; i < keyValues.length - 1; i+=2) {
      result.put(keyValues[i], keyValues[i+1]);
    }

    return result;
  }

  @Contract(pure = true)
  public static @NotNull <T> Iterator<T> iterate(T @NotNull [] array) {
    return array.length == 0 ? Collections.emptyIterator() : Arrays.asList(array).iterator();
  }

  @Contract(pure = true)
  public static @NotNull <T> Iterator<T> iterate(final @NotNull Enumeration<? extends T> enumeration) {
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

  @Contract(pure = true)
  public static @NotNull <T> Iterable<T> iterate(T @NotNull [] arrays, @NotNull Condition<? super T> condition) {
    return iterate(Arrays.asList(arrays), condition);
  }

  @Contract(pure = true)
  public static @NotNull <T> Iterable<T> iterate(final @NotNull Collection<? extends T> collection, final @NotNull Condition<? super T> condition) {
    if (collection.isEmpty()) return Collections.emptyList();
    return () -> new Iterator<T>() {
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

      private @Nullable T findNext() {
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

  @Contract(pure = true)
  public static @NotNull <T> Iterable<T> iterateBackward(final @NotNull List<? extends T> list) {
    return () -> new Iterator<T>() {
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

  @Contract(pure = true)
  public static @NotNull <T, E> Iterable<Pair<T, E>> zip(final @NotNull Iterable<? extends T> iterable1, final @NotNull Iterable<? extends E> iterable2) {
    return () -> new Iterator<Pair<T, E>>() {
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

  public static <E> void swapElements(@NotNull List<E> list, int index1, int index2) {
    E e1 = list.get(index1);
    E e2 = list.get(index2);
    list.set(index1, e2);
    list.set(index2, e1);
  }

  public static @NotNull <T> List<T> collect(@NotNull Iterator<?> iterator, @NotNull FilteringIterator.InstanceOf<T> instanceOf) {
    //noinspection unchecked
    return collect(FilteringIterator.create((Iterator<T>)iterator, instanceOf));
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
   * @param <C> type of the collection
   * @return the collection passed as first argument
   */
  @SafeVarargs
  public static @NotNull <T, C extends Collection<? super T>> C addAll(@NotNull C collection, T @NotNull ... elements) {
    //noinspection ManualArrayToCollectionCopy
    for (T element : elements) {
      //noinspection UseBulkOperation
      collection.add(element);
    }
    return collection;
  }

  /**
   * Adds all not-null elements from the {@code elements}, ignoring nulls
   */
  @SafeVarargs
  public static @NotNull <T, C extends Collection<T>> C addAllNotNull(@NotNull C collection, T @NotNull ... elements) {
    for (T element : elements) {
      if (element != null) {
        collection.add(element);
      }
    }
    return collection;
  }

  @SafeVarargs
  public static <T> boolean removeAll(@NotNull Collection<T> collection, T @NotNull ... elements) {
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

  @Contract(pure=true)
  public static <T, U extends T> U findInstance(T @NotNull [] array, @NotNull Class<? extends U> aClass) {
    //noinspection unchecked
    return (U)find(array, FilteringIterator.instanceOf(aClass));
  }

  @Contract(pure = true)
  public static @NotNull <T, V> List<T> concat(V @NotNull [] array, @NotNull Function<? super V, ? extends Collection<? extends T>> fun) {
    return concat(Arrays.asList(array), fun);
  }

  /**
   * @return read-only list consisting of the elements from the collections stored in list added together
   */
  @Contract(pure = true)
  public static @NotNull <T> List<T> concat(@NotNull Iterable<? extends Collection<? extends T>> list) {
    int totalSize = 0;
    for (final Collection<? extends T> ts : list) {
      totalSize += ts.size();
    }
    List<T> result = new ArrayList<>(totalSize);
    for (final Collection<? extends T> ts : list) {
      result.addAll(ts);
    }
    return result.isEmpty() ? Collections.emptyList() : result;
  }

  @SafeVarargs
  @Contract(pure = true)
  public static @NotNull <T> List<T> append(@NotNull List<? extends T> list, T @NotNull ... values) {
    return concat(list, Arrays.asList(values));
  }

  /**
   * prepend values in front of the list
   * @return read-only list consisting of values and the elements from specified list
   */
  @SafeVarargs
  @Contract(pure = true)
  public static @NotNull <T> List<T> prepend(@NotNull List<? extends T> list, T @NotNull ... values) {
    return concat(Arrays.asList(values), list);
  }

  /**
   * @return read-only list consisting of the two lists added together
   */
  @Contract(pure = true)
  public static @NotNull <T> List<T> concat(final @NotNull List<? extends T> list1, final @NotNull List<? extends T> list2) {
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

  @Contract(pure = true)
  public static @NotNull <T> Iterable<T> concat(@NotNull Iterable<? extends T> it1, @NotNull Iterable<? extends T> it2) {
    return new Iterable<T>() {
      @Override
      public void forEach(java.util.function.Consumer<? super T> action) {
        it1.forEach(action);
        it2.forEach(action);
      }

      @Override
      public @NotNull Iterator<T> iterator() {
        return new Iterator<T>() {
          Iterator<? extends T> it = it1.iterator();
          boolean firstFinished;

          { advance(); }

          @Override
          public boolean hasNext() {
            return !firstFinished || it.hasNext();
          }

          @Override
          public T next() {
            T value = it.next(); // it.next() will throw NSEE if no elements remaining
            advance();
            return value;
          }

          private void advance() {
            if (!firstFinished && !it.hasNext()) {
              it = it2.iterator();
              firstFinished = true;
            }
          }
        };
      }
    };
  }

  @SafeVarargs
  @Contract(pure = true)
  public static @NotNull <T> Iterable<T> concat(final Iterable<? extends T> @NotNull ... iterables) {
    if (iterables.length == 0) return Collections.emptyList();
    if (iterables.length == 1) {
      //noinspection unchecked
      return (Iterable<T>)iterables[0];
    }
    return () -> {
      //noinspection unchecked
      Iterator<? extends T>[] iterators = new Iterator[iterables.length];
      for (int i = 0; i < iterables.length; i++) {
        Iterable<? extends T> iterable = iterables[i];
        iterators[i] = iterable.iterator();
      }
      return concatIterators(iterators);
    };
  }

  @SafeVarargs
  @Contract(pure = true)
  public static @NotNull <T> Iterator<T> concatIterators(Iterator<? extends T> @NotNull ... iterators) {
    return new SequenceIterator<>(iterators);
  }

  @Contract(pure = true)
  public static @NotNull <T> Iterator<T> concatIterators(@NotNull Collection<? extends Iterator<? extends T>> iterators) {
    return new SequenceIterator<>(iterators);
  }

  @SafeVarargs
  @Contract(pure = true)
  public static @NotNull <T> Iterable<T> concat(final T[] @NotNull ... arrays) {
    return () -> {
      //noinspection unchecked
      Iterator<T>[] iterators = new Iterator[arrays.length];
      for (int i = 0; i < arrays.length; i++) {
        iterators[i] = iterate(arrays[i]);
      }
      return concatIterators(iterators);
    };
  }

  /**
   * @return read-only list consisting of the lists added together
   */
  @SafeVarargs
  @Contract(pure = true)
  public static @NotNull <T> List<T> concat(final List<? extends T> @NotNull ... lists) {
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
  @Contract(pure = true)
  public static @NotNull <T> List<T> concat(final @NotNull List<List<? extends T>> lists) {
    //noinspection unchecked
    return concat(lists.toArray(new List[0]));
  }

  /**
   * @return read-only list consisting of the lists (made by listGenerator) added together
   */
  @Contract(pure = true)
  public static @NotNull <T, V> List<V> concat(@NotNull Iterable<? extends T> list, @NotNull Function<? super T, ? extends Collection<? extends V>> listGenerator) {
    List<V> result = new ArrayList<>();
    for (final T v : list) {
      result.addAll(listGenerator.fun(v));
    }
    return result.isEmpty() ? emptyList() : result;
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
  @Contract(pure = true)
  public static @NotNull <T> Collection<T> intersection(@NotNull Collection<? extends T> collection1, @NotNull Collection<? extends T> collection2) {
    List<T> result = new ArrayList<>();
    for (T t : collection1) {
      if (collection2.contains(t)) {
        result.add(t);
      }
    }
    return result.isEmpty() ? emptyList() : result;
  }

  @Contract(pure = true)
  public static @NotNull <E extends Enum<E>> EnumSet<E> intersection(@NotNull EnumSet<E> collection1, @NotNull EnumSet<E> collection2) {
    EnumSet<E> result = EnumSet.copyOf(collection1);
    result.retainAll(collection2);
    return result;
  }

  @Contract(pure=true)
  public static <T> T getFirstItem(@Nullable Collection<? extends T> items) {
    return getFirstItem(items, null);
  }

  @Contract(pure=true)
  public static <T> T getFirstItem(@Nullable List<? extends T> items) {
    return items == null || items.isEmpty() ? null : items.get(0);
  }

  @Contract(pure=true)
  public static <T> T getFirstItem(final @Nullable Collection<? extends T> items, final @Nullable T defaultResult) {
    return items == null || items.isEmpty() ? defaultResult : items.iterator().next();
  }

  /**
   * Returns the only item from the collection or null if collection is empty or contains more than one item
   *
   * @param items collection to get the item from
   * @param <T> type of collection element
   * @return the only collection element or null
   */
  @Contract(pure=true)
  public static <T> T getOnlyItem(final @Nullable Collection<? extends T> items) {
    return getOnlyItem(items, null);
  }

  @Contract(pure=true)
  public static <T> T getOnlyItem(final @Nullable Collection<? extends T> items, final @Nullable T defaultResult) {
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
  @Contract(pure = true)
  public static @NotNull <T> List<T> getFirstItems(final @NotNull List<T> items, int maxItems) {
    if (maxItems < 0) {
      throw new IllegalArgumentException("Expected non-negative maxItems; got: "+maxItems);
    }
    return maxItems >= items.size() ? items : items.subList(0, maxItems);
  }

  @Contract(pure=true)
  public static <T> T iterateAndGetLastItem(@NotNull Iterable<? extends T> items) {
    Iterator<? extends T> itr = items.iterator();
    T res = null;
    while (itr.hasNext()) {
      res = itr.next();
    }

    return res;
  }

  @Contract(pure = true)
  public static @NotNull <T,U> Iterator<U> mapIterator(final @NotNull Iterator<? extends T> iterator, final @NotNull Function<? super T, ? extends U> mapper) {
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
  @Contract(pure = true)
  public static @NotNull <T> Iterator<T> filterIterator(final @NotNull Iterator<? extends T> iterator, final @NotNull Condition<? super T> filter) {
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

  @Contract(pure=true)
  public static <T> T getLastItem(@Nullable List<? extends T> list, @Nullable T def) {
    return isEmpty(list) ? def : list.get(list.size() - 1);
  }

  @Contract(pure=true)
  public static <T> T getLastItem(@Nullable List<? extends T> list) {
    return getLastItem(list, null);
  }

  /**
   * @return read-only collection consisting of elements from the 'from' collection which are absent from the 'what' collection
   */
  @Contract(pure = true)
  public static @NotNull <T> Collection<T> subtract(@NotNull Collection<? extends T> from, @NotNull Collection<? extends T> what) {
    final Set<T> set = new HashSet<>(from);
    set.removeAll(what);
    return set.isEmpty() ? emptyList() : set;
  }

  @Contract(pure=true)
  public static <T> T @NotNull [] toArray(@NotNull Collection<T> c, @NotNull ArrayFactory<? extends T> factory) {
    T[] a = factory.create(c.size());
    return c.toArray(a);
  }

  @Contract(pure=true)
  public static <T> T @NotNull [] toArray(@NotNull Collection<? extends T> c1, @NotNull Collection<? extends T> c2, @NotNull ArrayFactory<? extends T> factory) {
    return ArrayUtil.mergeCollections(c1, c2, factory);
  }

  @Contract(pure=true)
  public static <T> T @NotNull [] mergeCollectionsToArray(@NotNull Collection<? extends T> c1, @NotNull Collection<? extends T> c2, @NotNull ArrayFactory<? extends T> factory) {
    return ArrayUtil.mergeCollections(c1, c2, factory);
  }

  public static <T extends Comparable<? super T>> void sort(@NotNull List<T> list) {
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
      list.sort(comparator);
    }
  }

  public static <T extends Comparable<? super T>> void sort(T @NotNull [] a) {
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

  @Contract(pure = true)
  public static @NotNull <T> List<T> sorted(@NotNull Collection<? extends T> list, @NotNull Comparator<? super T> comparator) {
    return sorted((Iterable<? extends T>)list, comparator);
  }

  @Contract(pure = true)
  public static @NotNull <T> List<T> sorted(@NotNull Iterable<? extends T> list, @NotNull Comparator<? super T> comparator) {
    List<T> sorted = newArrayList(list);
    sort(sorted, comparator);
    return sorted;
  }

  @Contract(pure = true)
  public static @NotNull <T extends Comparable<? super T>> List<T> sorted(@NotNull Collection<? extends T> list) {
    List<T> result = new ArrayList<>(list);
    result.sort(null);
    return result;
  }

  /**
   * @apiNote this sort implementation is NOT stable for element.length < INSERTION_SORT_THRESHOLD
   */
  public static <T> void sort(T @NotNull [] a, @NotNull Comparator<? super T> comparator) {
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
  @Contract(pure = true)
  public static @NotNull <T,V> List<V> map(@NotNull Iterable<? extends T> iterable, @NotNull Function<? super T, ? extends V> mapping) {
    List<V> result = new ArrayList<>();
    for (T t : iterable) {
      result.add(mapping.fun(t));
    }
    return result.isEmpty() ? emptyList() : result;
  }

  /**
   * @param collection an input collection to process
   * @param mapping a side-effect free function which transforms iterable elements
   * @return read-only list consisting of the elements from the input collection converted by mapping
   */
  @Contract(pure = true)
  public static @NotNull <T,V> List<V> map(@NotNull Collection<? extends T> collection, @NotNull Function<? super T, ? extends V> mapping) {
    if (collection.isEmpty()) return emptyList();
    List<V> list = new ArrayList<>(collection.size());
    for (final T t : collection) {
      list.add(mapping.fun(t));
    }
    return list;
  }

  /**
   * @param array an input array to process
   * @param mapping a side-effect free function which transforms array elements
   * @return read-only list consisting of the elements from the input array converted by mapping with nulls filtered out
   */
  @Contract(pure = true)
  public static @NotNull <T, V> List<@NotNull V> mapNotNull(T @NotNull [] array,
                                                            @NotNull Function<? super T, ? extends @Nullable V> mapping) {
    if (array.length == 0) {
      return emptyList();
    }

    List<V> result = new ArrayList<>(array.length);
    for (T t : array) {
      final V o = mapping.fun(t);
      if (o != null) {
        result.add(o);
      }
    }
    return result.isEmpty() ? emptyList() : result;
  }

  /**
   * @param array an input array to process
   * @param mapping a side-effect free function which transforms array elements
   * @param emptyArray an empty array of desired result type (may be returned if the result is also empty)
   * @return array consisting of the elements from the input array converted by mapping with nulls filtered out
   */
  @Contract(pure=true)
  public static <T, V> @NotNull V @NotNull [] mapNotNull(T @NotNull [] array,
                                                         @NotNull Function<? super T, ? extends @Nullable V> mapping,
                                                         V @NotNull [] emptyArray) {
    List<V> result = new ArrayList<>(array.length);
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
  @Contract(pure = true)
  public static @NotNull <T, V> List<@NotNull V> mapNotNull(@NotNull Iterable<? extends T> iterable,
                                                            @NotNull Function<? super T, ? extends @Nullable V> mapping) {
    List<V> result = new ArrayList<>();
    for (T t : iterable) {
      final V o = mapping.fun(t);
      if (o != null) {
        result.add(o);
      }
    }
    return result.isEmpty() ? emptyList() : result;
  }

  /**
   * @param collection an input collection to process
   * @param mapping a side-effect free function which transforms collection elements
   * @return read-only list consisting of the elements from the array converted by mapping with nulls filtered out
   */
  @Contract(pure = true)
  public static @NotNull <T, V> List<@NotNull V> mapNotNull(@NotNull Collection<? extends T> collection,
                                                            @NotNull Function<? super T, ? extends @Nullable V> mapping) {
    if (collection.isEmpty()) {
      return emptyList();
    }

    List<V> result = new ArrayList<>(collection.size());
    for (T t : collection) {
      final V o = mapping.fun(t);
      if (o != null) {
        result.add(o);
      }
    }
    return result.isEmpty() ? emptyList() : result;
  }

  /**
   * @return read-only list consisting of the elements with nulls filtered out
   */
  @SafeVarargs
  @Contract(pure = true)
  public static @NotNull <T> List<T> packNullables(T @NotNull ... elements) {
    List<T> list = new ArrayList<>();
    for (T element : elements) {
      addIfNotNull(list, element);
    }
    return list.isEmpty() ? emptyList() : list;
  }

  /**
   * @return read-only list consisting of the elements from the array converted by mapping
   */
  @Contract(pure = true)
  public static @NotNull <T, V> List<V> map(T @NotNull [] array, @NotNull Function<? super T, ? extends V> mapping) {
    List<V> result = new ArrayList<>(array.length);
    for (T t : array) {
      result.add(mapping.fun(t));
    }
    return result.isEmpty() ? emptyList() : result;
  }

  @Contract(pure=true)
  public static <T, V> V @NotNull [] map(T @NotNull [] arr, @NotNull Function<? super T, ? extends V> mapping, V @NotNull [] emptyArray) {
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

  @SafeVarargs
  @Contract(pure = true)
  public static @NotNull <T> Set<T> set(T @NotNull ... items) {
    //noinspection SSBasedInspection
    return new HashSet<>(Arrays.asList(items));
  }

  public static <K, V> void putIfAbsent(final K key, @Nullable V value, final @NotNull Map<? super K, ? super V> result) {
    if (!result.containsKey(key)) {
      result.put(key, value);
    }
  }

  public static <K, V> void putIfNotNull(final K key, @Nullable V value, final @NotNull Map<? super K, ? super V> result) {
    if (value != null) {
      result.put(key, value);
    }
  }

  public static <K, V> void putIfNotNull(final K key, @Nullable Collection<? extends V> value, final @NotNull MultiMap<? super K, ? super V> result) {
    if (value != null) {
      result.putValues(key, value);
    }
  }

  public static <K, V> void putIfNotNull(final K key, @Nullable V value, final @NotNull MultiMap<? super K, ? super V> result) {
    if (value != null) {
      result.putValue(key, value);
    }
  }

  public static <T> void add(final T element, final @NotNull Collection<? super T> result, final @NotNull Disposable parentDisposable) {
    if (result.add(element)) {
      Disposer.register(parentDisposable, () -> result.remove(element));
    }
  }

  @Contract(pure = true)
  public static @NotNull <T> List<T> createMaybeSingletonList(@Nullable T element) {
    return element == null ? emptyList() : Collections.singletonList(element);
  }

  @Contract(pure = true)
  public static @NotNull <T> Set<T> createMaybeSingletonSet(@Nullable T element) {
    return element == null ? Collections.emptySet() : Collections.singleton(element);
  }

  public static @NotNull <T, V> V getOrCreate(@NotNull Map<T, V> result, final T key, @NotNull V defaultValue) {
    return result.computeIfAbsent(key, __ -> defaultValue);
  }

  public static <T, V> V getOrCreate(@NotNull Map<T, V> result, final T key, @NotNull Factory<? extends V> factory) {
    V value = result.get(key);
    if (value == null) {
      result.put(key, value = factory.create());
    }
    return value;
  }

  @Contract(pure = true)
  public static @NotNull <T, V> V getOrElse(@NotNull Map<? extends T, ? extends V> map, final T key, @NotNull V defValue) {
    V value = map.get(key);
    return value == null ? defValue : value;
  }

  @Contract(pure=true)
  public static <T> boolean and(T @NotNull [] iterable, @NotNull Condition<? super T> condition) {
    for (final T t : iterable) {
      if (!condition.value(t)) return false;
    }
    return true;
  }

  @Contract(pure=true)
  public static <T> boolean and(@NotNull Iterable<? extends T> iterable, @NotNull Condition<? super T> condition) {
    for (final T t : iterable) {
      if (!condition.value(t)) return false;
    }
    return true;
  }

  @Contract(pure=true)
  public static <T> boolean exists(T @NotNull [] array, @NotNull Condition<? super T> condition) {
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
  public static <T> boolean or(T @NotNull [] iterable, @NotNull Condition<? super T> condition) {
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

  /**
   * @deprecated Use {@link Arrays#asList(Object[])}
   */
  @SafeVarargs
  @Contract(pure = true)
  @Deprecated
  public static @NotNull <T> List<T> list(T @NotNull ... items) {
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
    int s = Math.min(a - off, b - a);
    vecswap(x, off, b - s, s);
    int n = off + len;
    s = Math.min(d - c, n - d - 1);
    vecswap(x, b, n - s, s);

    // Recursively sort non-partition-elements
    if ((s = b - a) > 1) quickSort(x, comparator, off, s);
    if ((s = d - c) > 1) quickSort(x, comparator, n - s, s);
  }

  /*
   * Returns the index of the median of the three indexed longs.
   */
  private static <T> int med3(@NotNull List<? extends T> x, @NotNull Comparator<? super T> comparator, int a, int b, int c) {
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
  @Contract(pure = true)
  public static @NotNull <T> Set<T> singleton(final T o, final @NotNull TObjectHashingStrategy<T> strategy) {
    return strategy == canonicalStrategy() ? new SingletonSet<>(o) : SingletonSet.withCustomStrategy(o, strategy);
  }

  /**
   * @return read-only list consisting of the elements from all of the collections
   */
  @Contract(pure = true)
  public static @NotNull <E> List<E> flatten(Collection<E> @NotNull [] collections) {
    return flatten(Arrays.asList(collections));
  }

  /**
   * Processes the list, remove all duplicates and return the list with unique elements.
   * @param list must be sorted (according to the comparator), all elements must be not-null
   */
  public static @NotNull <T> List<T> removeDuplicatesFromSorted(@NotNull List<T> list, @NotNull Comparator<? super T> comparator) {
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
          result = new ArrayList<>(list.size());
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
  @Contract(pure = true)
  public static @NotNull <E> List<E> flatten(@NotNull Iterable<? extends Collection<? extends E>> collections) {
    int totalSize = 0;
    for (Collection<? extends E> list : collections) {
      totalSize += list.size();
    }
    List<E> result = new ArrayList<>(totalSize);
    for (Collection<? extends E> list : collections) {
      result.addAll(list);
    }

    return result.isEmpty() ? emptyList() : result;
  }

  /**
   * @return read-only list consisting of the elements from all of the collections
   */
  @Contract(pure = true)
  public static @NotNull <E> List<E> flattenIterables(@NotNull Iterable<? extends Iterable<? extends E>> collections) {
    int totalSize = 0;
    for (Iterable<? extends E> list : collections) {
      totalSize += list instanceof Collection ? ((Collection<?>)list).size() : 10;
    }
    List<E> result = new ArrayList<>(totalSize);
    for (Iterable<? extends E> list : collections) {
      for (E e : list) {
        result.add(e);
      }
    }
    return result.isEmpty() ? emptyList() : result;
  }

  /**
   * @return read-only list consisting of the elements from all of the collections returned by the mapping function,
   * or a read-only view of the list returned by the mapping function, if it only returned a single list that was not empty
   */
  public static @NotNull <T, V> List<V> flatMap(@NotNull Iterable<? extends T> iterable, @NotNull Function<? super T, ? extends List<V>> mapping) {
    // GC optimization for critical clients
    List<V> result = null;
    boolean isOriginal = true;

    for (T each : iterable) {
      List<V> toAdd = mapping.fun(each);
      if (toAdd.isEmpty()) continue;

      if (result == null) {
        result = toAdd;
        continue;
      }

      if (isOriginal) {
        List<V> original = result;
        result = new ArrayList<>(Math.max(10, result.size() + toAdd.size()));
        result.addAll(original);
        isOriginal = false;
      }

      result.addAll(toAdd);
    }

    return result == null ? emptyList() : Collections.unmodifiableList(result);
  }

  public static <K,V> V @NotNull [] convert(K @NotNull [] from, V @NotNull [] to, @NotNull Function<? super K, ? extends V> fun) {
    if (to.length < from.length) {
      to = ArrayUtil.newArray(ArrayUtil.getComponentType(to), from.length);
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

  /**
   * Finds the first element in the list that satisfies given condition.
   *
   * @param list list to scan
   * @param condition condition that should be satisfied
   * @param <T> type of the list elements
   * @return index of the first element in the list that satisfies the condition; -1 if no element in the list satisfies the condition.
   */
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

  @Contract(pure = true)
  public static <T, U extends T> U findLastInstance(@NotNull List<? extends T> list, final @NotNull Class<? extends U> clazz) {
    int i = lastIndexOf(list, (Condition<T>)clazz::isInstance);
    //noinspection unchecked
    return i < 0 ? null : (U)list.get(i);
  }

  @Contract(pure = true)
  public static <T, U extends T> int lastIndexOfInstance(@NotNull List<? extends T> list, final @NotNull Class<U> clazz) {
    return lastIndexOf(list, (Condition<T>)clazz::isInstance);
  }

  @Contract(pure = true)
  public static @NotNull <A,B> Map<B,A> reverseMap(@NotNull Map<? extends A, ? extends B> map) {
    final Map<B,A> result = new HashMap<>();
    for (Map.Entry<? extends A, ? extends B> entry : map.entrySet()) {
      result.put(entry.getValue(), entry.getKey());
    }
    return result;
  }

  @Contract("null -> null; !null -> !null")
  public static <T> List<T> trimToSize(@Nullable List<T> list) {
    if (list == null) return null;
    if (list.isEmpty()) return emptyList();

    if (list instanceof ArrayList) {
      ((ArrayList<T>)list).trimToSize();
    }

    return list;
  }

  /**
   * @deprecated Use {@link Stack#Stack()}
   */
  @Contract(value = " -> new", pure = true)
  @Deprecated
  public static @NotNull <T> Stack<T> newStack() {
    return new Stack<>();
  }

  @Contract(pure = true)
  public static @NotNull <T> List<T> emptyList() {
    //noinspection deprecation
    return ContainerUtilRt.emptyList();
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <T> CopyOnWriteArrayList<T> createEmptyCOWList() {
    // does not create garbage new Object[0]
    return new CopyOnWriteArrayList<>(emptyList());
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
  @Contract(value = " -> new", pure = true)
  public static @NotNull <T> List<T> createLockFreeCopyOnWriteList() {
    return createConcurrentList();
  }

  @Contract(value = "_ -> new", pure = true)
  public static @NotNull <T> List<T> createLockFreeCopyOnWriteList(@NotNull Collection<? extends T> c) {
    return new LockFreeCopyOnWriteArrayList<>(c);
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <V> ConcurrentIntObjectMap<V> createConcurrentIntObjectMap() {
    return new ConcurrentIntObjectHashMap<>();
  }

  @Contract(value = "_,_,_ -> new", pure = true)
  public static @NotNull <V> ConcurrentIntObjectMap<V> createConcurrentIntObjectMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
    return new ConcurrentIntObjectHashMap<>(initialCapacity, loadFactor, concurrencyLevel);
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <V> ConcurrentIntObjectMap<V> createConcurrentIntObjectSoftValueMap() {
    return new ConcurrentIntKeySoftValueHashMap<>();
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <V> ConcurrentLongObjectMap<V> createConcurrentLongObjectMap() {
    return new ConcurrentLongObjectHashMap<>();
  }

  @Contract(value = "_ -> new", pure = true)
  public static @NotNull <V> ConcurrentLongObjectMap<V> createConcurrentLongObjectMap(int initialCapacity) {
    return new ConcurrentLongObjectHashMap<>(initialCapacity);
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K,V> ConcurrentMap<K,V> createConcurrentWeakValueMap() {
    return new ConcurrentWeakValueHashMap<>();
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <V> ConcurrentIntObjectMap<V> createConcurrentIntObjectWeakValueMap() {
    return new ConcurrentIntKeyWeakValueHashMap<>();
  }

  @Contract(value = "_,_,_,_ -> new", pure = true)
  public static @NotNull <K,V> ConcurrentMap<K,V> createConcurrentWeakKeySoftValueMap(int initialCapacity,
                                                                                      float loadFactor,
                                                                                      int concurrencyLevel,
                                                                                      final @NotNull TObjectHashingStrategy<? super K> hashingStrategy) {
    //noinspection deprecation
    return new ConcurrentWeakKeySoftValueHashMap<>(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K,V> ConcurrentMap<K,V> createConcurrentSoftKeySoftValueMap() {
    return createConcurrentSoftKeySoftValueMap(100, 0.75f, Runtime.getRuntime().availableProcessors(), canonicalStrategy());
  }

  @Contract(value = "_,_,_,_ -> new", pure = true)
  public static @NotNull <K,V> ConcurrentMap<K,V> createConcurrentSoftKeySoftValueMap(int initialCapacity,
                                                                                      float loadFactor,
                                                                                      int concurrencyLevel,
                                                                                      final @NotNull TObjectHashingStrategy<? super K> hashingStrategy) {
    return new ConcurrentSoftKeySoftValueHashMap<>(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K,V> ConcurrentMap<K,V> createConcurrentWeakKeySoftValueMap() {
    return createConcurrentWeakKeySoftValueMap(100, 0.75f, Runtime.getRuntime().availableProcessors(), canonicalStrategy());
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K,V> ConcurrentMap<K,V> createConcurrentWeakKeyWeakValueMap() {
    return createConcurrentWeakKeyWeakValueMap(canonicalStrategy());
  }

  @Contract(value = "_ -> new", pure = true)
  public static @NotNull <K,V> ConcurrentMap<K,V> createConcurrentWeakKeyWeakValueMap(@NotNull TObjectHashingStrategy<? super K> strategy) {
    return new ConcurrentWeakKeyWeakValueHashMap<>(100, 0.75f, Runtime.getRuntime().availableProcessors(),
                                                   strategy);
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K, V> ConcurrentMap<K,V> createConcurrentSoftValueMap() {
    return new ConcurrentSoftValueHashMap<>();
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K,V> ConcurrentMap<K,V> createConcurrentSoftMap() {
    return new ConcurrentSoftHashMap<>();
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K,V> ConcurrentMap<K,V> createConcurrentWeakMap() {
    return new ConcurrentWeakHashMap<>(0.75f);
  }

  @Contract(value = "_,_,_,_ -> new", pure = true)
  public static @NotNull <K,V> ConcurrentMap<K,V> createConcurrentSoftMap(int initialCapacity,
                                                                          float loadFactor,
                                                                          int concurrencyLevel,
                                                                          @NotNull TObjectHashingStrategy<? super K> hashingStrategy) {
    return new ConcurrentSoftHashMap<>(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
  }

  @Contract(value = "_,_,_,_ -> new", pure = true)
  public static @NotNull <K,V> ConcurrentMap<K,V> createConcurrentWeakMap(int initialCapacity,
                                                                          float loadFactor,
                                                                          int concurrencyLevel,
                                                                          @NotNull TObjectHashingStrategy<? super K> hashingStrategy) {
    return new ConcurrentWeakHashMap<>(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
  }

  @Contract(value = "_ -> new", pure = true)
  public static @NotNull <K,V> ConcurrentMap<K,V> createConcurrentWeakMap(@NotNull TObjectHashingStrategy<? super K> hashingStrategy) {
    return new ConcurrentWeakHashMap<>(hashingStrategy);
  }

  /**
   * @see #createLockFreeCopyOnWriteList()
   */
  @Contract(value = " -> new", pure = true)
  public static @NotNull <T> ConcurrentList<T> createConcurrentList() {
    return new LockFreeCopyOnWriteArrayList<>();
  }

  @Contract(value = "_ -> new", pure = true)
  public static @NotNull <T> ConcurrentList<T> createConcurrentList(@NotNull Collection <? extends T> collection) {
    return new LockFreeCopyOnWriteArrayList<>(collection);
  }

  /**
   * @deprecated use {@link #addIfNotNull(Collection, Object)} instead
   */
  @Deprecated
  public static <T> void addIfNotNull(@Nullable T element, @NotNull Collection<? super T> result) {
    addIfNotNull(result,element);
  }

  public static <T> void addIfNotNull(@NotNull Collection<? super T> result, @Nullable T element) {
    if (element != null) {
      result.add(element);
    }
  }

  @Contract(pure = true)
  public static @NotNull <T, V> List<V> map2List(T @NotNull [] array, @NotNull Function<? super T, ? extends V> mapper) {
    if (array.length == 0) return emptyList();
    List<V> list = new ArrayList<>(array.length);
    for (final T t : array) {
      list.add(mapper.fun(t));
    }
    return list;
  }

  @Contract(pure = true)
  public static @NotNull <T, V> List<V> map2List(@NotNull Collection<? extends T> collection, @NotNull Function<? super T, ? extends V> mapper) {
    if (collection.isEmpty()) return emptyList();
    List<V> list = new ArrayList<>(collection.size());
    for (final T t : collection) {
      list.add(mapper.fun(t));
    }
    return list;
  }

  @Contract(pure = true)
  public static @NotNull <K, V> List<Pair<K, V>> map2List(@NotNull Map<? extends K, ? extends V> map) {
    if (map.isEmpty()) return emptyList();
    final List<Pair<K, V>> result = new ArrayList<>(map.size());
    for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
      result.add(Pair.create(entry.getKey(), entry.getValue()));
    }
    return result;
  }

  @Contract(pure = true)
  public static @NotNull <T, V> Set<V> map2Set(T @NotNull [] array, @NotNull Function<? super T, ? extends V> mapper) {
    if (array.length == 0) return Collections.emptySet();
    Set<V> set = new HashSet<>(array.length);
    for (final T t : array) {
      set.add(mapper.fun(t));
    }
    return set;
  }

  @Contract(pure = true)
  public static @NotNull <T, V> Set<V> map2Set(@NotNull Collection<? extends T> collection, @NotNull Function<? super T, ? extends V> mapper) {
    if (collection.isEmpty()) return Collections.emptySet();
    Set <V> set = new HashSet<>(collection.size());
    for (final T t : collection) {
      set.add(mapper.fun(t));
    }
    return set;
  }

  @Contract(pure = true)
  public static @NotNull <T, V> Set<V> map2LinkedSet(@NotNull Collection<? extends T> collection, @NotNull Function<? super T, ? extends V> mapper) {
    if (collection.isEmpty()) return Collections.emptySet();
    Set <V> set = new LinkedHashSet<>(collection.size());
    for (final T t : collection) {
      set.add(mapper.fun(t));
    }
    return set;
  }

  @Contract(pure = true)
  public static @NotNull <T, V> Set<V> map2SetNotNull(@NotNull Collection<? extends T> collection, @NotNull Function<? super T, ? extends V> mapper) {
    if (collection.isEmpty()) return Collections.emptySet();
    Set <V> set = new HashSet<>(collection.size());
    for (T t : collection) {
      V value = mapper.fun(t);
      if (value != null) {
        set.add(value);
      }
    }
    return set.isEmpty() ? Collections.emptySet() : set;
  }

  /**
   * @deprecated use {@link List#toArray(Object[])} instead
   */
  @Deprecated
  @Contract(pure=true)
  public static <T> T @NotNull [] toArray(@NotNull List<T> collection, T @NotNull [] array) {
    return collection.toArray(array);
  }

  /**
   * @deprecated use {@link Collection#toArray(Object[])} instead
   */
  @Deprecated
  @Contract(pure=true)
  public static <T> T @NotNull [] toArray(@NotNull Collection<? extends T> c, T @NotNull [] sample) {
    return c.toArray(sample);
  }

  public static <T> T @NotNull [] copyAndClear(@NotNull Collection<? extends T> collection, @NotNull ArrayFactory<? extends T> factory, boolean clear) {
    int size = collection.size();
    T[] a = factory.create(size);
    if (size > 0) {
      a = collection.toArray(a);
      if (clear) collection.clear();
    }
    return a;
  }

  public static @NotNull <T> List<T> copyList(@NotNull List<? extends T> list) {
    if (list == Collections.emptyList()) {
      return Collections.emptyList();
    }
    if (list.size() == 1) {
      return new SmartList<>(list.get(0));
    }
    if (list.isEmpty()) {
      return new SmartList<>();
    }
    return new ArrayList<>(list);
  }

  @Contract(pure = true)
  public static @NotNull <T> Collection<T> toCollection(@NotNull Iterable<? extends T> iterable) {
    //noinspection unchecked
    return iterable instanceof Collection ? (Collection<T>)iterable : newArrayList(iterable);
  }

  public static @NotNull <T> List<T> toList(@NotNull Enumeration<? extends T> enumeration) {
    if (!enumeration.hasMoreElements()) {
      return Collections.emptyList();
    }

    List<T> result = new SmartList<>();
    while (enumeration.hasMoreElements()) {
      result.add(enumeration.nextElement());
    }
    return result;
  }

  @Contract(value = "null -> true", pure = true)
  public static <T> boolean isEmpty(@Nullable Collection<? extends T> collection) {
    return collection == null || collection.isEmpty();
  }

  @Contract(value = "null -> true", pure = true)
  public static boolean isEmpty(@Nullable Map<?, ?> map) {
    return map == null || map.isEmpty();
  }

  @Contract(pure = true)
  public static @NotNull <T> List<T> notNullize(@Nullable List<T> list) {
    return list == null ? emptyList() : list;
  }

  @Contract(pure = true)
  public static @NotNull <T> Set<T> notNullize(@Nullable Set<T> set) {
    return set == null ? Collections.emptySet() : set;
  }

  @Contract(pure = true)
  public static @NotNull <K, V> Map<K, V> notNullize(@Nullable Map<K, V> map) {
    return map == null ? Collections.emptyMap() : map;
  }

  @Contract(pure = true)
  public static <T> boolean startsWith(@NotNull List<? extends T> list, @NotNull List<? extends T> prefix) {
    return list.size() >= prefix.size() && list.subList(0, prefix.size()).equals(prefix);
  }

  @Contract(pure = true)
  public static @Nullable <T, C extends Collection<? extends T>> C nullize(@Nullable C collection) {
    return isEmpty(collection) ? null : collection;
  }

  @Contract(pure=true)
  public static <T extends Comparable<? super T>> int compareLexicographically(@NotNull List<? extends T> o1, @NotNull List<? extends T> o2) {
    for (int i = 0; i < Math.min(o1.size(), o2.size()); i++) {
      int result = Comparing.compare(o1.get(i), o2.get(i));
      if (result != 0) {
        return result;
      }
    }
    return Integer.compare(o1.size(), o2.size());
  }

  @Contract(pure=true)
  public static <T> int compareLexicographically(@NotNull List<? extends T> o1, @NotNull List<? extends T> o2, @NotNull Comparator<? super T> comparator) {
    for (int i = 0; i < Math.min(o1.size(), o2.size()); i++) {
      int result = comparator.compare(o1.get(i), o2.get(i));
      if (result != 0) {
        return result;
      }
    }
    return Integer.compare(o1.size(), o2.size());
  }

  /**
   * Returns a String representation of the given map, by listing all key-value pairs contained in the map.
   */
  @Contract(pure = true)
  public static @NotNull String toString(@NotNull Map<?, ?> map) {
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

  public static final class KeyOrderedMultiMap<K extends Comparable<? super K>, V> extends MultiMap<K, V> {
    public KeyOrderedMultiMap() {
      super(new TreeMap<>());
    }

    public KeyOrderedMultiMap(@NotNull MultiMap<? extends K, ? extends V> toCopy) {
      super(new TreeMap<>());
      putAllValues(toCopy);
    }

    public @NotNull NavigableSet<K> navigableKeySet() {
      return ((TreeMap<K, Collection<V>>)myMap).navigableKeySet();
    }
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K,V> Map<K,V> createWeakKeySoftValueMap() {
    return new WeakKeySoftValueHashMap<>();
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K,V> Map<K,V> createWeakKeyWeakValueMap() {
    return new WeakKeyWeakValueHashMap<>();
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <K,V> Map<K,V> createSoftKeySoftValueMap() {
    return new SoftKeySoftValueHashMap<>();
  }

  /**
   * Hard keys soft values hash map.
   * Null keys are NOT allowed
   * Null values are allowed
   */
  @Contract(value = " -> new", pure = true)
  public static @NotNull <K,V> Map<K,V> createSoftValueMap() {
    return new SoftValueHashMap<>(canonicalStrategy());
  }

  /**
   * Hard keys weak values hash map.
   * Null keys are NOT allowed
   * Null values are allowed
   */
  @Contract(value = " -> new", pure = true)
  public static @NotNull <K,V> Map<K,V> createWeakValueMap() {
    //noinspection deprecation
    return new WeakValueHashMap<>(canonicalStrategy());
  }

  /**
   * Soft keys hard values hash map.
   * Null keys are NOT allowed
   * Null values are allowed
   */
  @Contract(value = " -> new", pure = true)
  public static @NotNull <K,V> Map<K,V> createSoftMap() {
    //noinspection deprecation
    return new SoftHashMap<>(4);
  }

  @Contract(value = "_ -> new", pure = true)
  public static @NotNull <K,V> Map<K,V> createSoftMap(@NotNull TObjectHashingStrategy<? super K> strategy) {
    //noinspection deprecation
    return new SoftHashMap<>(strategy);
  }

  /**
   * Weak keys hard values hash map.
   * Null keys are NOT allowed
   * Null values are allowed
   */
  @Contract(value = " -> new", pure = true)
  public static @NotNull <K,V> Map<K,V> createWeakMap() {
    return createWeakMap(4);
  }

  @Contract(value = "_ -> new", pure = true)
  public static @NotNull <K,V> Map<K,V> createWeakMap(int initialCapacity) {
    return createWeakMap(initialCapacity, 0.8f, canonicalStrategy());
  }

  @Contract(value = "_, _, _ -> new", pure = true)
  public static @NotNull <K,V> Map<K,V> createWeakMap(int initialCapacity, float loadFactor, @NotNull TObjectHashingStrategy<? super K> strategy) {
    //noinspection deprecation
    return new WeakHashMap<>(initialCapacity, loadFactor, strategy);
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <T> Set<T> createWeakSet() {
    return new WeakHashSet<>();
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <T> IntObjectMap<T> createIntKeyWeakValueMap() {
    return new IntKeyWeakValueHashMap<>();
  }

  @Contract(value = " -> new", pure = true)
  public static @NotNull <T> ObjectIntMap<T> createWeakKeyIntValueMap() {
    return new WeakKeyIntValueHashMap<>();
  }

  /**
   * Create an immutable copy of the {@code list}.
   * Modifications of the {@code list} have no effect on the returned copy.
   */
  @SuppressWarnings("unchecked")
  @Contract(value = "_ -> new", pure = true)
  public static @NotNull <T> List<T> freeze(@NotNull List<? extends T> list) {
    if (list.isEmpty()) {
      return Collections.emptyList();
    }
    else if (list.size() == 1) {
      return immutableSingletonList(list.get(0));
    }
    else {
      return immutableList((T[])list.toArray());
    }
  }
}
