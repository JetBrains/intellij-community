// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * {@link #remove} throws {@link IllegalStateException} if called after {@link #hasNext}
 */
public final class FilteringIterator<Dom, E extends Dom> implements PeekableIterator<E> {
  private final Iterator<? extends Dom> delegate;
  private final Predicate<? super Dom> condition;
  private boolean isNextObtained;
  private boolean isCurrentIsValid;
  private Dom current;
  private Boolean currentPassedFilter;

  @Deprecated
  public FilteringIterator(@NotNull Iterator<? extends Dom> delegate, @NotNull Condition<? super Dom> condition) {
    this.delegate = delegate;
    this.condition = condition;
  }

  private FilteringIterator(@NotNull Predicate<? super Dom> condition, @NotNull Iterator<? extends Dom> delegate) {
    this.delegate = delegate;
    this.condition = condition;
  }

  private void obtainNext() {
    if (isNextObtained) return;
    boolean hasNext = delegate.hasNext();
    setCurrent(hasNext ? delegate.next() : null);

    isCurrentIsValid = hasNext;
    isNextObtained = true;
  }

  @Override
  public boolean hasNext() {
    obtainNext();
    if (!isCurrentIsValid) return false;
    boolean value = isCurrentPassesFilter();
    while (!value && delegate.hasNext()) {
      Dom next = delegate.next();
      setCurrent(next);
      value = isCurrentPassesFilter();
    }
    return value;
  }

  private void setCurrent(Dom next) {
    current = next;
    currentPassedFilter = null;
  }

  private boolean isCurrentPassesFilter() {
    if (currentPassedFilter != null) {
      return currentPassedFilter;
    }
    boolean passed = condition.test(current);
    currentPassedFilter = passed;
    return passed;
  }

  @Override
  public E next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }

    E result = (E)current;
    isNextObtained = false;
    return result;
  }

  /**
   * Works after call {@link #next} until call {@link #hasNext}
   * @throws IllegalStateException if {@link #hasNext} called
   */
  @Override
  public void remove() {
    if (isNextObtained) throw new IllegalStateException();
    delegate.remove();
  }

  @Override
  public E peek() {
    if (!hasNext()) throw new NoSuchElementException();
    return (E)current;
  }

  public static <T> Iterator<T> skipNulls(@NotNull Iterator<? extends T> iterator) {
    return create(iterator, Objects::nonNull);
  }

  public static <T> Iterator<T> create(@NotNull Iterator<? extends T> iterator, @NotNull Predicate<? super T> condition) {
    if (condition == Conditions.alwaysTrue()) {
      return (Iterator<T>)iterator;
    }
    return new FilteringIterator<>(condition, iterator);
  }

  public static <T> InstanceOf<T> instanceOf(Class<T> aClass) {
    return new InstanceOf<>(aClass);
  }

  public static final class InstanceOf<T> implements Condition<Object> {
    private final Class<T> myInstancesClass;

    public InstanceOf(@NotNull Class<T> instancesClass) {
      myInstancesClass = instancesClass;
    }

    @Override
    public boolean value(Object object) {
      return myInstancesClass.isInstance(object);
    }
  }
}
