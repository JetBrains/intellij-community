// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
public class ContainerUtil extends ContainerUtilRt {
  private static final int INSERTION_SORT_THRESHOLD = 10;

  @SafeVarargs
  @NotNull
  @Contract(pure=true)
  public static <T> T[] ar(@NotNull T... elements) {
    return elements;
  }

  /**
   * @deprecated Use {@link HashMap#HashMap()}
   */
  @NotNull
  @Contract(pure=true)
  @Deprecated
  public static <K, V> HashMap<K, V> newHashMap() {
    return new HashMap<>();
  }

  /**
   * @deprecated Use {@link HashMap#HashMap(Map)}
   */
  @NotNull
  @Contract(pure=true)
  @Deprecated
  public static <K, V> HashMap<K, V> newHashMap(@NotNull Map<? extends K, ? extends V> map) {
    return new HashMap<>(map);
  }

  @NotNull
  @SafeVarargs
  @Contract(pure=true)
  public static <K, V> Map<K, V> newHashMap(@NotNull Pair<? extends K, ? extends V> first, @NotNull Pair<? extends K, ? extends V>... entries) {
    Map<K, V> map = new HashMap<>(entries.length + 1);
    map.put(first.getFirst(), first.getSecond());
    for (Pair<? extends K, ? extends V> entry : entries) {
      map.put(entry.getFirst(), entry.getSecond());
    }
    return map;
  }

