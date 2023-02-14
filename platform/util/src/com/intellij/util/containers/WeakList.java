// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Implementation of the {@link Collection} interface which:
 * <ul>
 *   <li>Stores elements using weak semantics (see {@link java.lang.ref.WeakReference})</li>
 *   <li>Automatically reclaims storage for garbage collected elements</li>
 *   <li>Is thread safe</li>
 *   <li>Is NOT RandomAccess, because garbage collector can remove element at any time</li>
 *   <li>Does NOT support null elements</li>
 * </ul>
 * Please note that since weak references can be collected at any time, index-based methods (like get(index))
 * or size-based methods (like size()) are dangerous, misleading, error-inducing and are not supported.
 * Instead, please use {@link #add(T)} and {@link #iterator()}.
 */
public final class WeakList<T> extends UnsafeWeakList<T> {
  public WeakList() {
  }
  public WeakList(int initialCapacity) {
    super(initialCapacity);
  }

  @Override
  public boolean add(@NotNull T element) {
    synchronized (myList) {
      return super.add(element);
    }
  }

  @Override
  public boolean addAll(@NotNull Collection<? extends T> c) {
    synchronized (myList) {
      return super.addAll(c);
    }
  }

  @Override
  public boolean addIfAbsent(@NotNull T element) {
    synchronized (myList) {
      return super.addIfAbsent(element);
    }
  }

  @Override
  public void clear() {
    synchronized (myList) {
      super.clear();
    }
  }

  @Override
  public boolean contains(@NotNull Object o) {
    synchronized (myList) {
      return super.contains(o);
    }
  }

  @Override
  public boolean remove(@NotNull Object o) {
    synchronized (myList) {
      return super.remove(o);
    }
  }

  @Override
  public boolean removeAll(@NotNull Collection<?> c) {
    synchronized (myList) {
      return super.removeAll(c);
    }
  }

  @Override
  public boolean isEmpty() {
    synchronized (myList) {
      return super.isEmpty();
    }
  }

  @Override
  @NotNull
  public Iterator<@NotNull T> iterator() {
    final Iterator<T> iterator;
    synchronized (myList) {
      iterator = super.iterator();
    }
    return new Iterator<T>(){
      @Override
      public boolean hasNext() {
        synchronized (myList) {
          return iterator.hasNext();
        }
      }

      @Override
      public T next() {
        synchronized (myList) {
          return iterator.next();
        }
      }

      @Override
      public void remove() {
        synchronized (myList) {
          iterator.remove();
        }
      }
    };
  }

  @NotNull
  @Override
  public List<@NotNull T> toStrongList() {
    synchronized (myList) {
      return super.toStrongList();
    }
  }

  @NotNull
  public List<@NotNull T> copyAndClear() {
    synchronized (myList) {
      List<T> result = toStrongList();
      clear();
      return result;
    }
  }
}
