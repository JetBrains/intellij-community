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

import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.PersistentStringEnumerator;
import com.intellij.util.text.StringFactory;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class FileNameCache {
  private static final PersistentStringEnumerator ourNames = FSRecords.getNames();
  @NonNls private static final String EMPTY = "";
  @NonNls private static final String[] WELL_KNOWN_SUFFIXES = {"$1.class", "$2.class","Test.java","List.java","tion.java", ".class", ".java", ".html", ".txt", ".xml",".php",".gif",".svn",".css",".js"};
  private static final TIntObjectHashMap<String> ourSuffixCache = new TIntObjectHashMap<String>();
  private static final TIntObjectHashMap<Object> ourNameCache = new TIntObjectHashMap<Object>();
  private static final Object ourCacheLock = new Object();

  @SuppressWarnings("UnusedDeclaration")
  private static final LowMemoryWatcher ourWatcher = LowMemoryWatcher.register(new Runnable() {
    @Override
    public void run() {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println("Clearing VFS name cache");
      synchronized (ourCacheLock) {
        ourSuffixCache.clear();
        ourNameCache.clear();
      }
    }
  });

  static int storeName(@NotNull String name) {
    final int idx = FSRecords.getNameId(name);
    cacheData(name, idx, true);
    return idx;
  }

  @NotNull
  private static Object cacheData(String name, int idx, boolean returnName) {
    if (name == null) {
      ourNames.markCorrupted();
      throw new RuntimeException("VFS name enumerator corrupted");
    }

    String suffix = findSuffix(name);
    Object rawName = convertToBytesIfAsciiString(stripSuffix(name, suffix));

    synchronized (ourCacheLock) {
      ourSuffixCache.put(idx, suffix);
      ourNameCache.put(idx, rawName);
    }

    return returnName ? rawName : suffix;
  }

  private static String stripSuffix(String name, String suffix) {
    return suffix == EMPTY ? name : name.substring(0, name.length() - suffix.length());
  }

  private static String findSuffix(String name) {
    for (String suffix : WELL_KNOWN_SUFFIXES) {
      if (name.endsWith(suffix)) {
        return suffix;
      }
    }
    return EMPTY;
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
  static Object getRawName(int idx) {
    synchronized (ourCacheLock) {
      Object o = ourNameCache.get(idx);
      if (o != null) {
        return o;
      }
    }

    return cacheData(FSRecords.getNameByNameId(idx), idx, true);
  }

  static String getNameSuffix(int idx) {
    synchronized (ourCacheLock) {
      String suffix = ourSuffixCache.get(idx);
      if (suffix != null) {
        return suffix;
      }
    }

    return (String)cacheData(FSRecords.getNameByNameId(idx), idx, false);
  }

  @NotNull
  static String getVFileName(int nameId) {
    Object name = getRawName(nameId);
    String suffix = getNameSuffix(nameId);
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
    Object rawName = getRawName(nameId);
    if (rawName instanceof String) {
      String thisName = getVFileName(nameId);
      return VirtualFileSystemEntry.compareNames(thisName, name, ignoreCase);
    }

    byte[] bytes = (byte[])rawName;
    int bytesLength = bytes.length;

    String suffix = getNameSuffix(nameId);
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


}
