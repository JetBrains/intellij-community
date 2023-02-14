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

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

public abstract class DistinctRootsCollection<T> implements Collection<T> {
  private final Collection<T> myCollection = new ArrayList<>();

  protected abstract boolean isAncestor(@NotNull T ancestor, @NotNull T t);

  public DistinctRootsCollection() {
  }

  public DistinctRootsCollection(@NotNull Collection<? extends T> collection) {
    addAll(collection);
  }

  public DistinctRootsCollection(T @NotNull [] collection) {
    this(Arrays.asList(collection));
  }

  @Override
  public int size() {
    return myCollection.size();
  }

  @Override
  public boolean isEmpty() {
    return myCollection.isEmpty();
  }

  @Override
  public boolean contains(Object o) {
    return myCollection.contains(o);
  }

  @NotNull
  @Override
  public Iterator<T> iterator() {
    return myCollection.iterator();
  }

  @Override
  public Object @NotNull [] toArray() {
    return myCollection.toArray();
  }

  @Override
  public <T> T @NotNull [] toArray(T @NotNull [] a) {
    return myCollection.toArray(a);
  }

  @Override
  public synchronized boolean add(T o) {
    Collection<T> toRemove = new ArrayList<>();
    for (T existing : myCollection) {
      if (isAncestor(existing, o)) {
        return false;
      }
      if (isAncestor(o, existing)) {
        toRemove.add(existing);
      }
    }
    myCollection.removeAll(toRemove);
    myCollection.add(o);
    return true;
  }

  @Override
  public boolean remove(Object o) {
    return myCollection.remove(o);
  }

  @Override
  public boolean containsAll(@NotNull Collection<?> c) {
    return myCollection.containsAll(c);
  }

  @Override
  public boolean addAll(@NotNull Collection<? extends T> c) {
    boolean changed = false;
    for (T t : c) {
      changed |= add(t);
    }
    return changed;
  }

  @Override
  public boolean removeAll(@NotNull Collection<?> c) {
    return myCollection.removeAll(c);
  }

  @Override
  public boolean retainAll(@NotNull Collection<?> c) {
    return myCollection.retainAll(c);
  }

  @Override
  public void clear() {
    myCollection.clear();
  }

}
