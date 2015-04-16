/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: Alexey
 * Date: 18.12.2006
 * Time: 20:18:31
 */
package com.intellij.util.containers;

import com.intellij.openapi.util.Comparing;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/**
 * Concurrent map with weak keys and weak values.
 * Null keys are NOT allowed
 * Null values are NOT allowed
 * @deprecated Use {@link ContainerUtil#createConcurrentWeakKeyWeakValueMap()} instead
 */
class ConcurrentWeakKeyWeakValueHashMap<K, V> extends ConcurrentWeakKeySoftValueHashMap<K,V> {
  ConcurrentWeakKeyWeakValueHashMap(int initialCapacity,
                                    float loadFactor,
                                    int concurrencyLevel,
                                    @NotNull final TObjectHashingStrategy<K> hashingStrategy) {
    super(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
  }

  private static class WeakValue<K, V> extends WeakReference<V> implements ValueReference<K,V> {
   @NotNull private volatile KeyReference<K, V> myKeyReference; // can't make it final because of circular dependency of KeyReference to ValueReference
   private final int myHash; // Hashcode of key, stored here since the key may be tossed by the GC
   private WeakValue(@NotNull V value, @NotNull ReferenceQueue<V> queue) {
     super(value, queue);
     myHash = value.hashCode();
   }

   // MUST work with gced references too for the code in processQueue to work
   @Override
   public final boolean equals(final Object o) {
     if (this == o) return true;
     if (o == null) return false;

     ValueReference that = (ValueReference)o;

     return myHash == that.hashCode() && Comparing.equal(get(), that.get());
   }

   @Override
   public final int hashCode() {
     return myHash;
   }

   @NotNull
   @Override
   public KeyReference<K, V> getKeyReference() {
     return myKeyReference;
   }
 }

  @NotNull
  protected KeyReference<K,V> createKeyReference(@NotNull K k, @NotNull final V v) {
    final ValueReference<K, V> valueReference = createValueReference(v, myValueQueue);
    WeakKey<K, V> keyReference = new WeakKey<K, V>(k, valueReference, myHashingStrategy, myKeyQueue);
    if (valueReference instanceof WeakValue) {
      ((WeakValue)valueReference).myKeyReference = keyReference;
    }
    return keyReference;
  }

  @NotNull
  protected ValueReference<K, V> createValueReference(@NotNull V value, @NotNull ReferenceQueue<V> queue) {
    return new WeakValue<K, V>(value, queue);
  }
}
