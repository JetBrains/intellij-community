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

import com.intellij.openapi.util.Condition;

import java.util.Collection;
import java.util.Map;

public interface InternalIterator<T>{
  /**
   * @return false to stop iteration true to continue if more elements are avaliable.
   */
  boolean visit(T element);

  class Collector<T> implements InternalIterator<T> {
    private final Collection<? super T> myCollection;

    public Collector(Collection<? super T> collection) {
      myCollection = collection;
    }

    @Override
    public boolean visit(T value) {
      return myCollection.add(value);
    }

    public static <T> InternalIterator<T> create(Collection<? super T> collection) {
      return new Collector<T>(collection);
    }
  }

  class MapFromValues<K, Dom, V extends Dom> implements InternalIterator<V> {
    private final Map<? super K, ? super V> myMap;
    private final Convertor<? super Dom, ? extends K> myToKeyConvertor;

    public MapFromValues(Map<? super K, ? super V> map, Convertor<? super Dom, ? extends K> toKeyConvertor) {
      myMap = map;
      myToKeyConvertor = toKeyConvertor;
    }

    @Override
    public boolean visit(V value) {
      myMap.put(myToKeyConvertor.convert(value), value);
      return true;
    }

    public static <Dom, K, V extends Dom> InternalIterator<V> create(Convertor<? super Dom, ? extends K> toKey, Map<? super K, ? super V> map) {
      return new MapFromValues<K, Dom, V>(map, toKey);
    }
  }

  class Filtering<T> implements InternalIterator<T> {
    private final Condition<? super T> myFilter;
    private final InternalIterator<? super T> myIterator;

    public Filtering(InternalIterator<? super T> iterator, Condition<? super T> filter) {
      myIterator = iterator;
      myFilter = filter;
    }

    @Override
    public boolean visit(T value) {
      return !myFilter.value(value) || myIterator.visit(value);
    }

    public static <T> InternalIterator<T> create(InternalIterator<? super T> iterator, Condition<? super T> filter) {
      return new Filtering<T>(iterator, filter);
    }

    public static <T, V extends T> InternalIterator<T> createInstanceOf(InternalIterator<V> iterator, FilteringIterator.InstanceOf<V> filter) {
      return new Filtering<T>((InternalIterator<T>)iterator, filter);
    }

    public static <T> InternalIterator createInstanceOf(InternalIterator<T> iterator, Class<T> aClass) {
      return createInstanceOf(iterator, FilteringIterator.instanceOf(aClass));
    }
  }

  class Converting<Dom, Rng> implements InternalIterator<Dom> {
    private final Convertor<? super Dom, ? extends Rng> myConvertor;
    private final InternalIterator<? super Rng> myIterator;

    public Converting(InternalIterator<? super Rng> iterator, Convertor<? super Dom, ? extends Rng> convertor) {
      myIterator = iterator;
      myConvertor = convertor;
    }

    @Override
    public boolean visit(Dom element) {
      return myIterator.visit(myConvertor.convert(element));
    }

    public static <Dom, Rng> InternalIterator<Dom> create(Convertor<? super Dom, ? extends Rng> convertor, InternalIterator<? super Rng> iterator) {
      return new Converting<Dom, Rng>(iterator, convertor);
    }
  }
}
