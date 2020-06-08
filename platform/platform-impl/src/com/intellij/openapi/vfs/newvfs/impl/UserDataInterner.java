// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
final class UserDataInterner {
  private static final Map<MapReference, MapReference> ourCache = new LinkedHashMap<MapReference, MapReference>(20, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<MapReference, MapReference> eldest) {
      return size() > 15;
    }
  };

  @NotNull
  static KeyFMap internUserData(@NotNull KeyFMap map) {
    if (shouldIntern(map)) {
      MapReference key = new MapReference(map);
      synchronized (ourCache) {
        KeyFMap cached = SoftReference.dereference(ourCache.get(key));
        if (cached != null) return cached;

        ourCache.put(key, key);
      }
    }
    return map;
  }

  private static boolean shouldIntern(@NotNull KeyFMap map) {
    return map.size() <= 5;
  }
}

class MapReference extends WeakReference<KeyFMap> {
  private final int myHash;

  MapReference(@NotNull KeyFMap referent) {
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
