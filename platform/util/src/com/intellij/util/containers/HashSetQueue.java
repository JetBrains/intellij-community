/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;

/**
 * Unbounded non-thread-safe {@link Queue} implementation backed by {@link gnu.trove.THashSet}.
 * Differs from the conventional Queue by:<ul>
 * <li>The new method {@link #find(T)} which finds the queue element equivalent to its parameter in O(1) avg. time</li>
 * <li>The {@link #contains(Object)} method is O(1)</li>
 * <li>The {@link #remove(Object)} method is O(1)</li>
 * </ul>
 */
public class HashSetQueue<T> extends AbstractCollection<T> implements Queue<T> {
  private final OpenTHashSet<QueueEntry<T>> set = new OpenTHashSet<QueueEntry<T>>();
  private QueueEntry<T> last;
  private QueueEntry<T> first;

  private static class QueueEntry<T> {
    @NotNull private final T t;
    private QueueEntry<T> next;
    private QueueEntry<T> prev;

    public QueueEntry(@NotNull T t) {
      this.t = t;
    }

    @Override
    public int hashCode() {
      return t.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof QueueEntry && t.equals(((QueueEntry)obj).t);
    }
  }

  @Override
  public boolean offer(@NotNull T t) {
    return add(t);
  }

  @Override
  public boolean add(@NotNull T t) {
    QueueEntry<T> newLast = new QueueEntry<T>(t);
    boolean added = set.add(newLast);
    if (!added) return false;
    if (last == null) {
      last = newLast;
      first = newLast;
    }
    else {
      last.next = newLast;
      newLast.prev = last;
      last = newLast;
    }

    return true;
  }

  @Override
  @NotNull
  public T remove() {
    T poll = poll();
    if (poll == null) throw new NoSuchElementException();
    return poll;
  }

  @Override
  public T poll() {
    T peek = peek();
    if (peek != null) {
      remove(peek);
    }
    return peek;
  }

  @Override
  @NotNull
  public T element() {
    T peek = peek();
    if (peek == null) throw new NoSuchElementException();
    return peek;
  }

  @Override
  public T peek() {
    return first == null ? null : first.t;
  }

  public T find(@NotNull T t) {
    QueueEntry<T> existing = findEntry(t);
    return existing == null ? null : existing.t;
  }

  private QueueEntry<T> findEntry(@NotNull T t) {
    return set.get(new QueueEntry<T>(t));
  }

  @Override
  public boolean remove(Object o) {
    T t = cast(o);
    QueueEntry<T> entry = findEntry(t);
    if (entry == null) return false;
    QueueEntry<T> prev = entry.prev;
    QueueEntry<T> next = entry.next;
    if (prev != null) {
      prev.next = next;
    }
    else {
      first = next;
    }
    if (next != null) {
      next.prev = prev;
    }
    else {
      last = prev;
    }
    set.remove(entry);
    return true;
  }

  @Override
  public int size() {
    return set.size();
  }

  @Override
  public boolean contains(Object o) {
    return find(cast(o)) != null;
  }

  private T cast(Object o) {
    //noinspection unchecked
    return (T)o;
  }

  @NotNull
  @Override
  public Iterator<T> iterator() {
    return new Iterator<T>() {
      QueueEntry<T> cursor = first;
      @Override
      public boolean hasNext() {
        return cursor != null;
      }

      @Override
      public T next() {
        QueueEntry<T> entry = cursor;
        cursor = cursor.next;
        return entry.t;
      }

      @Override
      public void remove() {
        QueueEntry<T> toDelete = cursor == null ? last : cursor.prev;
        if (toDelete == null) throw new NoSuchElementException();
        HashSetQueue.this.remove(toDelete.t);
      }
    };
  }
}
