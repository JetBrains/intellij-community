// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.CharSequenceWithStringHash;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.WeakInterner;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * Memory-efficient CharSequence interner which stores file paths as an array of FileNameCache-enumerated ints
 */
public class FilePathInterner extends WeakInterner<CharSequence> {
  @NotNull
  @Override
  public CharSequence intern(@NotNull CharSequence path) {
    List<String> names = StringUtil.split(path.toString(), "/");
    int[] nameIds = names.stream().mapToInt(name -> FileNameCache.storeName(name)).toArray();
    return super.intern(new FileSeparatedCharSequence(nameIds));
  }

  private static class FileSeparatedCharSequence implements CharSequenceWithStringHash {
    private final int[] nameIds;
    private transient int hash;

    private FileSeparatedCharSequence(@NotNull int[] nameIds) {
      this.nameIds = nameIds;
    }

    @Override
    public int length() {
      int length = 0;
      for (int nameId : nameIds) {
        CharSequence name = FileNameCache.getVFileName(nameId);
        length += name.length();
      }
      return length + nameIds.length-1;
    }

    @Override
    public char charAt(int index) {
      for (int n = 0; n < nameIds.length; n++) {
        if (n>0) {
          if (index == 0) return '/';
          index --;
        }
        int nameId = nameIds[n];
        CharSequence name = FileNameCache.getVFileName(nameId);
        if (index < name.length()) {
          return name.charAt(index);
        }
        index -= name.length();
      }
      throw new IndexOutOfBoundsException();
    }

    @Override
    public CharSequence subSequence(int start, int end) {
      return toString().substring(start, end);
    }

    @NotNull
    @Override
    public String toString() {
      StringBuilder b = new StringBuilder(length() + nameIds.length-1);
      for (int n = 0; n < nameIds.length; n++) {
        if (n > 0 || !SystemInfo.isWindows) {
          b.append('/');
        }
        int nameId = nameIds[n];
        CharSequence name = FileNameCache.getVFileName(nameId);
        b.append(name);
      }
      return b.toString();
    }

    @Override
    public int hashCode() {
      int h = hash;
      if (h == 0) {
        for (int n = 0; n < nameIds.length; n++) {
          if (n > 0 || !SystemInfo.isWindows) {
            h = h * 31 + '/';
          }
          int nameId = nameIds[n];
          CharSequence name = FileNameCache.getVFileName(nameId);
          for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            h = h * 31 + c;
          }
        }
        hash = h;
        return h;
      }
      return h;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof FileSeparatedCharSequence)) return false;
      FileSeparatedCharSequence other = (FileSeparatedCharSequence)obj;
      return Arrays.equals(nameIds, other.nameIds);
    }
  }
}
