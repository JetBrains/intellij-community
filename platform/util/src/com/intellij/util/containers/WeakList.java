/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Implementation of the {@link List} interface which:
 * <ul>
 *   <li>Stores elements using weak semantics (see {@link java.lang.ref.WeakReference})</li>
 *   <li>Automatically reclaims storage for garbage collected elements</li>
 *   <li>Is thread safe</li>
 * </ul>
 */
public class WeakList<T> extends UnsafeWeakList<T> {
  public WeakList() {
    this(new WeakReferenceArray<T>());
  }

  // For testing only
  WeakList(@NotNull WeakReferenceArray<T> array) {
    super(array);
  }

  public T get(int index) {
    synchronized (myArray) {
      return super.get(index);
    }
  }

  public boolean add(T element) {
    synchronized (myArray) {
      return super.add(element);
    }
  }

  @Override
  public boolean contains(Object o) {
    synchronized (myArray) {
      return super.contains(o);
    }
  }

  public boolean addIfAbsent(T element) {
    synchronized (myArray) {
      return super.addIfAbsent(element);
    }
  }

  public void add(int index, T element) {
    synchronized (myArray) {
      super.add(index, element);
    }
  }

  @Override
  public T set(int index, T element) {
    synchronized (myArray) {
      return super.set(index, element);
    }
  }

  @Override
  public int indexOf(Object o) {
    synchronized (myArray) {
      return super.indexOf(o);
    }
  }

  @Override
  public int lastIndexOf(Object o) {
    synchronized (myArray) {
      return super.lastIndexOf(o);
    }
  }

  @Override
  public void clear() {
    synchronized (myArray) {
      super.clear();
    }
  }

  @Override
  protected void removeRange(int fromIndex, int toIndex) {
    synchronized (myArray) {
      super.removeRange(fromIndex, toIndex);
    }
  }

  @Override
  public boolean remove(Object o) {
    synchronized (myArray) {
      return super.remove(o);
    }
  }

  public T remove(int index) {
    synchronized (myArray) {
      return super.remove(index);
    }
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    synchronized (myArray) {
      return super.removeAll(c);
    }
  }

  public Iterator<T> iterator() {
    return new MySyncIterator();
  }

  public int size() {
    synchronized (myArray) {
      return myArray.size();
    }
  }

  public List<T> toStrongList() {
    synchronized (myArray) {
      List<T> result = new ArrayList<T>(myArray.size());
      myArray.toStrongCollection(result);
      return result;
    }
  }

  public List<T> copyAndClear() {
    synchronized (myArray) {
      List<T> result = new ArrayList<T>(myArray.size());
      myArray.toStrongCollection(result);
      clear();
      return result;
    }
  }

  private class MySyncIterator extends MyIterator {
    protected void findNext() {
      synchronized (myArray) {
        super.findNext();
      }
    }

    public void remove() {
      synchronized (myArray) {
        super.remove();
      }
    }
  }
}
