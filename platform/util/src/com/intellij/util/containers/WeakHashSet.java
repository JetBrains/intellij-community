// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.util.containers;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.util.Function;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Weak hash map.
 * Null keys are NOT allowed
 *
 */
final class WeakHashSet<T> extends AbstractSet<T> {
  private final Set<MyRef<T>> set = new THashSet<MyRef<T>>();
  private final ReferenceQueue<T> queue = new ReferenceQueue<T>();

  private static class MyRef<T> extends WeakReference<T> {
    private final int myHashCode;

    public MyRef(@NotNull T referent, ReferenceQueue<? super T> q) {
      super(referent, q);
      myHashCode = referent.hashCode();
    }

    @Override
    public int hashCode() {
      return myHashCode; // has to be stable even in presence of GC
    }

    // must compare to HardRef by its get().equals() for add()/remove()/contains() to put in correct bucket
    // must compare to another MyRef by identity to be able for remove() to remove GCed value from processQueue()
    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof MyRef)) return false;
      MyRef otherRef = (MyRef)obj;
      if (this instanceof HardRef || otherRef instanceof HardRef) {
        return Comparing.equal(otherRef.get(), get());
      }
      return this == obj;
    }
  }

  private static class HardRef<T> extends MyRef<T> {
    HardRef(@NotNull T referent) {
      super(referent, null);
    }
  }

  @Override
  public Iterator<T> iterator() {
    return ContainerUtil.filterIterator(ContainerUtil.mapIterator(set.iterator(), new Function<MyRef<T>, T>() {
                                                                    @Override
                                                                    public T fun(MyRef<T> ref) {
                                                                      return ref.get();
                                                                    }
                                                                  }
    ), new Condition<T>() {
      @Override
      public boolean value(T t) {
        return t != null;
      }
    });
  }

  @Override
  public int size() {
    return set.size();
  }

  @Override
  public boolean add(@NotNull T t) {
    processQueue();
    MyRef<T> ref = new MyRef<T>(t, queue);
    return set.add(ref);
  }

  @Override
  public boolean remove(@NotNull Object o) {
    processQueue();
    return set.remove(new HardRef<T>((T)o));
  }

  @Override
  public boolean contains(@NotNull Object o) {
    processQueue();
    return set.contains(new HardRef<T>((T)o));
  }

  @Override
  public void clear() {
    set.clear();
  }

  private void processQueue() {
    MyRef<T> ref;
    while ((ref = (MyRef<T>)queue.poll()) != null) {
      set.remove(ref);
    }
  }
}
