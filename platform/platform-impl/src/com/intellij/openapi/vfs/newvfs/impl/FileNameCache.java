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
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.util.IntSLRUCache;
import com.intellij.util.containers.IntObjectLinkedMap;
import com.intellij.util.io.PersistentStringEnumerator;
import com.intellij.util.text.ByteArrayCharSequence;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class FileNameCache {
  private static final PersistentStringEnumerator ourNames = FSRecords.getNames();
  @SuppressWarnings("unchecked") private static final IntSLRUCache<IntObjectLinkedMap.MapEntry<CharSequence>>[] ourNameCache = new IntSLRUCache[16];
  static {
    final int protectedSize = 40000 / ourNameCache.length;
    final int probationalSize = 20000 / ourNameCache.length;
    for(int i = 0; i < ourNameCache.length; ++i) {
      ourNameCache[i] = new IntSLRUCache<IntObjectLinkedMap.MapEntry<CharSequence>>(protectedSize, probationalSize);
    }
  }

  public static int storeName(@NotNull String name) {
    final int idx = FSRecords.getNameId(name);
    cacheData(name, idx, calcStripeIdFromNameId(idx));
    return idx;
  }

  @NotNull
  private static IntObjectLinkedMap.MapEntry<CharSequence> cacheData(String name, int id, int stripe) {
    if (name == null) {
      ourNames.markCorrupted();
      throw new RuntimeException("VFS name enumerator corrupted");
    }

    CharSequence rawName = ByteArrayCharSequence.convertToBytesIfAsciiString(name);
    IntObjectLinkedMap.MapEntry<CharSequence> entry = new IntObjectLinkedMap.MapEntry<CharSequence>(id, rawName);
    IntSLRUCache<IntObjectLinkedMap.MapEntry<CharSequence>> cache = ourNameCache[stripe];
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (cache) {
      return cache.cacheEntry(entry);
    }
  }

  private static int calcStripeIdFromNameId(int id) {
    int h = id;
    h -= (h<<6);
    h ^= (h>>17);
    h -= (h<<9);
    h ^= (h<<4);
    h -= (h<<3);
    h ^= (h<<10);
    h ^= (h>>15);
    return h % ourNameCache.length;
  }

  @NotNull
  public static CharSequence getVFileName(int nameId) {
    assert nameId > 0;
    final int stripe = calcStripeIdFromNameId(nameId);
    IntSLRUCache<IntObjectLinkedMap.MapEntry<CharSequence>> cache = ourNameCache[stripe];
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (cache) {
      IntObjectLinkedMap.MapEntry<CharSequence> entry = cache.getCachedEntry(nameId);
      if (entry != null) {
        return entry.value;
      }
    }

    return cacheData(FSRecords.getNameByNameId(nameId), nameId, stripe).value;
  }
}
