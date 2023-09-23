// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.CharSequenceWithStringHash;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl;
import com.intellij.util.containers.WeakInterner;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * Memory-efficient CharSequence interner which stores file paths as an array of enumerated ints
 */
public final class FilePathInterner extends WeakInterner<CharSequence> {

  private final FSRecordsImpl vfs;

  /**
   * TODO Ideally, one should always explicitly supply {@link FSRecordsImpl} instance against which nameId
   * are resolved -- because nameId make sense only in the context of particular {@link FSRecordsImpl}.
   * So try to use ctor with explicit arg instead of this one.
   */
  public FilePathInterner() {
    this(FSRecords.getInstance());
  }

  public FilePathInterner(@NotNull FSRecordsImpl vfs) {
    this.vfs = vfs;
  }

  @Override
  public @NotNull CharSequence intern(@NotNull CharSequence path) {
    //FIXME check path is an absolute one (not contain '.', or '..' segments)
    //      also check different path-separators ('/' vs '\')
    List<String> names = StringUtil.split(path.toString(), "/");
    int[] nameIds = names.stream().mapToInt(name -> vfs.getNameId(name)).toArray();
    return nameIds.length == 0 ? "" : super.intern(new FileSeparatedCharSequence(vfs, nameIds));
  }

  private static final class FileSeparatedCharSequence implements CharSequenceWithStringHash {
    private final @NotNull FSRecordsImpl vfs;

    private final int[] nameIds;
    private transient int hash;

    private FileSeparatedCharSequence(@NotNull FSRecordsImpl vfs,
                                      int @NotNull [] nameIds) {
      this.vfs = vfs;
      this.nameIds = nameIds;
    }

    @Override
    public int length() {
      int length = 0;
      for (int nameId : nameIds) {
        CharSequence name = nameByNameId(nameId);
        length += name.length();
      }
      return length + nameIds.length - 1;
    }

    @Override
    public char charAt(int index) {
      for (int n = 0; n < nameIds.length; n++) {
        if (n > 0) {
          if (index == 0) return '/';
          index--;
        }
        int nameId = nameIds[n];
        CharSequence name = nameByNameId(nameId);
        if (index < name.length()) {
          return name.charAt(index);
        }
        index -= name.length();
      }
      throw new IndexOutOfBoundsException();
    }

    @Override
    public @NotNull CharSequence subSequence(int start, int end) {
      return toString().substring(start, end);
    }

    @Override
    public @NotNull String toString() {
      StringBuilder b = new StringBuilder(length() + nameIds.length - 1);
      for (int n = 0; n < nameIds.length; n++) {
        if (n > 0 || !SystemInfo.isWindows) {
          b.append('/');
        }
        int nameId = nameIds[n];
        CharSequence name = nameByNameId(nameId);
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
          CharSequence name = nameByNameId(nameId);
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

    private @NotNull CharSequence nameByNameId(int nameId) {
      return vfs.getNameByNameId(nameId);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof FileSeparatedCharSequence other)) return false;
      return Arrays.equals(nameIds, other.nameIds);
    }
  }
}