  @NotNull
  @Contract(pure=true)
  public static <K, V> Map<K, V> newHashMap(@NotNull List<? extends K> keys, @NotNull List<? extends V> values) {
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
  @NotNull
  @Contract(pure=true)
  @Deprecated
  public static <K extends Comparable<? super K>, V> TreeMap<K, V> newTreeMap() {
    return new TreeMap<>();
  }

  /**
   * @deprecated Use {@link TreeMap#TreeMap(Map)}
   */
  @SuppressWarnings("unused")
  @NotNull
  @Contract(pure=true)
  @Deprecated
  public static <K extends Comparable<? super K>, V> TreeMap<K, V> newTreeMap(@NotNull Map<? extends K, ? extends V> map) {
    return new TreeMap<>(map);
  }

  /**
   * @deprecated Use {@link LinkedHashMap#LinkedHashMap()}
   */
  @NotNull
  @Contract(pure=true)
  @Deprecated
  public static <K, V> LinkedHashMap<K, V> newLinkedHashMap() {
    return new LinkedHashMap<>();
  }

  /**
   * @deprecated Use {@link LinkedHashMap#LinkedHashMap(Map)}
   */
  @NotNull
  @Contract(pure=true)
  @Deprecated
  public static <K, V> LinkedHashMap<K, V> newLinkedHashMap(@NotNull Map<? extends K, ? extends V> map) {
    return new LinkedHashMap<>(map);
  }

  @NotNull
  @SafeVarargs
  @Contract(pure=true)
  public static <K, V> LinkedHashMap<K, V> newLinkedHashMap(@NotNull Pair<? extends K, ? extends V> first, @NotNull Pair<? extends K, ? extends V>... entries) {
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
  @NotNull
  @Contract(pure=true)
  public static <K, V> THashMap<K, V> newTroveMap() {
    return new THashMap<>();
  }

  /**
   * @deprecated Use {@link THashMap#THashMap(TObjectHashingStrategy)}
   */
  @Deprecated
  @NotNull
  @Contract(pure=true)
  public static <K, V> THashMap<K, V> newTroveMap(@NotNull TObjectHashingStrategy<K> strategy) {
    return new THashMap<>(strategy);
  }

  /**
   * @deprecated Use {@link EnumMap#EnumMap(Class)}
   */
  @NotNull
  @Contract(pure=true)
  @Deprecated
  public static <K extends Enum<K>, V> EnumMap<K, V> newEnumMap(@NotNull Class<K> keyType) {
    return new EnumMap<>(keyType);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> TObjectHashingStrategy<T> canonicalStrategy() {
    //noinspection unchecked
    return TObjectHashingStrategy.CANONICAL;
  }

  @NotNull
  @Contract(pure=true)
  public static <T> TObjectHashingStrategy<T> identityStrategy() {
    //noinspection unchecked
    return TObjectHashingStrategy.IDENTITY;
  }

  @NotNull
  @Contract(pure=true)
  public static <K, V> IdentityHashMap<K, V> newIdentityHashMap() {
    return new IdentityHashMap<>();
  }

  /**
   * @deprecated Use {@link LinkedList#LinkedList()}
   */
  @NotNull
  @Contract(pure=true)
  @Deprecated
  public static <T> LinkedList<T> newLinkedList() {
    return new LinkedList<>();
  }

  @SafeVarargs
  @NotNull
  @Contract(pure=true)
  public static <T> LinkedList<T> newLinkedList(@NotNull T... elements) {
    final LinkedList<T> list = new LinkedList<>();
    Collections.addAll(list, elements);
    return list;
  }

  /**
   * @deprecated Use {@link ArrayList#ArrayList()}
   */
  @Deprecated
  @NotNull
  @Contract(pure=true)
  public static <T> ArrayList<T> newArrayList() {
    return new ArrayList<>();
  }

  @SafeVarargs
  @NotNull
  @Contract(pure=true)
  public static <E> ArrayList<E> newArrayList(@NotNull E... array) {
    return new ArrayList<>(Arrays.asList(array));
  }

  /**
   * @deprecated Use {@link ArrayList#ArrayList(Collection)} instead
   */
  @Deprecated
  @NotNull
  @Contract(pure=true)
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  public static <E> ArrayList<E> newArrayList(@NotNull Collection<? extends E> iterable) {
    DeprecatedMethodException.report("Use `new ArrayList(Collection)` instead. "+iterable.getClass());
    return new ArrayList<>(iterable);
  }
  @NotNull
  @Contract(pure=true)
  public static <E> ArrayList<E> newArrayList(@NotNull Iterable<? extends E> iterable) {
    //noinspection deprecation
    return ContainerUtilRt.newArrayList(iterable);
  }

  /**
   * @deprecated Use {@link ArrayList#ArrayList(int)}
   */
  @NotNull
  @Contract(pure=true)
  @Deprecated
  public static <T> ArrayList<T> newArrayListWithCapacity(int size) {
    return new ArrayList<>(size);
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
  public static <T> List<T> newUnmodifiableList(@NotNull List<? extends T> originalList) {
    int size = originalList.size();
    if (size == 0) {
      return emptyList();
    }
    if (size == 1) {
      return Collections.singletonList(originalList.get(0));
    }
    return Collections.unmodifiableList(new ArrayList<>(originalList));
  }

  @NotNull
  @Contract(pure = true)
  public static <T> Collection<T> unmodifiableOrEmptyCollection(@NotNull Collection<? extends T> original) {
    int size = original.size();
    if (size == 0) {
      return emptyList();
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
    return Collections.unmodifiableList(original);
  }

  @NotNull
  @Contract(pure = true)
  public static <T> Set<T> unmodifiableOrEmptySet(@NotNull Set<? extends T> original) {
    int size = original.size();
    if (size == 0) {
      return Collections.emptySet();
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
    return Collections.unmodifiableMap(original);
  }

  /**
   * @deprecated Use {@link SmartList()}
   */
  @NotNull
  @Deprecated
  public static <T> List<T> newSmartList() {
    return new SmartList<>();
  }

  /**
   * @deprecated Use {@link SmartList(T)}
   */
  @NotNull
  @Deprecated
  public static <T> List<T> newSmartList(T element) {
    return new SmartList<>(element);
  }

  /**
    * @deprecated Use {@link SmartList(T)}
    */
  @SafeVarargs
  @NotNull
  @Contract(pure=true)
  @Deprecated
  public static <T> List<T> newSmartList(@NotNull T... elements) {
    return new SmartList<>(elements);
  }

  /**
   * @deprecated Use {@link HashSet#HashSet()}
   */
  @NotNull
  @Contract(pure=true)
  @Deprecated
  public static <T> HashSet<T> newHashSet() {
    return new HashSet<>();
  }

  /**
   * @deprecated Use {@link HashSet#HashSet(int)}
   */
  @NotNull
  @Contract(pure=true)
  @Deprecated
  public static <T> HashSet<T> newHashSet(int initialCapacity) {
    return new HashSet<>(initialCapacity);
  }

  @SafeVarargs
  @NotNull
  @Contract(pure=true)
  public static <T> HashSet<T> newHashSet(@NotNull T... elements) {
    return new HashSet<>(Arrays.asList(elements));
  }

  @NotNull
  @Contract(pure=true)
  public static <T> HashSet<T> newHashSet(@NotNull Iterable<? extends T> iterable) {
    return ContainerUtilRt.newHashSet(iterable);
  }

  /**
   * @deprecated Use {@link HashSet#HashSet(Collection)}
   */
  @NotNull
  @Contract(pure=true)
  @Deprecated
  public static <T> HashSet<T> newHashSet(@NotNull Collection<? extends T> collection) {
    return new HashSet<>(collection);
  }

  @NotNull
  public static <T> HashSet<T> newHashSet(@NotNull Iterator<? extends T> iterator) {
    HashSet<T> set = new HashSet<>();
    while (iterator.hasNext()) set.add(iterator.next());
    return set;
  }

  @NotNull
  @Contract(pure=true)
  public static <T> Set<T> newHashOrEmptySet(@Nullable Iterable<? extends T> iterable) {
    boolean empty = iterable == null || iterable instanceof Collection && ((Collection<?>)iterable).isEmpty();
    return empty ? Collections.emptySet() : ContainerUtilRt.newHashSet(iterable);
  }

  /**
   * @deprecated Use {@link LinkedHashSet#LinkedHashSet()}
   */
  @NotNull
  @Contract(pure=true)
  @Deprecated
  public static <T> LinkedHashSet<T> newLinkedHashSet() {
    return new LinkedHashSet<>();
  }

  @NotNull
  @Contract(pure=true)
  public static <T> LinkedHashSet<T> newLinkedHashSet(@NotNull Iterable<? extends T> elements) {
    return copy(new LinkedHashSet<>(), elements);
  }

  /**
   * @deprecated Use {@link LinkedHashSet#LinkedHashSet(Collection)} instead
   */
  @Deprecated
  @NotNull
  @Contract(pure=true)
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  public static <T> LinkedHashSet<T> newLinkedHashSet(@NotNull Collection<? extends T> elements) {
    DeprecatedMethodException.report("Use `new LinkedHashSet(Collection)` instead. "+elements.getClass());
    return new LinkedHashSet<>(elements);
  }

  @SafeVarargs
  @NotNull
  @Contract(pure=true)
  public static <T> LinkedHashSet<T> newLinkedHashSet(@NotNull T... elements) {
    return new LinkedHashSet<>(Arrays.asList(elements));
  }

  /**
   * @deprecated Use {@link THashSet#THashSet()}
   */
  @Deprecated
  @NotNull
  @Contract(pure=true)
  public static <T> THashSet<T> newTroveSet() {
    return new THashSet<>();
  }

  /**
   * @deprecated Use {@link THashSet#THashSet(TObjectHashingStrategy)}
   */
  @Deprecated
  @NotNull
  @Contract(pure=true)
  public static <T> THashSet<T> newTroveSet(@NotNull TObjectHashingStrategy<T> strategy) {
    return new THashSet<>(strategy);
  }

  @SafeVarargs
  @NotNull
  @Contract(pure=true)
  public static <T> THashSet<T> newTroveSet(@NotNull T... elements) {
    return new THashSet<>(Arrays.asList(elements));
  }

  @SafeVarargs
  @NotNull
  @Contract(pure=true)
  public static <T> THashSet<T> newTroveSet(@NotNull TObjectHashingStrategy<T> strategy, @NotNull T... elements) {
    return new THashSet<>(Arrays.asList(elements), strategy);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> THashSet<T> newTroveSet(@NotNull TObjectHashingStrategy<T> strategy, @NotNull Collection<? extends T> elements) {
    return new THashSet<>(elements, strategy);
  }

  /**
   * @deprecated Use {@link THashSet#THashSet(Collection)}
   */
  @NotNull
  @Contract(pure=true)
  @Deprecated
  public static <T> THashSet<T> newTroveSet(@NotNull Collection<? extends T> elements) {
    return new THashSet<>(elements);
  }

  @NotNull
  @Contract(pure=true)
  public static <K> THashSet<K> newIdentityTroveSet() {
    return new THashSet<>(identityStrategy());
  }

  @NotNull
  @Contract(pure=true)
  public static <K> THashSet<K> newIdentityTroveSet(int initialCapacity) {
    return new THashSet<>(initialCapacity, identityStrategy());
  }
  @NotNull
  @Contract(pure=true)
  public static <K> THashSet<K> newIdentityTroveSet(@NotNull Collection<? extends K> collection) {
    return new THashSet<>(collection, identityStrategy());
  }

  @NotNull
  @Contract(pure=true)
  public static <K,V> THashMap<K,V> newIdentityTroveMap() {
    return new THashMap<>(identityStrategy());
  }

  /**
   * @deprecated Use {@link TreeSet#TreeSet()}
   */
  @Deprecated
  @NotNull
  @Contract(pure=true)
  public static <T extends Comparable<? super T>> TreeSet<T> newTreeSet() {
    return new TreeSet<>();
  }

  /**
   * @deprecated Use {@link TreeSet#TreeSet(Comparator)}
   */
  @NotNull
  @Contract(pure=true)
  @Deprecated
  public static <T> TreeSet<T> newTreeSet(@Nullable Comparator<? super T> comparator) {
    return new TreeSet<>(comparator);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> Set<T> newConcurrentSet() {
    return Collections.newSetFromMap(newConcurrentMap());
  }

  @NotNull
  @Contract(pure=true)
  public static <K, V> ConcurrentMap<K, V> newConcurrentMap() {
    return new ConcurrentHashMap<>();
  }

  @Contract(pure=true)
  public static <K, V> ConcurrentMap<K,V> newConcurrentMap(int initialCapacity) {
    return new ConcurrentHashMap<>(initialCapacity);
  }

  @Contract(pure=true)
  public static <K, V> ConcurrentMap<K,V> newConcurrentMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
    return new ConcurrentHashMap<>(initialCapacity, loadFactor, concurrencyLevel);
  }

  @NotNull
  @Contract(pure=true)
  public static <E> List<E> reverse(@NotNull final List<? extends E> elements) {
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

  @NotNull
  @Contract(pure=true)
  public static <K, V> Map<K, V> union(@NotNull Map<? extends K, ? extends V> map, @NotNull Map<? extends K, ? extends V> map2) {
    Map<K, V> result = new THashMap<>(map.size() + map2.size());
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
    Set<T> result = new THashSet<>(set.size() + set2.size());
    result.addAll(set);
    result.addAll(set2);
    return result;
  }

  @SafeVarargs
  @NotNull
  @Contract(pure=true)
  public static <E> Set<E> immutableSet(@NotNull E... elements) {
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
  @NotNull
  @Contract(pure=true)
  public static <E> ImmutableList<E> immutableList(@NotNull E... array) {
    return new ImmutableListBackedByArray<>(array);
  }

  @NotNull
  @Contract(pure=true)
  public static <E> ImmutableList<E> immutableSingletonList(final E element) {
    return ImmutableList.singleton(element);
  }

  @NotNull
  @Contract(pure=true)
  public static <E> ImmutableList<E> immutableList(@NotNull List<? extends E> list) {
    return new ImmutableListBackedByList<>(list);
  }

  @NotNull
  @Contract(pure=true)
  public static <K, V> ImmutableMapBuilder<K, V> immutableMapBuilder() {
    return new ImmutableMapBuilder<>();
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

  @NotNull
  @Contract(pure=true)
  public static <U> Iterator<U> mapIterator(@NotNull TIntIterator iterator, @NotNull IntFunction<? extends U> mapper) {
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

  public static class ImmutableMapBuilder<K, V> {
    private final Map<K, V> myMap = new THashMap<>();

    @NotNull
    public ImmutableMapBuilder<K, V> put(K key, V value) {
      myMap.put(key, value);
      return this;
    }

    @Contract(pure=true)
    @NotNull
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

    // overridden for more efficient arraycopy vs. iterator() creation/traversing
    @NotNull
    @Override
    public <T> T[] toArray(@NotNull T[] a) {
      int size = size();
      T[] result = a.length >= size ? a : ArrayUtil.newArray(ArrayUtil.getComponentType(a), size);
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
    final Map<K, V> res = new HashMap<>(map1);
    for (Map.Entry<? extends K, ? extends V> entry : map2.entrySet()) {
      K key = entry.getKey();
      V v2 = entry.getValue();
      V v1 = map1.get(key);
      if (!Objects.equals(v1, v2)) {
        res.remove(key);
      }
    }
    return res;
  }

  @NotNull
  @Contract(pure=true)
  public static <K, V> Map<K,Couple<V>> diff(@NotNull Map<? extends K, ? extends V> map1, @NotNull Map<? extends K, ? extends V> map2) {
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

  @NotNull
  @Contract(pure=true)
  public static <T> List<T> mergeSortedLists(@NotNull List<? extends T> list1,
                                             @NotNull List<? extends T> list2,
                                             @NotNull Comparator<? super T> comparator,
                                             boolean mergeEqualItems) {
    final List<T> result = new ArrayList<>(list1.size() + list2.size());
    processSortedListsInOrder(list1, list2, comparator, mergeEqualItems, result::add);
    return result;
  }

  @NotNull
  @Contract(pure=true)
  public static <T> List<T> subList(@NotNull List<T> list, int from) {
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

  @NotNull
  public static <T> List<T> collect(@NotNull Iterator<? extends T> iterator) {
    if (!iterator.hasNext()) return emptyList();
    List<T> list = new ArrayList<>();
    addAll(list, iterator);
    return list;
  }

  @NotNull
  @Contract(pure = true)
  public static <K, V> Map<K, V> newMapFromKeys(@NotNull Iterator<? extends K> keys, @NotNull Convertor<? super K, ? extends V> valueConvertor) {
    Map<K, V> map = new HashMap<>();
    while (keys.hasNext()) {
      K key = keys.next();
      map.put(key, valueConvertor.convert(key));
    }
    return map;
  }

  @NotNull
  @Contract(pure = true)
  public static <K, V> Map<K, V> newMapFromValues(@NotNull Iterator<? extends V> values, @NotNull Convertor<? super V, ? extends K> keyConvertor) {
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

  @NotNull
  @Contract(pure = true)
  public static <K, V> Map<K, Set<V>> classify(@NotNull Iterator<? extends V> iterator, @NotNull Convertor<? super V, ? extends K> keyConvertor) {
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
  @NotNull
  @Contract(pure=true)
  public static <T> Iterator<T> emptyIterator() {
    return Collections.emptyIterator();
  }

  @NotNull
  @Contract(pure=true)
  public static <T> Iterable<T> emptyIterable() {
    return EmptyIterable.getInstance();
  }

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

  @Contract(pure=true)
  public static <T> T find(@NotNull Iterable<? extends T> iterable, @NotNull Condition<? super T> condition) {
    return find(iterable.iterator(), condition);
  }

  @Contract(pure=true)
  public static <T> T find(@NotNull Iterable<? extends T> iterable, @NotNull final T equalTo) {
    return find(iterable, (Condition<T>)object -> equalTo == object || equalTo.equals(object));
  }

  @Contract(pure=true)
  public static <T> T find(@NotNull Iterator<? extends T> iterator, @NotNull final T equalTo) {
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

  @NotNull
  @Contract(pure=true)
  public static <T, K, V> Map<K, V> map2Map(@NotNull T[] collection, @NotNull Function<? super T, ? extends Pair<K, V>> mapper) {
    return map2Map(Arrays.asList(collection), mapper);
  }

  @NotNull
  @Contract(pure=true)
  public static <T, K, V> Map<K, V> map2Map(@NotNull Collection<? extends T> collection,
                                            @NotNull Function<? super T, ? extends Pair<K, V>> mapper) {
    final Map<K, V> set = new THashMap<>(collection.size());
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
    final Map<K, V> set = new THashMap<>(collection.size());
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
    final Map<K, V> result = new THashMap<>(collection.size());
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
  public static <T, V> V[] map2Array(@NotNull T[] array, @NotNull Class<V> aClass, @NotNull Function<? super T, ? extends V> mapper) {
    V[] result = ArrayUtil.newArray(aClass, array.length);
    for (int i = 0; i < array.length; i++) {
      result[i] = mapper.fun(array[i]);
    }
    return result;
  }

  @NotNull
  @Contract(pure=true)
  public static <T, V> V[] map2Array(@NotNull Collection<? extends T> collection, @NotNull Class<V> aClass, @NotNull Function<? super T, ? extends V> mapper) {
    V[] result = ArrayUtil.newArray(aClass, collection.size());
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
    return result.isEmpty() ? ArrayUtilRt.EMPTY_INT_ARRAY : result.toNativeArray();
  }

  @NotNull
  @Contract(pure=true)
  public static <T> List<T> findAll(@NotNull T[] collection, @NotNull Condition<? super T> condition) {
    final List<T> result = new SmartList<>();
    for (T t : collection) {
      if (condition.value(t)) {
        result.add(t);
      }
    }
    return result;
  }

  @NotNull
  @Contract(pure = true)
  public static <T> List<T> filterIsInstance(@NotNull Collection<?> collection, @NotNull final Class<? extends T> aClass) {
    //noinspection unchecked
    return filter((Collection<T>)collection, Conditions.instanceOf(aClass));
  }

  @NotNull
  @Contract(pure=true)
  public static <T> List<T> filter(@NotNull Collection<? extends T> collection, @NotNull Condition<? super T> condition) {
    return findAll(collection, condition);
  }

  @NotNull
  @Contract(pure = true)
  public static <K, V> Map<K, V> filter(@NotNull Map<? extends K, ? extends V> map, @NotNull Condition<? super K> keyFilter) {
    Map<K, V> result = new HashMap<>();
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
    final List<T> result = new SmartList<>();
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
    return findAll(collection, Conditions.notNull());
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
    V[] array = ArrayUtil.newArray(instanceOf, list.size());
    return list.toArray(array);
  }

  @NotNull
  @Contract(pure=true)
  public static <T, V> V[] findAllAsArray(@NotNull Collection<? extends T> collection, @NotNull Class<V> instanceOf) {
    List<V> list = findAll(collection, instanceOf);
    V[] array = ArrayUtil.newArray(instanceOf, list.size());
    return list.toArray(array);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> T[] findAllAsArray(@NotNull T[] collection, @NotNull Condition<? super T> instanceOf) {
    List<T> list = findAll(collection, instanceOf);
    if (list.size() == collection.length) {
      return collection;
    }
    T[] array = ArrayUtil.newArray(ArrayUtil.getComponentType(collection), list.size());
    return list.toArray(array);
  }

  @NotNull
  @Contract(pure=true)
  public static <T, V> List<V> findAll(@NotNull Collection<? extends T> collection, @NotNull Class<V> instanceOf) {
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

  @NotNull
  @Contract(pure=true)
  public static Map<String, String> stringMap(@NotNull final String... keyValues) {
    final Map<String, String> result = new HashMap<>();
    for (int i = 0; i < keyValues.length - 1; i+=2) {
      result.put(keyValues[i], keyValues[i+1]);
    }

    return result;
  }

  @NotNull
  @Contract(pure=true)
  public static <T> Iterator<T> iterate(@NotNull T[] array) {
    return array.length == 0 ? Collections.emptyIterator() : Arrays.asList(array).iterator();
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

  @NotNull
  @Contract(pure=true)
  public static <T> Iterable<T> iterateBackward(@NotNull final List<? extends T> list) {
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

  @NotNull
  @Contract(pure=true)
  public static <T, E> Iterable<Pair<T, E>> zip(@NotNull final Iterable<? extends T> iterable1, @NotNull final Iterable<? extends E> iterable2) {
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

  @NotNull
  public static <T> List<T> collect(@NotNull Iterator<?> iterator, @NotNull FilteringIterator.InstanceOf<T> instanceOf) {
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
  @NotNull
  public static <T, C extends Collection<? super T>> C addAll(@NotNull C collection, @NotNull T... elements) {
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
  @NotNull
  public static <T, C extends Collection<T>> C addAllNotNull(@NotNull C collection, @NotNull T... elements) {
    for (T element : elements) {
      if (element != null) {
        collection.add(element);
      }
    }
    return collection;
  }

  @SafeVarargs
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
    List<T> result = new ArrayList<>(totalSize);
    for (final Collection<? extends T> ts : list) {
      result.addAll(ts);
    }
    return result.isEmpty() ? Collections.emptyList() : result;
  }

  @SafeVarargs
  @NotNull
  @Contract(pure=true)
  public static <T> List<T> append(@NotNull List<? extends T> list, @NotNull T... values) {
    return concat(list, Arrays.asList(values));
  }

  /**
   * prepend values in front of the list
   * @return read-only list consisting of values and the elements from specified list
   */
  @SafeVarargs
  @NotNull
  @Contract(pure=true)
  public static <T> List<T> prepend(@NotNull List<? extends T> list, @NotNull T... values) {
    return concat(Arrays.asList(values), list);
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

  @NotNull
  @Contract(pure=true)
  public static <T> Iterable<T> concat(@NotNull Iterable<? extends T> it1, @NotNull Iterable<? extends T> it2) {
    return new Iterable<T>() {
      @Override
      public void forEach(java.util.function.Consumer<? super T> action) {
        it1.forEach(action);
        it2.forEach(action);
      }

      @NotNull
      @Override
      public Iterator<T> iterator() {
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
  @NotNull
  @Contract(pure=true)
  public static <T> Iterable<T> concat(@NotNull final Iterable<? extends T>... iterables) {
    if (iterables.length == 0) return emptyIterable();
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
  @NotNull
  @Contract(pure=true)
  public static <T> Iterator<T> concatIterators(@NotNull Iterator<? extends T>... iterators) {
    return new SequenceIterator<>(iterators);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> Iterator<T> concatIterators(@NotNull Collection<? extends Iterator<? extends T>> iterators) {
    return new SequenceIterator<>(iterators);
  }

  @SafeVarargs
  @NotNull
  @Contract(pure=true)
  public static <T> Iterable<T> concat(@NotNull final T[]... iterables) {
    return () -> {
      //noinspection unchecked
      Iterator<T>[] iterators = new Iterator[iterables.length];
      for (int i = 0; i < iterables.length; i++) {
        T[] iterable = iterables[i];
        iterators[i] = iterate(iterable);
      }
      return concatIterators(iterators);
    };
  }

  /**
   * @return read-only list consisting of the lists added together
   */
  @NotNull
  @SafeVarargs
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
    //noinspection unchecked
    return concat(lists.toArray(new List[0]));
  }

  /**
   * @return read-only list consisting of the lists (made by listGenerator) added together
   */
  @NotNull
  @Contract(pure=true)
  public static <T, V> List<V> concat(@NotNull Iterable<? extends T> list, @NotNull Function<? super T, ? extends Collection<? extends V>> listGenerator) {
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
  @NotNull
  @Contract(pure=true)
  public static <T> Collection<T> intersection(@NotNull Collection<? extends T> collection1, @NotNull Collection<? extends T> collection2) {
    List<T> result = new ArrayList<>();
    for (T t : collection1) {
      if (collection2.contains(t)) {
        result.add(t);
      }
    }
    return result.isEmpty() ? emptyList() : result;
  }

  @NotNull
  @Contract(pure=true)
  public static <E extends Enum<E>> EnumSet<E> intersection(@NotNull EnumSet<E> collection1, @NotNull EnumSet<E> collection2) {
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
  @NotNull
  @Contract(pure=true)
  public static <T> Collection<T> subtract(@NotNull Collection<? extends T> from, @NotNull Collection<? extends T> what) {
    final Set<T> set = new HashSet<>(from);
    set.removeAll(what);
    return set.isEmpty() ? emptyList() : set;
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

  public static <T extends Comparable<? super T>> void sort(@NotNull T[] a) {
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
    return sorted(list, Comparator.naturalOrder());
  }

  /**
   * @apiNote this sort implementation is NOT stable for element.length < INSERTION_SORT_THRESHOLD
   */
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
  @NotNull
  @Contract(pure=true)
  public static <T,V> List<V> map(@NotNull Collection<? extends T> collection, @NotNull Function<? super T, ? extends V> mapping) {
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
  @NotNull
  @Contract(pure=true)
  public static <T, V> List<V> mapNotNull(@NotNull Iterable<? extends T> iterable, @NotNull Function<? super T, ? extends V> mapping) {
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
  @NotNull
  @Contract(pure=true)
  public static <T, V> List<V> mapNotNull(@NotNull Collection<? extends T> collection, @NotNull Function<? super T, ? extends V> mapping) {
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
  @NotNull
  @Contract(pure=true)
  public static <T> List<T> packNullables(@NotNull T... elements) {
    List<T> list = new ArrayList<>();
    for (T element : elements) {
      addIfNotNull(list, element);
    }
    return list.isEmpty() ? emptyList() : list;
  }

  /**
   * @return read-only list consisting of the elements from the array converted by mapping
   */
  @NotNull
  @Contract(pure=true)
  public static <T, V> List<V> map(@NotNull T[] array, @NotNull Function<? super T, ? extends V> mapping) {
    List<V> result = new ArrayList<>(array.length);
    for (T t : array) {
      result.add(mapping.fun(t));
    }
    return result.isEmpty() ? emptyList() : result;
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

  @SafeVarargs
  @NotNull
  @Contract(pure=true)
  public static <T> Set<T> set(@NotNull T ... items) {
    return new HashSet<>(Arrays.asList(items));
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
      Disposer.register(parentDisposable, () -> result.remove(element));
    }
  }

  @NotNull
  @Contract(pure=true)
  public static <T> List<T> createMaybeSingletonList(@Nullable T element) {
    return element == null ? emptyList() : Collections.singletonList(element);
  }

  @NotNull
  @Contract(pure=true)
  public static <T> Set<T> createMaybeSingletonSet(@Nullable T element) {
    return element == null ? Collections.emptySet() : Collections.singleton(element);
  }

  @NotNull
  public static <T, V> V getOrCreate(@NotNull Map<T, V> result, final T key, @NotNull V defaultValue) {
    return result.computeIfAbsent(key, __ -> defaultValue);
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

    List<T> list = new ArrayList<>();
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

  /**
   * @deprecated Use {@link Arrays#asList(Object[])}
   */
  @NotNull
  @SafeVarargs
  @Contract(pure=true)
  @Deprecated
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
    return strategy == TObjectHashingStrategy.CANONICAL ? new SingletonSet<>(o) : SingletonSet.withCustomStrategy(o, strategy);
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
  @NotNull
  @Contract(pure=true)
  public static <E> List<E> flatten(@NotNull Iterable<? extends Collection<? extends E>> collections) {
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
  @NotNull
  @Contract(pure=true)
  public static <E> List<E> flattenIterables(@NotNull Iterable<? extends Iterable<E>> collections) {
    int totalSize = 0;
    for (Iterable<E> list : collections) {
      totalSize += list instanceof Collection ? ((Collection<?>)list).size() : 10;
    }
    List<E> result = new ArrayList<>(totalSize);
    for (Iterable<E> list : collections) {
      for (E e : list) {
        result.add(e);
      }
    }
    return result.isEmpty() ? emptyList() : result;
  }

  @NotNull
  public static <K,V> V[] convert(@NotNull K[] from, @NotNull V[] to, @NotNull Function<? super K, ? extends V> fun) {
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
  public static <T, U extends T> U findLastInstance(@NotNull List<? extends T> list, @NotNull final Class<? extends U> clazz) {
    int i = lastIndexOf(list, (Condition<T>)clazz::isInstance);
    //noinspection unchecked
    return i < 0 ? null : (U)list.get(i);
  }

  @Contract(pure = true)
  public static <T, U extends T> int lastIndexOfInstance(@NotNull List<? extends T> list, @NotNull final Class<U> clazz) {
    return lastIndexOf(list, (Condition<T>)clazz::isInstance);
  }

  @NotNull
  @Contract(pure=true)
  public static <A,B> Map<B,A> reverseMap(@NotNull Map<A,B> map) {
    final Map<B,A> result = new HashMap<>();
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
      ((ArrayList<?>)list).trimToSize();
    }

    return list;
  }

  /**
   * @deprecated Use {@link Stack#Stack()}
   */
  @NotNull
  @Contract(value = " -> new", pure = true)
  @Deprecated
  public static <T> Stack<T> newStack() {
    return new Stack<>();
  }

  @NotNull
  @Contract(pure=true)
  public static <T> List<T> emptyList() {
    //noinspection deprecation
    return ContainerUtilRt.emptyList();
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <T> CopyOnWriteArrayList<T> createEmptyCOWList() {
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
  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <T> List<T> createLockFreeCopyOnWriteList() {
    return createConcurrentList();
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> List<T> createLockFreeCopyOnWriteList(@NotNull Collection<? extends T> c) {
    return new LockFreeCopyOnWriteArrayList<>(c);
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <V> ConcurrentIntObjectMap<V> createConcurrentIntObjectMap() {
    return new ConcurrentIntObjectHashMap<>();
  }

  @NotNull
  @Contract(value = "_,_,_ -> new", pure = true)
  public static <V> ConcurrentIntObjectMap<V> createConcurrentIntObjectMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
    return new ConcurrentIntObjectHashMap<>(initialCapacity, loadFactor, concurrencyLevel);
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <V> ConcurrentIntObjectMap<V> createConcurrentIntObjectSoftValueMap() {
    return new ConcurrentIntKeySoftValueHashMap<>();
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <V> ConcurrentLongObjectMap<V> createConcurrentLongObjectMap() {
    return new ConcurrentLongObjectHashMap<>();
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <V> ConcurrentLongObjectMap<V> createConcurrentLongObjectMap(int initialCapacity) {
    return new ConcurrentLongObjectHashMap<>(initialCapacity);
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <K,V> ConcurrentMap<K,V> createConcurrentWeakValueMap() {
    return new ConcurrentWeakValueHashMap<>();
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <V> ConcurrentIntObjectMap<V> createConcurrentIntObjectWeakValueMap() {
    return new ConcurrentIntKeyWeakValueHashMap<>();
  }

  @NotNull
  @Contract(value = "_,_,_,_ -> new", pure = true)
  public static <K,V> ConcurrentMap<K,V> createConcurrentWeakKeySoftValueMap(int initialCapacity,
                                                                             float loadFactor,
                                                                             int concurrencyLevel,
                                                                             @NotNull final TObjectHashingStrategy<? super K> hashingStrategy) {
    //noinspection deprecation
    return new ConcurrentWeakKeySoftValueHashMap<>(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <K,V> ConcurrentMap<K,V> createConcurrentSoftKeySoftValueMap() {
    return createConcurrentSoftKeySoftValueMap(100, 0.75f, Runtime.getRuntime().availableProcessors(), canonicalStrategy());
  }

  @NotNull
  @Contract(value = "_,_,_,_ -> new", pure = true)
  public static <K,V> ConcurrentMap<K,V> createConcurrentSoftKeySoftValueMap(int initialCapacity,
                                                                             float loadFactor,
                                                                             int concurrencyLevel,
                                                                             @NotNull final TObjectHashingStrategy<? super K> hashingStrategy) {
    return new ConcurrentSoftKeySoftValueHashMap<>(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <K,V> ConcurrentMap<K,V> createConcurrentWeakKeySoftValueMap() {
    return createConcurrentWeakKeySoftValueMap(100, 0.75f, Runtime.getRuntime().availableProcessors(), canonicalStrategy());
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <K,V> ConcurrentMap<K,V> createConcurrentWeakKeyWeakValueMap() {
    return createConcurrentWeakKeyWeakValueMap(canonicalStrategy());
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <K,V> ConcurrentMap<K,V> createConcurrentWeakKeyWeakValueMap(@NotNull TObjectHashingStrategy<? super K> strategy) {
    return new ConcurrentWeakKeyWeakValueHashMap<>(100, 0.75f, Runtime.getRuntime().availableProcessors(),
                                                   strategy);
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <K, V> ConcurrentMap<K,V> createConcurrentSoftValueMap() {
    return new ConcurrentSoftValueHashMap<>();
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <K,V> ConcurrentMap<K,V> createConcurrentSoftMap() {
    return new ConcurrentSoftHashMap<>();
  }

  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <K,V> ConcurrentMap<K,V> createConcurrentWeakMap() {
    return new ConcurrentWeakHashMap<>(0.75f);
  }

  @NotNull
  @Contract(value = "_,_,_,_ -> new", pure = true)
  public static <K,V> ConcurrentMap<K,V> createConcurrentSoftMap(int initialCapacity,
                                                                 float loadFactor,
                                                                 int concurrencyLevel,
                                                                 @NotNull TObjectHashingStrategy<? super K> hashingStrategy) {
    return new ConcurrentSoftHashMap<>(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
  }

  @NotNull
  @Contract(value = "_,_,_,_ -> new", pure = true)
  public static <K,V> ConcurrentMap<K,V> createConcurrentWeakMap(int initialCapacity,
                                                                 float loadFactor,
                                                                 int concurrencyLevel,
                                                                 @NotNull TObjectHashingStrategy<? super K> hashingStrategy) {
    return new ConcurrentWeakHashMap<>(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <K,V> ConcurrentMap<K,V> createConcurrentWeakMap(@NotNull TObjectHashingStrategy<? super K> hashingStrategy) {
    return new ConcurrentWeakHashMap<>(hashingStrategy);
  }

  /**
   * @see #createLockFreeCopyOnWriteList()
   */
  @NotNull
  @Contract(value = " -> new", pure = true)
  public static <T> ConcurrentList<T> createConcurrentList() {
    return new LockFreeCopyOnWriteArrayList<>();
  }

  @NotNull
  @Contract(value = "_ -> new", pure = true)
  public static <T> ConcurrentList<T> createConcurrentList(@NotNull Collection <? extends T> collection) {
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

  @NotNull
  @Contract(pure=true)
  public static <T, V> List<V> map2List(@NotNull T[] array, @NotNull Function<? super T, ? extends V> mapper) {
    return map2List(Arrays.asList(array), mapper);
  }

  @NotNull
  @Contract(pure=true)
  public static <T, V> List<V> map2List(@NotNull Collection<? extends T> collection, @NotNull Function<? super T, ? extends V> mapper) {
    if (collection.isEmpty()) return emptyList();
    List<V> list = new ArrayList<>(collection.size());
    for (final T t : collection) {
      list.add(mapper.fun(t));
    }
    return list;
  }

  @NotNull
  @Contract(pure=true)
  public static <K, V> List<Pair<K, V>> map2List(@NotNull Map<? extends K, ? extends V> map) {
    if (map.isEmpty()) return emptyList();
    final List<Pair<K, V>> result = new ArrayList<>(map.size());
    for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
      result.add(Pair.create(entry.getKey(), entry.getValue()));
    }
    return result;
  }

  @NotNull
  @Contract(pure=true)
  public static <T, V> Set<V> map2Set(@NotNull T[] collection, @NotNull Function<? super T, ? extends V> mapper) {
    return map2Set(Arrays.asList(collection), mapper);
  }

  @NotNull
  @Contract(pure=true)
  public static <T, V> Set<V> map2Set(@NotNull Collection<? extends T> collection, @NotNull Function<? super T, ? extends V> mapper) {
    if (collection.isEmpty()) return Collections.emptySet();
    Set <V> set = new HashSet<>(collection.size());
    for (final T t : collection) {
      set.add(mapper.fun(t));
    }
    return set;
  }

  @NotNull
  @Contract(pure=true)
  public static <T, V> Set<V> map2LinkedSet(@NotNull Collection<? extends T> collection, @NotNull Function<? super T, ? extends V> mapper) {
    if (collection.isEmpty()) return Collections.emptySet();
    Set <V> set = new LinkedHashSet<>(collection.size());
    for (final T t : collection) {
      set.add(mapper.fun(t));
    }
    return set;
  }

  @NotNull
  @Contract(pure=true)
  public static <T, V> Set<V> map2SetNotNull(@NotNull Collection<? extends T> collection, @NotNull Function<? super T, ? extends V> mapper) {
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

  @NotNull
  public static <T> List<T> copyList(@NotNull List<? extends T> list) {
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

  @NotNull
  @Contract(pure=true)
  public static <T> List<T> notNullize(@Nullable List<T> list) {
    return list == null ? emptyList() : list;
  }

  @NotNull
  @Contract(pure=true)
  public static <T> Set<T> notNullize(@Nullable Set<T> set) {
    return set == null ? Collections.emptySet() : set;
  }

  @NotNull
  @Contract(pure = true)
  public static <K, V> Map<K, V> notNullize(@Nullable Map<K, V> map) {
    return map == null ? Collections.emptyMap() : map;
  }

  @Contract(pure = true)
  public static <T> boolean startsWith(@NotNull List<? extends T> list, @NotNull List<? extends T> prefix) {
    return list.size() >= prefix.size() && list.subList(0, prefix.size()).equals(prefix);
  }

  @Nullable
  @Contract(pure=true)
  public static <T, C extends Collection<? extends T>> C nullize(@Nullable C collection) {
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

  public static class KeyOrderedMultiMap<K extends Comparable<? super K>, V> extends MultiMap<K, V> {
    public KeyOrderedMultiMap() {
    }

    public KeyOrderedMultiMap(@NotNull MultiMap<? extends K, ? extends V> toCopy) {
      super(toCopy);
    }

    @NotNull
    @Override
    protected Map<K, Collection<V>> createMap() {
      return new TreeMap<>();
    }

    @NotNull
    @Override
    protected Map<K, Collection<V>> createMap(int initialCapacity, float loadFactor) {
      return new TreeMap<>();
    }

    @NotNull
    public NavigableSet<K> navigableKeySet() {
      return ((TreeMap<K, Collection<V>>)myMap).navigableKeySet();
    }
  }

  @Contract(value = " -> new", pure = true)
  @NotNull
  public static <K,V> Map<K,V> createWeakKeySoftValueMap() {
    return new WeakKeySoftValueHashMap<>();
  }

  @Contract(value = " -> new", pure = true)
  @NotNull
  public static <K,V> Map<K,V> createWeakKeyWeakValueMap() {
    return new WeakKeyWeakValueHashMap<>();
  }

  @Contract(value = " -> new", pure = true)
  @NotNull
  public static <K,V> Map<K,V> createSoftKeySoftValueMap() {
    return new SoftKeySoftValueHashMap<>();
  }

  /**
   * Hard keys soft values hash map.
   * Null keys are NOT allowed
   * Null values are allowed
   */
  @Contract(value = " -> new", pure = true)
  @NotNull
  public static <K,V> Map<K,V> createSoftValueMap() {
    return new SoftValueHashMap<>(canonicalStrategy());
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
    return new WeakValueHashMap<>(canonicalStrategy());
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
    return new SoftHashMap<>(4);
  }

  @Contract(value = "_ -> new", pure = true)
  @NotNull
  public static <K,V> Map<K,V> createSoftMap(@NotNull TObjectHashingStrategy<? super K> strategy) {
    //noinspection deprecation
    return new SoftHashMap<>(strategy);
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
    return createWeakMap(initialCapacity, 0.8f, canonicalStrategy());
  }

  @Contract(value = "_, _, _ -> new", pure = true)
  @NotNull
  public static <K,V> Map<K,V> createWeakMap(int initialCapacity, float loadFactor, @NotNull TObjectHashingStrategy<? super K> strategy) {
    //noinspection deprecation
    return new WeakHashMap<>(initialCapacity, loadFactor, strategy);
  }

  @Contract(value = " -> new", pure = true)
  @NotNull
  public static <T> Set<T> createWeakSet() {
    return new WeakHashSet<>();
  }

  @Contract(value = " -> new", pure = true)
  @NotNull
  public static <T> IntObjectMap<T> createIntKeyWeakValueMap() {
    return new IntKeyWeakValueHashMap<>();
  }

  @Contract(value = " -> new", pure = true)
  @NotNull
  public static <T> ObjectIntMap<T> createWeakKeyIntValueMap() {
    return new WeakKeyIntValueHashMap<>();
  }

  /**
   * Create an immutable copy of the {@code list}.
   * Modifications of the {@code list} have no effect on the returned copy.
   */
  @SuppressWarnings("unchecked")
  @Contract(value = "_ -> new", pure = true)
  @NotNull
  public static <T> List<T> freeze(@NotNull List<? extends T> list) {
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
