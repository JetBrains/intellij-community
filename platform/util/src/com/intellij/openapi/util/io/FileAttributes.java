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
package com.intellij.openapi.util.io;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.BitUtil.isSet;

/**
 * @version 11.1
 * @see FileSystemUtil#getAttributes(String)
 */
public class FileAttributes {
  public enum Type { FILE, DIRECTORY, SPECIAL }

  public static final byte SYM_LINK = 0x01;
  public static final byte HIDDEN = 0x02;
  public static final byte READ_ONLY = 0x04;
  private static final int TYPE_SHIFT = 3; // two bits encoding Type: 00: unknown, 01: FILE, 10: DIRECTORY, 11: SPECIAL
  private static final int CASE_SENSITIVITY_SHIFT = 5; // two bits encoding case-sensitivity: 00: unknown, 01: case-sensitive, 10: case-insensitive

  @MagicConstant(flags = {SYM_LINK, HIDDEN, READ_ONLY})
  public @interface Flags { }

  public static final FileAttributes BROKEN_SYMLINK = new FileAttributes(SYM_LINK, 0, 0);
  protected static final FileAttributes UNKNOWN = new FileAttributes((byte)-1, 0, 0);

  @Flags
  protected final byte flags;

  /**
   * In bytes, 0 for special files.<br/>
   * For symlinks - length of a link target.
   */
  public final long length;

  /**
   * In milliseconds (actual resolution depends on a file system and may be less accurate).<br/>
   * For symlinks - timestamp of a link target.
   */
  public final long lastModified;

  public FileAttributes(boolean isDirectory, boolean isSpecial, boolean isSymlink, boolean isHidden, long length, long lastModified, boolean isWritable) {
    this(isDirectory, isSpecial, isSymlink, isHidden, length, lastModified, isWritable, CaseSensitivity.UNKNOWN);
  }
  public enum CaseSensitivity {
    SENSITIVE,   // files in this directory are case-sensitive
    INSENSITIVE, // files in this directory are case-insensitive
    UNKNOWN  // case sensitivity is not specified, either because the file is not a directory or because sensitivity is unknown
  }

  /**
   *
   * File attributes
   * @param caseSensitivity flag for this directory case sensitivity.
   *    Directory is considered "case-sensitive" if it's able to contain both files "readme.txt" and "README.TXT" and consider them different.
   *    Examples of case-sensitive directories are regular directories on Linux, directories in case-sensitive volumes on Mac
   *    or NTFS directories configured with "fsutil.exe file setCaseSensitiveInfo" on Windows 10+.
   *    In case of {@code isDirectory==false} the caseSensitivity argument must be {@link CaseSensitivity#UNKNOWN} because case sensitivity configured on a directory level,
   */
  public FileAttributes(boolean isDirectory,
                        boolean isSpecial,
                        boolean isSymlink,
                        boolean isHidden,
                        long length,
                        long lastModified,
                        boolean isWritable,
                        @NotNull CaseSensitivity caseSensitivity) {
    this(flags(isDirectory, isSpecial, isSymlink, isHidden, isWritable, caseSensitivity), length, lastModified);
    if (!isDirectory && caseSensitivity != CaseSensitivity.UNKNOWN) {
      throw new IllegalArgumentException("case-sensitivity for a file must be UNKNOWN, but got: "+this);
    }
  }

  protected FileAttributes(@Flags byte flags, long length, long lastModified) {
    if (flags != -1 && (flags & 0b10000000) != 0) {
      throw new IllegalArgumentException("Invalid flags: " + Integer.toBinaryString(flags));
    }
    this.flags = flags;
    this.length = length;
    this.lastModified = lastModified;
  }

  protected FileAttributes(@NotNull FileAttributes fileAttributes) {
    this.flags = fileAttributes.flags;
    this.length = fileAttributes.length;
    this.lastModified = fileAttributes.lastModified;
  }

