/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.util.*;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
public class ContainerUtil {
  private static final int INSERTION_SORT_THRESHOLD = 10;

  public static <T> List<T> mergeSortedLists(List<T> list1, List<T> list2, Comparator<? super T> comparator, boolean mergeEqualItems){
    List<T> result = new ArrayList<T>(list1.size() + list2.size());

    int index1 = 0;
    int index2 = 0;
    while (index1 < list1.size() || index2 < list2.size()) {
      if (index1 >= list1.size()) {
        result.add(list2.get(index2++));
      }
      else if (index2 >= list2.size()) {
        result.add(list1.get(index1++));
      }
      else {
        T element1 = list1.get(index1);
        T element2 = list2.get(index2);
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
  public static <T> List<T> mergeSortedArrays(T[] list1, T[] list2, Comparator<? super T> comparator, boolean mergeEqualItems, @Nullable Processor<? super T> filter){
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

  public static <T> void addAll(Collection<T> collection, Iterator<T> iterator) {
    while (iterator.hasNext()) {
      T o = iterator.next();
      collection.add(o);
    }
  }

  public static <T> ArrayList<T> collect(Iterator<T> iterator) {
    ArrayList<T> list = new ArrayList<T>();
    addAll(list, iterator);
    return list;
  }

  public static <T> HashSet<T> collectSet(Iterator<T> iterator) {
    HashSet<T> hashSet = new HashSet<T>();
    addAll(hashSet, iterator);
    return hashSet;
  }

  public static <K, V> HashMap<K, V> assignKeys(Iterator<V> iterator, Convertor<V, K> keyConvertor) {
    HashMap<K, V> hashMap = new HashMap<K, V>();
    while (iterator.hasNext()) {
      V value = iterator.next();
      hashMap.put(keyConvertor.convert(value), value);
    }
    return hashMap;
  }

  public static <K, V> HashMap<K, Set<V>> classify(Iterator<V> iterator, Convertor<V, K> keyConvertor) {
    HashMap<K, Set<V>> hashMap = new HashMap<K, Set<V>>();
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

  public static <K, V> HashMap<K, V> assignValues(Iterator<K> iterator, Convertor<K, V> valueConvertor) {
    HashMap<K, V> hashMap = new HashMap<K, V>();
    while (iterator.hasNext()) {
      K key = iterator.next();
      hashMap.put(key, valueConvertor.convert(key));
    }
    return hashMap;
  }

  public static <T> Iterator<T> emptyIterator() {
    return EmptyIterator.getInstance();
  }

  public static <T> Iterable<T> emptyIterable() {
    return EmptyIterable.getInstance();
  }

  @Nullable
  public static <T> T find(T[] array, Condition<T> condition) {
    for (T element : array) {
      if (condition.value(element)) return element;
    }
    return null;
  }

  public static <T> boolean process(Iterable<? extends T> iterable, Processor<T> processor) {
    for (final T t : iterable) {
      if (!processor.process(t)) {
        return false;
      }
    }
    return true;
  }

  public static <T> boolean process(T[] iterable, Processor<? super T> processor) {
    for (final T t : iterable) {
      if (!processor.process(t)) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  public static <T, V extends T> V find(Iterable<V> iterable, Condition<T> condition) {
    return find(iterable.iterator(), condition);
  }

  @Nullable
  public static <T> T find(Iterable<? extends T> iterable, final T equalTo) {
    return find(iterable, new Condition<T>() {
      public boolean value(final T object) {
        return equalTo == object || equalTo.equals(object);
      }
    });
  }

  @Nullable
  public static <T, V extends T> V find(Iterator<V> iterator, Condition<T> condition) {
    while (iterator.hasNext()) {
      V value = iterator.next();
      if (condition.value(value)) return value;
    }
    return null;
  }

  public static <T, V> List<V> map2List(T[] array, Function<T, V> mapper) {
    return map2List(Arrays.asList(array), mapper);
  }

  public static <T, V> List<V> map2List(Collection<? extends T> collection, Function<T, V> mapper) {
    final ArrayList<V> list = new ArrayList<V>(collection.size());
    for (final T t : collection) {
      list.add(mapper.fun(t));
    }
    return list;
  }

  public static <T, V> Set<V> map2Set(T[] collection, Function<T, V> mapper) {
    return map2Set(Arrays.asList(collection), mapper);
  }

  public static <T, V> Set<V> map2Set(Collection<? extends T> collection, Function<T, V> mapper) {
    final HashSet<V> set = new HashSet<V>(collection.size());
    for (final T t : collection) {
      set.add(mapper.fun(t));
    }
    return set;
  }

  public static <T> Object[] map2Array(T[] array, Function<T, Object> mapper) {
    return map2Array(array, Object.class, mapper);
  }

  public static <T> Object[] map2Array(Collection<T> array, Function<T, Object> mapper) {
    return map2Array(array, Object.class, mapper);
  }

  public static <T, V> V[] map2Array(T[] array, Class<? extends V> aClass, Function<T, V> mapper) {
    return map2Array(Arrays.asList(array), aClass, mapper);
  }

  public static <T, V> V[] map2Array(Collection<? extends T> collection, Class<? extends V> aClass, Function<T, V> mapper) {
    final List<V> list = map2List(collection, mapper);
    return list.toArray((V[])Array.newInstance(aClass, list.size()));
  }

  public static <T, V> V[] map2Array(Collection<? extends T> collection, V[] to, Function<T, V> mapper) {
    return map2List(collection, mapper).toArray(to);
  }

  public static <T> List<T> findAll(T[] collection, Condition<? super T> condition) {
    return findAll(Arrays.asList(collection), condition);
  }

  public static <T> List<T> findAll(Collection<? extends T> collection, Condition<? super T> condition) {
    final ArrayList<T> result = new ArrayList<T>();
    for (final T t : collection) {
      if (condition.value(t)) {
        result.add(t);
      }
    }
    return result;
  }

  public static <T> List<T> skipNulls(Collection<? extends T> collection) {
    return findAll(collection, Condition.NOT_NULL);
  }

  public static <T, V> List<V> findAll(T[] collection, Class<V> instanceOf) {
    return findAll(Arrays.asList(collection), instanceOf);
  }

  public static <T, V> V[] findAllAsArray(T[] collection, Class<V> instanceOf) {
    List<V> list = findAll(Arrays.asList(collection), instanceOf);
    return list.toArray((V[])Array.newInstance(instanceOf, list.size()));
  }

  public static <T, V> V[] findAllAsArray(Collection<? extends T> collection, Class<V> instanceOf) {
    List<V> list = findAll(collection, instanceOf);
    return list.toArray((V[])Array.newInstance(instanceOf, list.size()));
  }

  public static <T> T[] findAllAsArray(T[] collection, Condition<? super T> instanceOf) {
    List<T> list = findAll(collection, instanceOf);
    return list.toArray((T[])Array.newInstance(collection.getClass().getComponentType(), list.size()));
  }

  public static <T, V> List<V> findAll(Collection<? extends T> collection, Class<V> instanceOf) {
    final ArrayList<V> result = new ArrayList<V>();
    for (final T t : collection) {
      if (instanceOf.isInstance(t)) {
        result.add((V)t);
      }
    }
    return result;
  }

  public static <T> void removeDuplicates(Collection<T> collection) {
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

  public static <T> Iterator<T> iterate(T[] arrays) {
    return Arrays.asList(arrays).iterator();
  }

  public static <T> Iterator<T> iterate(final Enumeration<T> enumeration) {
    return new Iterator<T>() {
      public boolean hasNext() {
        return enumeration.hasMoreElements();
      }

      public T next() {
        return enumeration.nextElement();
      }

      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  public static <T> Iterable<T> iterate(T[] arrays, final Condition<? super T> condition) {
    return iterate(Arrays.asList(arrays), condition);
  }

  public static <T> Iterable<T> iterate(final Collection<? extends T> collection, final Condition<? super T> condition) {
    if (collection.isEmpty()) return emptyIterable();
    return new Iterable<T>() {
      public Iterator<T> iterator() {
        return new Iterator<T>() {
          Iterator<? extends T> impl = collection.iterator();
          T next = findNext();

          public boolean hasNext() {
            return next != null;
          }

          public T next() {
            T result = next;
            next = findNext();
            return result;
          }

          private T findNext() {
            while (impl.hasNext()) {
              T each = impl.next();
              if (condition.value(each)) {
                return each;
              }
            }
            return null;
          }

          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }

  public static <T> Iterable<T> iterateBackward(final List<? extends T> list) {
    return new Iterable<T>() {
      public Iterator<T> iterator() {
        return new Iterator<T>() {
          ListIterator<? extends T> it = list.listIterator(list.size());

          public boolean hasNext() {
            return it.hasPrevious();
          }

          public T next() {
            return it.previous();
          }

          public void remove() {
            it.remove();
          }
        };
      }
    };
  }

  public static <E> void swapElements(final List<E> list, final int index1, final int index2) {
    E e1 = list.get(index1);
    E e2 = list.get(index2);
    list.set(index1, e2);
    list.set(index2, e1);
  }

  public static <T> ArrayList<T> collect(Iterator<?> iterator, FilteringIterator.InstanceOf<T> instanceOf) {
    return collect(FilteringIterator.create(iterator, instanceOf));
  }

  public static <T> void addAll(Collection<T> collection, Enumeration<T> enumeration) {
    while (enumeration.hasMoreElements()) {
      T element = enumeration.nextElement();
      collection.add(element);
    }
  }

  public static <T, U extends T> U findInstance(Iterable<T> iterable, Class<U> aClass) {
    return findInstance(iterable.iterator(), aClass);
  }

  public static <T, U extends T> U findInstance(Iterator<T> iterator, Class<U> aClass) {
    // uncomment for 1.5
    //return (U)find(iterator, new FilteringIterator.InstanceOf<U>(aClass));
    return (U)find(iterator, new FilteringIterator.InstanceOf<T>((Class<T>)aClass));
  }

  @Nullable
  public static <T, U extends T> U findInstance(T[] array, Class<U> aClass) {
    return findInstance(Arrays.asList(array), aClass);
  }

  public static <T, V> List<T> concat(V[] array, Function<V, Collection<? extends T>> fun) {
    return concat(Arrays.asList(array), fun);
  }

  public static <T> List<T> concat(Iterable<? extends Collection<T>> list) {
    final ArrayList<T> result = new ArrayList<T>();
    for (final Collection<T> ts : list) {
      result.addAll(ts);
    }
    return result;
  }

  public static <T> List<T> concat(@NotNull final List<? extends T> list1, @NotNull final List<? extends T> list2) {
    return concat(new List[]{list1, list2});
  }

  public static <T> Iterable<T> concat(@NotNull final Iterable<? extends T>... iterables) {
    return new Iterable<T>() {
      public Iterator<T> iterator() {
        Iterator[] iterators = new Iterator[iterables.length];
        for (int i = 0, iterablesLength = iterables.length; i < iterablesLength; i++) {
          Iterable<? extends T> iterable = iterables[i];
          iterators[i] = iterable.iterator();
        }
        return new SequenceIterator<T>(iterators);
      }
    };
  }

  public static <T> List<T> concat(@NotNull final List<? extends T>... lists) {
    int size = 0;
    for (List<? extends T> each : lists) {
      size += each.size();
    }
    final int finalSize = size;
    return new AbstractList<T>() {
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

      public int size() {
        return finalSize;
      }
    };
  }

  public static <T, V> List<T> concat(Iterable<? extends V> list, Function<V, Collection<? extends T>> fun) {
    final ArrayList<T> result = new ArrayList<T>();
    for (final V v : list) {
      result.addAll(fun.fun(v));
    }
    return result;
  }

  public static <T> boolean intersects(Collection<? extends T> collection1, Collection<? extends T> collection2) {
    for (T t : collection1) {
      //noinspection SuspiciousMethodCalls
      if (collection2.contains(t)) {
        return true;
      }
    }
    return false;
  }

  public static <T> T getFirstItem(final Collection<T> items, final T def) {
    return items == null || items.isEmpty() ? def : items.iterator().next();
  }

  public static <T> Collection<T> subtract(Collection<T> from, Collection<T> what) {
    final HashSet<T> set = new HashSet<T>(from);
    set.removeAll(what);
    return set;
  }

  public static <T> T[] toArray(List<T> collection, T[] array) {
    final int length = array.length;
    if (length < 20) {
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
  public static <T> T[] toArray(Collection<T> c, T[] sample) {
    final int size = c.size();
    if (size == sample.length && size < 20) {
      int i = 0;
      for (T t : c) {
        sample[i++] = t;
      }
      return sample;
    }

    return c.toArray(sample);
  }

  public static <T extends Comparable<T>> void sort(List<T> list) {
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

  public static <T> void sort(List<T> list, Comparator<T> comparator) {
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

  public static <T extends Comparable<T>> void sort(T[] a) {
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

  public static <T> void sort(T[] a, Comparator<T> comparator) {
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

  public static <T,V> List<V> map(Iterable<? extends T> iterable, Function<T, V> mapping) {
    List<V> result = new ArrayList<V>();
    for (T t : iterable) {
      result.add(mapping.fun(t));
    }
    return result;
  }

  public static <T, V> List<V> mapNotNull(@NotNull T[] array, Function<T, V> mapping) {
    return mapNotNull(Arrays.asList(array), mapping);
  }

  public static <T, V> List<V> mapNotNull(Iterable<? extends T> iterable, Function<T, V> mapping) {
    List<V> result = new ArrayList<V>();
    for (T t : iterable) {
      final V o = mapping.fun(t);
      if (o != null) {
        result.add(o);
      }
    }
    return result;
  }

  public static <T> List<T> packNullables(T... elements) {
    ArrayList<T> list = new ArrayList<T>();
    for (T element : elements) {
      addIfNotNull(element, list);
    }
    return list;
  }

  public static <T, V> List<V> map(T[] arr, Function<T, V> mapping) {
    List<V> result = new ArrayList<V>();
    for (T t : arr) {
      result.add(mapping.fun(t));
    }
    return result;
  }

  public static <T, V> V[] map(T[] arr, Function<T, V> mapping, V[] emptyArray) {
    List<V> result = new ArrayList<V>();
    for (T t : arr) {
      result.add(mapping.fun(t));
    }
    return result.toArray(emptyArray);
  }

  public static <T> void addIfNotNull(final T element, final Collection<T> result) {
    if (element != null) {
      result.add(element);
    }
  }

  public static <K, V> void putIfNotNull(final K key, final V value, final Map<K, V> result) {
    if (value != null) {
      result.put(key, value);
    }
  }

  public static <T> void add(final T element, final Collection<T> result, final Disposable parentDisposable) {
    if (result.add(element)) {
      Disposer.register(parentDisposable, new Disposable() {
        public void dispose() {
          result.remove(element);
        }
      });
    }
  }

  public static <T> List<T> createMaybeSingletonList(@Nullable T element) {
    return element == null ? Collections.<T>emptyList() : Arrays.asList(element);
  }

  public static <T, V> V getOrCreate(final Map<T, V> result, final T key, final V defaultValue) {
    V value = result.get(key);
    if (value == null) {
      result.put(key, value = defaultValue);
    }
    return value;
  }

  public static <T, V> V getOrCreate(final Map<T, V> result, final T key, final Factory<V> factory) {
    V value = result.get(key);
    if (value == null) {
      result.put(key, value = factory.create());
    }
    return value;
  }

  public static <T> boolean and(T[] iterable, Condition<T> condition) {
    return and(Arrays.asList(iterable), condition);
  }

  public static <T> boolean and(Iterable<T> iterable, Condition<T> condition) {
    for (final T t : iterable) {
      if (!condition.value(t)) return false;
    }
    return true;
  }

  public static <T> boolean or(T[] iterable, Condition<T> condition) {
    return or(Arrays.asList(iterable), condition);
  }

  public static <T> boolean or(Iterable<T> iterable, Condition<T> condition) {
    for (final T t : iterable) {
      if (condition.value(t)) return true;
    }
    return false;
  }

  public static <T> List<T> unfold(@Nullable T t, @NotNull NullableFunction<T, T> next) {
    if (t == null) return Collections.emptyList();

    final ArrayList<T> list = new ArrayList<T>();
    while (t != null) {
      list.add(t);
      t = next.fun(t);
    }
    return list;
  }

  public static <T> List<T> dropTail(List<T> items) {
    return items.subList(0, items.size() - 1);
  }

  public static <T> List<T> list(T... items) {
    return Arrays.asList(items);
  }

  // Generalized Quick Sort. Does neither array.clone() nor list.toArray()

  public static <T> void quickSort(List<T> list, Comparator<? super T> comparator) {
    quickSort(list, comparator, 0, list.size());
  }

  private static <T> void quickSort(List<T> x, Comparator<? super T> comparator, int off, int len) {
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

  /**
   * Returns the index of the median of the three indexed longs.
   */
  private static <T> int med3(List<T> x, Comparator<? super T> comparator, int a, int b, int c) {
    return comparator.compare(x.get(a), x.get(b)) < 0 ? comparator.compare(x.get(b), x.get(c)) < 0
                                                        ? b
                                                        : comparator.compare(x.get(a), x.get(c)) < 0 ? c : a
                                                      : comparator.compare(x.get(c), x.get(b)) < 0
                                                        ? b
                                                        : comparator.compare(x.get(c), x.get(a)) < 0 ? c : a;
  }

  /**
   * Swaps x[a .. (a+n-1)] with x[b .. (b+n-1)].
   */
  private static <T> void vecswap(List<T> x, int a, int b, int n) {
    for (int i = 0; i < n; i++, a++, b++) {
      swapElements(x, a, b);
    }
  }

  public static <T> CopyOnWriteArrayList<T> createEmptyCOWList() {
    // does not create garbage new Object[0]
    return new CopyOnWriteArrayList<T>(ContainerUtil.<T>emptyList());
  }

  public static <T> List<T> emptyList() {
    return (List<T>)EmptyList.INSTANCE;
  }

  private static class EmptyList extends AbstractList<Object> implements RandomAccess, Serializable {
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
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public <T> T[] toArray(T[] a) {
      return a;
    }
  }

  @NotNull
  public static <T> Set<T> singleton(final T o, @NotNull final TObjectHashingStrategy<T> strategy) {
    return new Set<T>() {
      public int size() {
        return 1;
      }

      public boolean isEmpty() {
        return false;
      }

      public boolean contains(Object elem) {
        return strategy.equals(o, (T)elem);
      }

      public Iterator<T> iterator() {
        return new Iterator<T>() {
          boolean atEnd;

          public boolean hasNext() {
            return !atEnd;
          }

          public T next() {
            if (atEnd) throw new NoSuchElementException();
            atEnd = true;
            return o;
          }

          public void remove() {
            throw new IncorrectOperationException();
          }
        };
      }

      public Object[] toArray() {
        return new Object[]{o};
      }

      public <T> T[] toArray(T[] a) {
        assert a.length == 1;
        a[0] = (T)o;
        return a;
      }

      public boolean add(T t) {
        throw new IncorrectOperationException();
      }

      public boolean remove(Object o) {
        throw new IncorrectOperationException();
      }

      public boolean containsAll(Collection<?> c) {
        return false;
      }

      public boolean addAll(Collection<? extends T> c) {
        throw new IncorrectOperationException();
      }

      public boolean retainAll(Collection<?> c) {
        throw new IncorrectOperationException();
      }

      public boolean removeAll(Collection<?> c) {
        throw new IncorrectOperationException();
      }

      public void clear() {
        throw new IncorrectOperationException();
      }
    };
  }

  public static <E> List<E> flatten(Collection<? extends Collection<E>> collections) {
    List<E> result = new ArrayList<E>();
    for (Collection<E> list : collections) {
      result.addAll(list);
    }

    return result;
  }

}
