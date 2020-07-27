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

  @MagicConstant(flags = {SYM_LINK, HIDDEN, READ_ONLY})
  public @interface Flags { }

  public static final FileAttributes BROKEN_SYMLINK = new FileAttributes(SYM_LINK, 0, 0);

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


  public FileAttributes(boolean directory, boolean special, boolean symlink, boolean hidden, long length, long lastModified, boolean writable) {
    this(flags(symlink, hidden, !writable, directory, special), length, lastModified);
  }

  protected FileAttributes(@Flags byte flags, long length, long lastModified) {
    if (flags != -1 && (flags & 0b11100000) != 0) {
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
  private static byte flags(boolean isSymlink, boolean isHidden, boolean isReadOnly, boolean directory, boolean special) {
    @Flags byte flags = 0;
    if (isSymlink) flags |= SYM_LINK;
    if (isHidden) flags |= HIDDEN;
    if (isReadOnly) flags |= READ_ONLY;
    int type_flags = special ? 0b11 : !directory ? 0b01 : 0b10;
    flags |= (type_flags << TYPE_SHIFT);
    return flags;
  }

  public boolean isFile() {
    return ((flags >> TYPE_SHIFT) & 0xff) == 0b01;
  }

  public boolean isDirectory() {
    return ((flags >> TYPE_SHIFT) & 0xff) == 0b10;
  }

  public boolean isSpecial() {
    return ((flags >> TYPE_SHIFT) & 0xff) == 0b11;
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

    if (isSet(flags, SYM_LINK)) sb.append('l');
    if (isSet(flags, HIDDEN)) sb.append('.');

    if (isSet(flags, READ_ONLY)) sb.append(" ro");

    sb.append(" length:").append(length);

    sb.append(" modified:").append(lastModified);

    sb.append(']');
    return sb.toString();
  }

  /**
   * {@code null} means unknown type - typically broken symlink.
   */
  public Type getType() {
    int type = (flags >> TYPE_SHIFT) & 0xff;
    switch (type) {
      case 0b00: return null;
      case 0b01: return Type.FILE;
      case 0b10: return Type.DIRECTORY;
      case 0b11: return Type.SPECIAL;
    }
    throw new IllegalStateException(Integer.toBinaryString(flags));
  }
}
