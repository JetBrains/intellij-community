/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.reference.SoftReference;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.keyFMap.ArrayBackedFMap;
import com.intellij.util.keyFMap.KeyFMap;
import com.intellij.util.keyFMap.OneElementFMap;
import com.intellij.util.keyFMap.PairElementsFMap;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Map;

/**
 * @author peter
 */
class UserDataInterner {
  private static final LinkedHashMap<MapReference, MapReference> ourCache = new LinkedHashMap<MapReference, MapReference>(20, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<MapReference, MapReference> eldest) {
      return size() > 15;
    }
  };
  
  static KeyFMap internUserData(@NotNull KeyFMap map) {
    if (shouldIntern(map)) {
      MapReference key = new MapReference(map);
      synchronized (ourCache) {
        KeyFMap cached = SoftReference.dereference(ourCache.get(key));
        if (cached != null) return cached;

        ourCache.put(key, key);
      }
      return map;
    }
    return map;
  }

  private static boolean shouldIntern(@NotNull KeyFMap map) {
    return map instanceof OneElementFMap || 
           map instanceof PairElementsFMap ||
           map instanceof ArrayBackedFMap && ((ArrayBackedFMap)map).getKeyIds().length <= 5;
  }
}

class MapReference extends WeakReference<KeyFMap> {
  final int myHash;

  MapReference(KeyFMap referent) {
    super(referent);
    myHash = computeHashCode(referent);
  }

  private static int computeHashCode(KeyFMap object) {
    if (object instanceof OneElementFMap) {
      return ((OneElementFMap)object).getKey().hashCode() * 31 + System.identityHashCode(((OneElementFMap)object).getValue());
    }
    if (object instanceof PairElementsFMap) {
      PairElementsFMap map = (PairElementsFMap)object;
      return (map.getKey1().hashCode() * 31 + map.getKey2().hashCode()) * 31 +
             System.identityHashCode(map.getValue1()) + System.identityHashCode(map.getValue2());
    }
    if (object instanceof ArrayBackedFMap) {
      int hc = Arrays.hashCode(((ArrayBackedFMap)object).getKeyIds());
      for (Object o : ((ArrayBackedFMap)object).getValues()) {
        hc = hc * 31 + System.identityHashCode(o);
      }
      return hc;
    }
    return 0;
  }

  @Override
  public int hashCode() {
    return myHash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof MapReference) || myHash != ((MapReference)obj).myHash) return false;

    KeyFMap o1 = get();
    KeyFMap o2 = ((MapReference)obj).get();
    if (o1 == null || o2 == null) return false;

    if (o1 instanceof OneElementFMap && o2 instanceof OneElementFMap) {
      OneElementFMap m1 = (OneElementFMap)o1;
      OneElementFMap m2 = (OneElementFMap)o2;
      return m1.getKey() == m2.getKey() && m1.getValue() == m2.getValue();
    }
    if (o1 instanceof PairElementsFMap && o2 instanceof PairElementsFMap) {
      PairElementsFMap m1 = (PairElementsFMap)o1;
      PairElementsFMap m2 = (PairElementsFMap)o2;
      return m1.getKey1() == m2.getKey1() && m1.getKey2() == m2.getKey2() &&
             m1.getValue1() == m2.getValue1() && m1.getValue2() == m2.getValue2();
    }
    if (o1 instanceof ArrayBackedFMap && o2 instanceof ArrayBackedFMap) {
      ArrayBackedFMap m1 = (ArrayBackedFMap)o1;
      ArrayBackedFMap m2 = (ArrayBackedFMap)o2;
      return Arrays.equals(m1.getKeyIds(), m2.getKeyIds()) && containSameElements(m1.getValues(), m2.getValues());
    }
    return false;
  }

  private static boolean containSameElements(Object[] v1, Object[] v2) {
    if (v1.length != v2.length) return false;

    for (int i = 0; i < v1.length; i++) {
      if (v1[i] != v2[i]) return false;
    }
    return true;
  }
}
