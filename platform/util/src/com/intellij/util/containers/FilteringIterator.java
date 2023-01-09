// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * {@link #remove} throws {@link IllegalStateException} if called after {@link #hasNext}
 *  @author dsl
 *  @author dyoma
 */
public final class FilteringIterator<Dom, E extends Dom> implements PeekableIterator<E> {
  private final Iterator<? extends Dom> myDelegate;
  private final Condition<? super Dom> myCondition;
  private boolean myNextObtained;
  private boolean myCurrentIsValid;
  private Dom myCurrent;
  private Boolean myCurrentPassedFilter;

  public FilteringIterator(@NotNull Iterator<? extends Dom> delegate, @NotNull Condition<? super Dom> condition) {
    myDelegate = delegate;
    myCondition = condition;
  }

  private void obtainNext() {
    if (myNextObtained) return;
    boolean hasNext = myDelegate.hasNext();
    setCurrent(hasNext ? myDelegate.next() : null);

    myCurrentIsValid = hasNext;
    myNextObtained = true;
  }

  @Override
  public boolean hasNext() {
    obtainNext();
    if (!myCurrentIsValid) return false;
    boolean value = isCurrentPassesFilter();
    while (!value && myDelegate.hasNext()) {
      Dom next = myDelegate.next();
      setCurrent(next);
      value = isCurrentPassesFilter();
    }
    return value;
  }

  private void setCurrent(Dom next) {
    myCurrent = next;
    myCurrentPassedFilter = null;
  }

  private boolean isCurrentPassesFilter() {
    if (myCurrentPassedFilter != null) {
      return myCurrentPassedFilter;
    }
    boolean passed = myCondition.value(myCurrent);
    myCurrentPassedFilter = passed;
    return passed;
  }

  @Override
  public E next() {
    if (!hasNext()) throw new NoSuchElementException();
    E result = (E)myCurrent;
    myNextObtained = false;
    return result;
  }

  /**
   * Works after call {@link #next} until call {@link #hasNext}
   * @throws IllegalStateException if {@link #hasNext} called
   */
  @Override
  public void remove() {
    if (myNextObtained) throw new IllegalStateException();
    myDelegate.remove();
  }

  @Override
  public E peek() {
    if (!hasNext()) throw new NoSuchElementException();
    return (E)myCurrent;
  }

  public static <T> Iterator<T> skipNulls(Iterator<? extends T> iterator) {
    return create(iterator, Conditions.notNull());
  }

  public static <T> Iterator<T> create(Iterator<? extends T> iterator, Condition<? super T> condition) {
    if (condition == Conditions.alwaysTrue()) {
      return (Iterator<T>)iterator;
    }
    return new FilteringIterator<>(iterator, condition);
  }

  public static <T> Condition<T> alwaysTrueCondition(Class<T> aClass) {
    return Conditions.alwaysTrue();
  }

  public static <T> InstanceOf<T> instanceOf(final Class<T> aClass) {
    return new InstanceOf<>(aClass);
  }

  public static <T> Iterator<T> createInstanceOf(Iterator<?> iterator, Class<T> aClass) {
    return create((Iterator<T>)iterator, instanceOf(aClass));
  }

  public static class InstanceOf<T> implements Condition<Object> {
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