  @Flags
  private static byte flags(boolean isDirectory, boolean isSpecial, boolean isSymlink,
                            boolean isHidden,
                            boolean isWritable,
                            @NotNull CaseSensitivity sensitivity) {
    @Flags byte flags = 0;
    if (isSymlink) flags |= SYM_LINK;
    if (isHidden) flags |= HIDDEN;
    if (!isWritable) flags |= READ_ONLY;
    int type_flags = isSpecial ? 0b11 : isDirectory ? 0b10 : 0b01;
    flags |= type_flags << TYPE_SHIFT;
    flags = packSensitivityToFlags(sensitivity, flags);
    return flags;
  }

  @Flags
  private static byte packSensitivityToFlags(@NotNull CaseSensitivity sensitivity, byte flags) {
    int sensitivity_flags = sensitivity == CaseSensitivity.UNKNOWN ? 0 : sensitivity == CaseSensitivity.SENSITIVE ? 1 : 2;
    flags |= sensitivity_flags << CASE_SENSITIVITY_SHIFT;
    return flags;
  }

  public boolean isFile() {
    return ((flags >> TYPE_SHIFT) & 0b11) == 0b01;
  }

  public boolean isDirectory() {
    return ((flags >> TYPE_SHIFT) & 0b11) == 0b10;
  }

  public boolean isSpecial() {
    return !isDirectory() && ((flags >> TYPE_SHIFT) & 0b11) == 0b11;
  }

  public boolean isSymLink() {
    return isSet(flags, SYM_LINK);
  }

  public boolean isHidden() {
    return isSet(flags, HIDDEN);
  }

  public boolean isWritable() {
    return !isSet(flags, READ_ONLY);
  }

  @NotNull
  public CaseSensitivity areChildrenCaseSensitive() {
    if (!isDirectory()) {
      return CaseSensitivity.UNKNOWN;
    }
    int sensitivity_flags = (flags >> CASE_SENSITIVITY_SHIFT) & 0b11;
    switch (sensitivity_flags) {
      case 0: return CaseSensitivity.UNKNOWN;
      case 1: return CaseSensitivity.SENSITIVE;
      case 2: return CaseSensitivity.INSENSITIVE;
    }
    throw new IllegalStateException("Invalid sensitivity flags: "+Integer.toBinaryString(sensitivity_flags));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FileAttributes that = (FileAttributes)o;
    if (flags != that.flags) return false;
    if (lastModified != that.lastModified) return false;
    if (length != that.length) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = flags;
    result = 31 * result + (int)(length ^ (length >>> 32));
    result = 31 * result + (int)(lastModified ^ (lastModified >>> 32));
    return result;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();

    sb.append("[type:");
    sb.append(getType());

    if (isSet(flags, SYM_LINK)) sb.append(" l");
    if (isSet(flags, HIDDEN)) sb.append(" .");
    if (isSet(flags, READ_ONLY)) sb.append(" ro");

    sb.append(" length:").append(length);

    sb.append(" modified:").append(lastModified);
    sb.append(" case sensitive: ").append(areChildrenCaseSensitive());
    sb.append(']');
    return sb.toString();
  }

  /**
   * {@code null} means unknown type - typically broken symlink.
   */
  public Type getType() {
    int type = (flags >> TYPE_SHIFT) & 0b11;
    switch (type) {
      case 0b00: return null;
      case 0b01: return Type.FILE;
      case 0b10: return Type.DIRECTORY;
      case 0b11: return Type.SPECIAL;
    }
    throw new IllegalStateException(Integer.toBinaryString(flags));
  }

  @NotNull
  public FileAttributes withCaseSensitivity(@NotNull CaseSensitivity sensitivity) {
    byte newFlags = (byte)(flags & ~(0b11 << CASE_SENSITIVITY_SHIFT));
    newFlags = packSensitivityToFlags(sensitivity, newFlags);
    return new FileAttributes(newFlags, length, lastModified);
  }
}
