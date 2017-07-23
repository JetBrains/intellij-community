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
import com.intellij.util.keyFMap.KeyFMap;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
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
    return map.size() <= 5;
  }
}

class MapReference extends WeakReference<KeyFMap> {
  final int myHash;

  MapReference(KeyFMap referent) {
    super(referent);
    myHash = referent.getValueIdentityHashCode();
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
    return o1.equalsByReference(o2);
  }
}
