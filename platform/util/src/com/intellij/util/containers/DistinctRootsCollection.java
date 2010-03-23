/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

/**
 * Created by IntelliJ IDEA.
 * User: ksafonov
 * Date: 22.03.2010
 * Time: 16:43:12
 * To change this template use File | Settings | File Templates.
 */
public abstract class DistinctRootsCollection<T> implements Collection<T> {
  private final Collection<T> myCollection = new ArrayList<T>();

  protected abstract boolean isAncestor(@NotNull T ancestor, @NotNull T t);

  public DistinctRootsCollection() {
  }

  public DistinctRootsCollection(Collection<T> collection) {
    addAll(collection);
  }

  public DistinctRootsCollection(T[] collection) {
    this(Arrays.asList(collection));
  }

  public int size() {
    return myCollection.size();
  }

  public boolean isEmpty() {
    return myCollection.isEmpty();
  }

  public boolean contains(Object o) {
    return myCollection.contains(o);
  }

  public Iterator<T> iterator() {
    return myCollection.iterator();
  }

  public Object[] toArray() {
    return myCollection.toArray();
  }

  public <T> T[] toArray(T[] a) {
    return myCollection.toArray(a);
  }

  public boolean add(T o) {
    Collection<T> toRemove = new ArrayList<T>();
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

  public boolean remove(Object o) {
    return myCollection.remove(o);
  }

  public boolean containsAll(Collection<?> c) {
    return myCollection.containsAll(c);
  }

  public boolean addAll(Collection<? extends T> c) {
    boolean changed = false;
    for (T t : c) {
      changed |= add(t);
    }
    return changed;
  }

  public boolean removeAll(Collection<?> c) {
    return myCollection.removeAll(c);
  }

  public boolean retainAll(Collection<?> c) {
    return myCollection.retainAll(c);
  }

  public void clear() {
    myCollection.clear();
  }

}
