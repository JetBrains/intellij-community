// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.util.keyFMap.KeyFMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;

final class UserDataInterner {
  private static final int MAX_SIZE = 20;
  private static final ObjectLinkedOpenHashSet<MapReference> cache = new ObjectLinkedOpenHashSet<>(MAX_SIZE + 1);

  static @NotNull KeyFMap internUserData(@NotNull KeyFMap map) {
    if (!shouldIntern(map)) {
      return map;
    }

    MapReference key = new MapReference(map);
    synchronized (cache) {
      MapReference internedKey = cache.addOrGet(key);
      if (internedKey == key) {
        // was not present - no need to move to last, remove old items
        while (cache.size() > MAX_SIZE) {
          cache.removeFirst();
        }
        return map;
      }
      else {
        // use the interned map if still actual
        KeyFMap cached = internedKey.get();
        if (cached == null) {
          // weak reference was collected - remove item
          cache.remove(internedKey);
          cache.add(key);
          return map;
        }
        else {
          // was present and actual - move to last
          cache.addAndMoveToLast(internedKey);
          return cached;
        }
      }
    }
  }

  private static boolean shouldIntern(@NotNull KeyFMap map) {
    return map.size() <= 5;
  }
}

final class MapReference extends WeakReference<KeyFMap> {
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
