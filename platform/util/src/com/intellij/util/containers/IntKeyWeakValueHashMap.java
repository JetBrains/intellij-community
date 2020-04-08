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

import com.intellij.reference.SoftReference;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectIterator;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;

class IntKeyWeakValueHashMap<V> implements IntObjectMap<V> {
  private final TIntObjectHashMap<MyReference<V>> myMap = new TIntObjectHashMap<>();
  private final ReferenceQueue<V> myQueue = new ReferenceQueue<>();

  private static class MyReference<T> extends WeakReference<T> {
    private final int key;

    private MyReference(int key, @NotNull T referent, ReferenceQueue<? super T> q) {
      super(referent, q);
      this.key = key;
    }
  }

  private void processQueue() {
    while(true){
      MyReference ref = (MyReference)myQueue.poll();
      if (ref == null) {
        return;
      }
      int key = ref.key;
      myMap.remove(key);
    }
  }

  @Override
  public final V get(int key) {
    return SoftReference.dereference(myMap.get(key));
  }

  @Override
  public final V put(int key, @NotNull V value) {
    processQueue();
    MyReference<V> ref = new MyReference<>(key, value, myQueue);
    MyReference<V> oldRef = myMap.put(key, ref);
    return SoftReference.dereference(oldRef);
  }

  @Override
  public final V remove(int key) {
    processQueue();
    MyReference<V> ref = myMap.remove(key);
    return SoftReference.dereference(ref);
  }

  @Override
  public final void clear() {
    myMap.clear();
    processQueue();
  }

  @Override
  public final int size() {
    return myMap.size();
  }

  @Override
  public final boolean isEmpty() {
    return myMap.isEmpty();
  }

  @Override
  public final boolean containsKey(int key) {
    throw RefValueHashMap.pointlessContainsKey();
  }

  @Override
  @NotNull
  public final Collection<V> values() {
    List<V> result = new ArrayList<>();
    Object[] refs = myMap.getValues();
    for (Object o : refs) {
      @SuppressWarnings("unchecked")
      final V value = ((MyReference<V>)o).get();
      if (value != null) {
        result.add(value);
      }
    }
    return result;
  }

  @Override
  public int @NotNull [] keys() {
    throw new IncorrectOperationException("keys() makes no sense for weak/soft map because GC can clear the value any moment now");
  }

  @Override
  public boolean containsValue(@NotNull V value) {
    return values().contains(value);
  }

  @NotNull
  @Override
  public Set<Entry<V>> entrySet() {
    return new MyEntrySetView();
  }

  private class MyEntrySetView extends AbstractSet<Entry<V>> {
    @NotNull
    @Override
    public Iterator<Entry<V>> iterator() {
      return entriesIterator();
    }

    @Override
    public int size() {
      return IntKeyWeakValueHashMap.this.size();
    }
  }

  private static class MovingIterator<V> extends TIntObjectIterator<MyReference<V>> {
    MovingIterator(TIntObjectHashMap<MyReference<V>> map) {
      super(map);
    }

    void removed() {
      _expectedSize--;
    }
  }

  @NotNull
  private Iterator<Entry<V>> entriesIterator() {
    final MovingIterator<V> entryIterator = new MovingIterator<>(myMap);
    return new Iterator<Entry<V>>() {
      private Entry<V> nextVEntry;
      private int lastReturned;
      {
        nextAliveEntry();
      }
      @Override
      public boolean hasNext() {
        return nextVEntry != null;
      }

      @Override
      public Entry<V> next() {
        if (!hasNext()) throw new NoSuchElementException();
        Entry<V> result = nextVEntry;
        lastReturned = result.getKey();
        nextAliveEntry();
        return result;
      }

      private void nextAliveEntry() {
        while (entryIterator.hasNext()) {
          entryIterator.advance();

          MyReference<V> ref = entryIterator.value();
          final V v = ref.get();
          if (v == null) {
            continue;
          }
          final int key = entryIterator.key();
          nextVEntry = new SimpleEntry<>(key, v);
          return;
        }
        nextVEntry = null;
      }

      @Override
      public void remove() {
        myMap.remove(lastReturned);
        entryIterator.removed();
      }
    };
  }
}
