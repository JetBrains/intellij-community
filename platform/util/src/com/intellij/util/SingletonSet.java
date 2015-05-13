/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.util.containers.SingletonIterator;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * Read-only set consisting of the only element
 */
public class SingletonSet<E> implements Set<E> {
  private final E theElement;

  public SingletonSet(E e) {
    theElement = e;
  }

  @Override
  public int size() {
    return 1;
  }

  @Override
  public boolean contains(Object elem) {
    //noinspection unchecked
    return getStrategy().equals(theElement, (E)elem);
  }

  @NotNull
  @Override
  public Iterator<E> iterator() {
    return new SingletonIterator<E>(theElement);
  }

  @NotNull
  @Override
  public Object[] toArray() {
    return new Object[]{theElement};
  }

  @NotNull
  @Override
  public <T> T[] toArray(@NotNull T[] a) {
    if (a.length == 0) {
      //noinspection unchecked
      a = (T[]) Array.newInstance(a.getClass().getComponentType(), 1);
    }
    //noinspection unchecked
    a[0] = (T)theElement;
    if (a.length > 1) {
        a[1] = null;
    }
    return a;
  }

  @Override
  public boolean add(E t) {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean remove(Object o) {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean containsAll(@NotNull Collection<?> c) {
    for (Object e : c) {
      if (!contains(e)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean addAll(@NotNull Collection<? extends E> c) {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean retainAll(@NotNull Collection<?> c) {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean removeAll(@NotNull Collection<?> c) {
    throw new IncorrectOperationException();
  }

  @Override
  public void clear() {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @NotNull
  protected TObjectHashingStrategy<E> getStrategy() {
    //noinspection unchecked
    return TObjectHashingStrategy.CANONICAL;
  }

  @NotNull
  public static <T> Set<T> withCustomStrategy(T o, @NotNull TObjectHashingStrategy<T> strategy) {
    return new CustomStrategySingletonSet<T>(o, strategy);
  }

  private static class CustomStrategySingletonSet<E> extends SingletonSet<E> {
    @NotNull private final TObjectHashingStrategy<E> strategy;

    private CustomStrategySingletonSet(E e, @NotNull final TObjectHashingStrategy<E> strategy) {
      super(e);
      this.strategy = strategy;
    }


    @Override
    @NotNull
    protected TObjectHashingStrategy<E> getStrategy() {
      return strategy;
    }
  }
}
