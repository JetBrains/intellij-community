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
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.util.containers.IntObjectLinkedMap;
import com.intellij.util.IntSLRUCache;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.PersistentStringEnumerator;
import com.intellij.util.text.StringFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class FileNameCache {
  private static final PersistentStringEnumerator ourNames = FSRecords.getNames();
  @NonNls private static final String EMPTY = "";
  @SuppressWarnings("unchecked") private static final IntSLRUCache<IntObjectLinkedMap.MapEntry<Object>>[] ourNameCache = new IntSLRUCache[16];
  static {
    final int protectedSize = 40000 / ourNameCache.length;
    final int probationalSize = 20000 / ourNameCache.length;
    for(int i = 0; i < ourNameCache.length; ++i) {
      ourNameCache[i] = new IntSLRUCache<IntObjectLinkedMap.MapEntry<Object>>(protectedSize, probationalSize);
    }
  }

  public static int storeName(@NotNull String name) {
    final int idx = FSRecords.getNameId(name);
    cacheData(name, idx);
    return idx;
  }

  @NotNull
  private static IntObjectLinkedMap.MapEntry<Object> cacheData(String name, int id) {
    if (name == null) {
      ourNames.markCorrupted();
      throw new RuntimeException("VFS name enumerator corrupted");
    }

    Object rawName = convertToBytesIfAsciiString(name);
    IntObjectLinkedMap.MapEntry<Object> entry = new IntObjectLinkedMap.MapEntry<Object>(id, rawName);
    final int stripe = calcStripeIdFromNameId(id);
    synchronized (ourNameCache[stripe]) {
      return ourNameCache[stripe].cacheEntry(entry);
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

  private static Object convertToBytesIfAsciiString(@NotNull String name) {
    int length = name.length();
    if (length == 0) return EMPTY;

    if (!IOUtil.isAscii(name)) {
      return name;
    }

    byte[] bytes = new byte[length];
    for (int i = 0; i < length; i++) {
      bytes[i] = (byte)name.charAt(i);
    }
    return bytes;
  }

  @NotNull
  private static IntObjectLinkedMap.MapEntry<Object> getEntry(int id) {
    final int stripe = calcStripeIdFromNameId(id);
    synchronized (ourNameCache[stripe]) {
      IntObjectLinkedMap.MapEntry<Object> entry = ourNameCache[stripe].getCachedEntry(id);
      if (entry != null) {
        return entry;
      }
    }

    return cacheData(FSRecords.getNameByNameId(id), id);
  }

  @NotNull
  public static String getVFileName(int nameId) {
    IntObjectLinkedMap.MapEntry<Object> entry = getEntry(nameId);
    Object name = entry.value;
    if (name instanceof String) {
      //noinspection StringEquality
      return (String)name;
    }

    byte[] bytes = (byte[])name;
    int length = bytes.length;
    char[] chars = new char[length];
    for (int i = 0; i < length; i++) {
      chars[i] = (char)bytes[i];
    }
    return StringFactory.createShared(chars);
  }

  static int compareNameTo(int nameId, @NotNull String name, boolean ignoreCase) {
    IntObjectLinkedMap.MapEntry<Object> entry = getEntry(nameId);
    Object rawName = entry.value;
    if (rawName instanceof String) {
      String thisName = getVFileName(nameId);
      return VirtualFileSystemEntry.compareNames(thisName, name, ignoreCase);
    }

    byte[] bytes = (byte[])rawName;
    int bytesLength = bytes.length;

    int d = bytesLength - name.length();
    if (d != 0) return d;

    return compareBytes(bytes, 0, name, 0, bytesLength, ignoreCase);
  }

  private static int compareBytes(@NotNull byte[] name1, int offset1, @NotNull String name2, int offset2, int len, boolean ignoreCase) {
    for (int i1 = offset1, i2=offset2; i1 < offset1 + len; i1++, i2++) {
      char c1 = (char)name1[i1];
      char c2 = name2.charAt(i2);
      int d = StringUtil.compare(c1, c2, ignoreCase);
      if (d != 0) return d;
    }
    return 0;
  }

  static char[] appendPathOnFileSystem(int nameId, @Nullable VirtualFileSystemEntry parent, int accumulatedPathLength, int[] positionRef) {
    IntObjectLinkedMap.MapEntry<Object> entry = getEntry(nameId);
    Object o = entry.value;
    int nameLength = o instanceof String ? ((String)o).length() : ((byte[])o).length;
    boolean appendSlash = SystemInfo.isWindows && parent == null && nameLength == 2 &&
                          (o instanceof String ? ((String)o).charAt(1) : (char)((byte[])o)[1]) == ':';

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

    if (o instanceof String) {
      positionRef[0] = VirtualFileSystemEntry.copyString(chars, positionRef[0], (String)o);
    }
    else {
      byte[] bytes = (byte[])o;
      int pos = positionRef[0];
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0, len = bytes.length; i < len; i++) {
        chars[pos++] = (char)bytes[i];
      }
      positionRef[0] = pos;
    }

    if (appendSlash) {
      chars[positionRef[0]++] = '/';
    }

    return chars;
  }

}
