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

import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.RandomAccess;
import java.util.Set;

// have to extend ArrayList because otherwise the spliterator() methods declared in Set and List are in conflict
public class OrderedSet<T> extends ArrayList<T> implements Set<T>, RandomAccess {
  private final OpenTHashSet<T> myHashSet;

  public OrderedSet() {
    this(ContainerUtil.<T>canonicalStrategy());
  }

  public OrderedSet(@NotNull TObjectHashingStrategy<T> hashingStrategy) {
    this(hashingStrategy, 4);
  }

  public OrderedSet(@NotNull TObjectHashingStrategy<T> hashingStrategy, int capacity) {
    super(capacity);
    myHashSet = new OpenTHashSet<T>(capacity, hashingStrategy);
  }

  @Override
  public boolean contains(Object o) {
    return myHashSet.contains(o);
  }

  @Override
  public boolean add(T o) {
    if (myHashSet.add(o)){
      super.add(o);
      return true;
    }
    return false;
  }

  @Override
  public boolean remove(Object o) {
    if (myHashSet.remove(o)){
      super.remove(o);
      return true;
    }
    return false;
  }

  @Override
  public void clear() {
    myHashSet.clear();
    super.clear();
  }

  @Override
  public boolean addAll(final int index, final Collection<? extends T> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public T set(final int index, final T element) {
    final T removed = remove(index);
    add(index, element);
    return removed;
  }

  @Override
  public void add(final int index, final T element) {
    if (myHashSet.add(element)){
      super.add(index, element);
    }
  }

  @Override
  public T remove(final int index) {
    final T t = super.remove(index);
    myHashSet.remove(t);
    return t;
  }

  @Override
  public int indexOf(final Object o) {
    final int index = myHashSet.index((T)o);
    return index >= 0? super.indexOf(myHashSet.get(index)) : -1;
  }

  @Override
  public int lastIndexOf(final Object o) {
    final int index = myHashSet.index((T)o);
    return index >= 0 ? super.lastIndexOf(myHashSet.get(index)) : -1;
  }
}
