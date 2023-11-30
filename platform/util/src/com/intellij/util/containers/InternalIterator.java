// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.openapi.util.Condition;

import java.util.Collection;

@FunctionalInterface
public interface InternalIterator<T>{
  /**
   * @return false to stop iteration true to continue if more elements are avaliable.
   */
  boolean visit(T element);

  final class Collector<T> implements InternalIterator<T> {
    private final Collection<? super T> myCollection;

    public Collector(Collection<? super T> collection) {
      myCollection = collection;
    }

    @Override
    public boolean visit(T value) {
      return myCollection.add(value);
    }

    public static <T> InternalIterator<T> create(Collection<? super T> collection) {
      return new Collector<>(collection);
    }
  }

  final class Filtering<T> implements InternalIterator<T> {
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
      return new Filtering<>(iterator, filter);
    }

    public static <T, V extends T> InternalIterator<T> createInstanceOf(InternalIterator<V> iterator, FilteringIterator.InstanceOf<V> filter) {
      return new Filtering<>((InternalIterator<T>)iterator, filter);
    }

    public static <T> InternalIterator createInstanceOf(InternalIterator<T> iterator, Class<T> aClass) {
      return createInstanceOf(iterator, FilteringIterator.instanceOf(aClass));
    }
  }
}
