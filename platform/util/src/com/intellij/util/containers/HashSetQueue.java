// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;

/**
 * Unbounded non-thread-safe {@link Queue} with fast add/remove/contains.<br>
 * Differs from the conventional {@link Queue} by:<ul>
 * <li>The new method {@link #find(T)} which finds the queue element equivalent to its argument in hashmap-like O(1) avg. time</li>
 * <li>The {@link #contains(Object)} method is O(1)</li>
 * <li>The {@link #remove(Object)} method is O(1)</li>
 * <li>Null elements are NOT permitted</li>
 * </ul>
 * Differs from the conventional {@link java.util.LinkedHashSet} by additional queue flavor:<ul>
 * <li>The distinguished "first added" and "last added" elements (stored in {@link #TOMB} field in its {@link QueueEntry#prev} and {@link QueueEntry#next} fields correspondingly)</li>
 * <li>Ability to iterate these elements in the modification order by {@link PositionalIterator}</li>
 * <li>Ability to modify these elements in the queue fashion by {@link #offer(Object)}, {@link #poll()}
 * </ul>
 * Implementation is backed by {@link ObjectOpenHashSet} containing double-linked QueueEntry nodes holding elements themselves.
 */
public final class HashSetQueue<T> extends AbstractCollection<T> implements Queue<T> {
  private final ObjectOpenHashSet<QueueEntry<T>> set = new ObjectOpenHashSet<>();
  // Entries in the queue are double-linked circularly, the TOMB serving as a sentinel.
  // TOMB.next is the first entry; TOMB.prev is the last entry;
  // TOMB.next == TOMB.prev == TOMB means the queue is empty
  private final QueueEntry<T> TOMB = new QueueEntry<>(cast(new Object()));

  public HashSetQueue() {
    TOMB.next = TOMB.prev = TOMB;
  }

  private static class QueueEntry<T> {
    @NotNull private final T t;
    private QueueEntry<T> next;
    private QueueEntry<T> prev;

    QueueEntry(@NotNull T t) {
      this.t = t;
    }

    @Override
    public int hashCode() {
      return t.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      //noinspection unchecked
      return obj instanceof QueueEntry && t.equals(((QueueEntry<T>)obj).t);
    }

    @Override
    public String toString() {
      return t.toString();
    }
  }

  @Override
  public boolean offer(@NotNull T t) {
    return add(t);
  }

  @Override
  public boolean add(@NotNull T t) {
    QueueEntry<T> newLast = new QueueEntry<>(t);
    boolean added = set.add(newLast);
    if (!added) return false;
    QueueEntry<T> oldLast = TOMB.prev;

    oldLast.next = newLast;
    newLast.prev = oldLast;
    newLast.next = TOMB;
    TOMB.prev = newLast;

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
    return TOMB.next == TOMB ? null : TOMB.next.t;
  }

  public T find(@NotNull T t) {
    QueueEntry<T> existing = findEntry(t);
    return existing == null ? null : existing.t;
  }

  private QueueEntry<T> findEntry(@NotNull T t) {
    return set.get(new QueueEntry<>(t));
  }

  @Override
  public boolean remove(Object o) {
    T t = cast(o);
    QueueEntry<T> entry = findEntry(t);
    if (entry == null) return false;
    QueueEntry<T> prev = entry.prev;
    QueueEntry<T> next = entry.next;

    prev.next = next;
    next.prev = prev;

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
  public PositionalIterator<T> iterator() {
    return new PositionalIterator<T>() {
      private QueueEntry<T> cursor = TOMB;
      private long count;
      @Override
      public boolean hasNext() {
        return cursor.next != TOMB;
      }

      @Override
      public T next() {
        cursor = cursor.next;
        count++;
        return cursor.t;
      }

      @Override
      public void remove() {
        if (cursor == TOMB) throw new NoSuchElementException();
        HashSetQueue.this.remove(cursor.t);
      }

      @NotNull
      @Override
      public IteratorPosition<T> position() {
        return new MyIteratorPosition<>(cursor, count, TOMB);
      }
    };
  }

  private static final class MyIteratorPosition<T> implements PositionalIterator.IteratorPosition<T> {
    private final QueueEntry<T> cursor;
    private final long count;
    private final QueueEntry<T> TOMB;

    private MyIteratorPosition(@NotNull QueueEntry<T> cursor, long count, @NotNull QueueEntry<T> TOMB) {
      this.cursor = cursor;
      this.count = count;
      this.TOMB = TOMB;
    }

    @Override
    public T peek() {
      if (cursor == TOMB) {
        throw new IllegalStateException("Iterator is before the first element. Must call .next() first.");
      }
      return cursor.t;
    }

    @Override
    public PositionalIterator.IteratorPosition<T> next() {
      return cursor.next == TOMB ? null : new MyIteratorPosition<>(cursor.next, count + 1, TOMB);
    }

    @Override
    public int compareTo(@NotNull PositionalIterator.IteratorPosition<T> o) {
      return Long.compare(count, ((MyIteratorPosition<T>)o).count);
    }
  }

  public interface PositionalIterator<T> extends Iterator<T> {
    /**
     * @return the current position of this iterator.
     * The position of the newly created iterator is before the first element of the queue (so the {@link IteratorPosition#peek()} value is undefined)
     */
    @NotNull
    IteratorPosition<T> position();

    interface IteratorPosition<T> extends Comparable<IteratorPosition<T>>  {
      T peek();
      IteratorPosition<T> next();
    }
  }
}
