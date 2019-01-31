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
}
