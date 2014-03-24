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

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.util.IntSLRUCache;
import com.intellij.util.containers.IntObjectLinkedMap;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.PersistentStringEnumerator;
import com.intellij.util.text.ByteArrayCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    CharSequence rawName = convertToBytesIfAsciiString(name);
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
  private static CharSequence convertToBytesIfAsciiString(@NotNull String name) {
    int length = name.length();
    if (length == 0) return "";

    if (!IOUtil.isAscii(name)) {
      return new String(name); // So we don't hold whole char[] buffer of a lengthy path on JDK 6
    }

    byte[] bytes = new byte[length];
    for (int i = 0; i < length; i++) {
      bytes[i] = (byte)name.charAt(i);
    }
    return new ByteArrayCharSequence(bytes);
  }

  @NotNull
  private static IntObjectLinkedMap.MapEntry<CharSequence> getEntry(int id) {
    final int stripe = calcStripeIdFromNameId(id);
    IntSLRUCache<IntObjectLinkedMap.MapEntry<CharSequence>> cache = ourNameCache[stripe];
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (cache) {
      IntObjectLinkedMap.MapEntry<CharSequence> entry = cache.getCachedEntry(id);
      if (entry != null) {
        return entry;
      }
    }

    return cacheData(FSRecords.getNameByNameId(id), id, stripe);
  }

  @NotNull
  public static CharSequence getVFileName(int nameId) {
    return getEntry(nameId).value;
  }

  static int compareNameTo(int nameId, @NotNull CharSequence name, boolean ignoreCase) {
    return VirtualFileSystemEntry.compareNames(getEntry(nameId).value, name, ignoreCase);
  }

  @NotNull
  static char[] appendPathOnFileSystem(int nameId, @Nullable VirtualFileSystemEntry parent, int accumulatedPathLength, @NotNull int[] positionRef) {
    IntObjectLinkedMap.MapEntry<CharSequence> entry = getEntry(nameId);
    CharSequence o = entry.value;
    int nameLength = o.length();
    boolean appendSlash = SystemInfo.isWindows && parent == null && nameLength == 2 && o.charAt(1) == ':';

    char[] chars;
    if (parent != null) {
      chars = parent.appendPathOnFileSystem(accumulatedPathLength + 1 + nameLength, positionRef);
      if (positionRef[0] > 0 && chars[positionRef[0] - 1] != '/') {
        chars[positionRef[0]++] = '/';
      }
    }
    else {
      int rootPathLength = accumulatedPathLength + nameLength;
      if (appendSlash) ++rootPathLength;
      chars = new char[rootPathLength];
    }

    positionRef[0] = VirtualFileSystemEntry.copyString(chars, positionRef[0], o);

    if (appendSlash) {
      chars[positionRef[0]++] = '/';
    }

    return chars;
  }

}
