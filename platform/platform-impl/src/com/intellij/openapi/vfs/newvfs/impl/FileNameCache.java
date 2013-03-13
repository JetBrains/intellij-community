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
  @NonNls private static final String[] WELL_KNOWN_SUFFIXES = {EMPTY, "$1.class", "$2.class","Test.java","List.java","tion.java", ".class", ".java", ".html", ".txt", ".xml",".php",".gif",".svn",".css",".js"};
  private static final IntSLRUCache<NameSuffixEntry> ourNameCache = new IntSLRUCache<NameSuffixEntry>(40000, 20000);

  static int storeName(@NotNull String name) {
    final int idx = FSRecords.getNameId(name);
    cacheData(name, idx);
    return idx;
  }

  @NotNull
  private static NameSuffixEntry cacheData(String name, int id) {
    if (name == null) {
      ourNames.markCorrupted();
      throw new RuntimeException("VFS name enumerator corrupted");
    }

    byte suffixId = findSuffix(name);
    Object rawName = convertToBytesIfAsciiString(suffixId == 0 ? name : name.substring(0, name.length() -
                                                                                          WELL_KNOWN_SUFFIXES[suffixId].length()));
    NameSuffixEntry entry = new NameSuffixEntry(id, suffixId, rawName);
    if (shouldUseCache()) {
      synchronized (ourNameCache) {
        entry = ourNameCache.cacheEntry(entry);
      }
    }
    return entry;
  }

  private static boolean shouldUseCache() {
    return true;
  }

  private static byte findSuffix(String name) {
    for (byte i = 1; i < WELL_KNOWN_SUFFIXES.length; i++) {
      String suffix = WELL_KNOWN_SUFFIXES[i];
      if (name.endsWith(suffix)) {
        return i;
      }
    }
    return 0;
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
  private static NameSuffixEntry getEntry(int id) {
    if (shouldUseCache()) {
      synchronized (ourNameCache) {
        NameSuffixEntry entry = ourNameCache.getCachedEntry(id);
        if (entry != null) {
          return entry;
        }
      }
    }

    return cacheData(FSRecords.getNameByNameId(id), id);
  }

  @NotNull
  static String getVFileName(int nameId) {
    NameSuffixEntry entry = getEntry(nameId);
    Object name = entry.getRawName();
    String suffix = entry.getSuffix();
    if (name instanceof String) {
      //noinspection StringEquality
      return suffix == EMPTY ? (String)name : name + suffix;
    }

    byte[] bytes = (byte[])name;
    int length = bytes.length;
    char[] chars = new char[length + suffix.length()];
    for (int i = 0; i < length; i++) {
      chars[i] = (char)bytes[i];
    }
    VirtualFileSystemEntry.copyString(chars, length, suffix);
    return StringFactory.createShared(chars);
  }

  static int compareNameTo(int nameId, @NotNull String name, boolean ignoreCase) {
    NameSuffixEntry entry = getEntry(nameId);
    Object rawName = entry.getRawName();
    if (rawName instanceof String) {
      String thisName = getVFileName(nameId);
      return VirtualFileSystemEntry.compareNames(thisName, name, ignoreCase);
    }

    byte[] bytes = (byte[])rawName;
    int bytesLength = bytes.length;

    String suffix = entry.getSuffix();
    int suffixLength = suffix.length();

    int d = bytesLength + suffixLength - name.length();
    if (d != 0) return d;

    d = compareBytes(bytes, 0, name, 0, bytesLength, ignoreCase);
    if (d != 0) return d;

    d = VirtualFileSystemEntry.compareNames(suffix, name, ignoreCase, bytesLength);
    return d;
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
    NameSuffixEntry entry = getEntry(nameId);
    Object o = entry.getRawName();
    String suffix = entry.getSuffix();
    int rawNameLength = o instanceof String ? ((String)o).length() : ((byte[])o).length;
    int nameLength = rawNameLength + suffix.length();
    boolean appendSlash = SystemInfo.isWindows && parent == null && suffix.isEmpty() && rawNameLength == 2 &&
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
    else {
      positionRef[0] = VirtualFileSystemEntry.copyString(chars, positionRef[0], suffix);
    }

    return chars;
  }

  private static class NameSuffixEntry extends IntSLRUCache.CacheEntry<Object> {
    final byte suffixId;

    private NameSuffixEntry(int nameId, byte suffixId, Object rawName) {
      super(nameId, rawName);
      this.suffixId = suffixId;
    }

    Object getRawName() {
      return value;
    }

    public String getSuffix() {
      return WELL_KNOWN_SUFFIXES[suffixId];
    }
  }


}
