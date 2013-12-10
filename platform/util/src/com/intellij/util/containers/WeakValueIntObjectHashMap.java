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
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class WeakValueIntObjectHashMap<V> {
  private final TIntObjectHashMap<MyReference<V>> myMap = new TIntObjectHashMap<MyReference<V>>();
  private final ReferenceQueue<V> myQueue = new ReferenceQueue<V>();

  private static class MyReference<T> extends WeakReference<T> {
    private final int key;
    String name;

    private MyReference(int key, T referent, ReferenceQueue<? super T> q) {
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
      keyExpired(key);
    }
  }

  protected void keyExpired(int key) {

  }

  public final V get(int key) {
    MyReference<V> ref = myMap.get(key);
    return SoftReference.dereference(ref);
  }

  public final V put(int key, @NotNull V value) {
    processQueue();
    MyReference<V> ref = new MyReference<V>(key, value, myQueue);
    ref.name = value.toString();
    MyReference<V> oldRef = myMap.put(key, ref);
    return SoftReference.dereference(oldRef);
  }

  public final V remove(int key) {
    processQueue();
    MyReference<V> ref = myMap.remove(key);
    return SoftReference.dereference(ref);
  }

  public final void clear() {
    myMap.clear();
    processQueue();
  }

  public final int size() {
    return myMap.size();
  }

  public final boolean isEmpty() {
    return myMap.isEmpty();
  }

  public final boolean containsKey(int key) {
    return get(key) != null;
  }

  @NotNull
  public final Collection<V> values() {
    List<V> result = new ArrayList<V>();
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
}
