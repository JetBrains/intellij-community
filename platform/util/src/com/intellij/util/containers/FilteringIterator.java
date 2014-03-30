/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.util.Conditions;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * {@link #remove} throws {@link IllegalStateException} if called after {@link #hasNext}
 *  @author dsl
 *  @author dyoma
 */
public class FilteringIterator<Dom, E extends Dom> implements Iterator<E> {
  private final Iterator<Dom> myBaseIterator;
  private final Condition<Dom> myFilter;
  private boolean myNextObtained = false;
  private boolean myCurrentIsValid = false;
  private Dom myCurrent;
  private Boolean myCurrentPassedFilter = null;
  public static final Condition NOT_NULL = new Condition() {
    @Override
    public boolean value(Object t) {
      return t != null;
    }
  };

  public FilteringIterator(@NotNull Iterator<Dom> baseIterator, @NotNull Condition<Dom> filter) {
    myBaseIterator = baseIterator;
    myFilter = filter;
  }

  private void obtainNext() {
    if (myNextObtained) return;
    boolean hasNext = myBaseIterator.hasNext();
    setCurrent(hasNext ? myBaseIterator.next() : null);

    myCurrentIsValid = hasNext;
    myNextObtained = true;
  }

  @Override
  public boolean hasNext() {
    obtainNext();
    if (!myCurrentIsValid) return false;
    boolean value = isCurrentPassesFilter();
    while (!value && myBaseIterator.hasNext()) {
      Dom next = myBaseIterator.next();
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
    if (myCurrentPassedFilter != null) return myCurrentPassedFilter.booleanValue();
    boolean passed = myFilter.value(myCurrent);
    myCurrentPassedFilter = Boolean.valueOf(passed);
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
    myBaseIterator.remove();
  }

  public static <T> Iterator<T> skipNulls(Iterator<T> iterator) {
    return create(iterator, NOT_NULL);
  }

  public static <Dom, T extends Dom> Iterator<T> create(Iterator<Dom> iterator, Condition<Dom> condition) {
    return new FilteringIterator<Dom, T>(iterator, condition);
  }

  public static <T> Condition<T> alwaysTrueCondition(Class<T> aClass) {
    return Conditions.alwaysTrue();
  }

  public static <T> InstanceOf<T> instanceOf(final Class<T> aClass) {
    return new InstanceOf<T>(aClass);
  }

  public static <T> Iterator<T> createInstanceOf(Iterator<?> iterator, Class<T> aClass) {
    return create((Iterator<T>)iterator, instanceOf(aClass));
  }

  public static class InstanceOf<T> implements Condition {
    private final Class<T> myInstancesClass;

    public InstanceOf(Class<T> instancesClass) {
      myInstancesClass = instancesClass;
    }

    @Override
    public boolean value(Object object) {
      return myInstancesClass.isInstance(object);
    }

    public boolean isClassAcceptable(Class hintClass) {
      return ReflectionUtil.isAssignable(myInstancesClass, hintClass);
    }

    public T cast(Object object) {
      return (T)object;
    }
  }
}
