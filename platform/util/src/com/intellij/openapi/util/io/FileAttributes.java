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
package com.intellij.openapi.util.io;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.BitUtil.isSet;

/**
 * @version 11.1
 * @see FileSystemUtil#getAttributes(String)
 */
@SuppressWarnings("OctalInteger")
public final class FileAttributes {
  public enum Type { FILE, DIRECTORY, SPECIAL }

  public static final byte SYM_LINK = 0x01;
  public static final byte HIDDEN = 0x02;
  public static final byte READ_ONLY = 0x04;

  @MagicConstant(flags = {SYM_LINK, HIDDEN, READ_ONLY})
  public @interface Flags { }

  /** @deprecated (to remove in IDEA 14) */ @SuppressWarnings("unused") public static final int OWNER_READ = 0400;
  /** @deprecated (to remove in IDEA 14) */ @SuppressWarnings("unused") public static final int OWNER_WRITE = 0200;
  /** @deprecated (to remove in IDEA 14) */ @SuppressWarnings("unused") public static final int OWNER_EXECUTE = 0100;
  /** @deprecated (to remove in IDEA 14) */ @SuppressWarnings("unused") public static final int GROUP_READ = 0040;
  /** @deprecated (to remove in IDEA 14) */ @SuppressWarnings("unused") public static final int GROUP_WRITE = 0020;
  /** @deprecated (to remove in IDEA 14) */ @SuppressWarnings("unused") public static final int GROUP_EXECUTE = 0010;
  /** @deprecated (to remove in IDEA 14) */ @SuppressWarnings("unused") public static final int OTHERS_READ = 0004;
  /** @deprecated (to remove in IDEA 14) */ @SuppressWarnings("unused") public static final int OTHERS_WRITE = 0002;
  /** @deprecated (to remove in IDEA 14) */ @SuppressWarnings("unused") public static final int OTHERS_EXECUTE = 0001;

  /** @deprecated (to remove in IDEA 14) */
  @SuppressWarnings("unused")
  public @interface Permissions { }

  public static final FileAttributes BROKEN_SYMLINK = new FileAttributes(null, SYM_LINK, 0, 0);

  /**
   * {@code null} means unknown type - typically broken symlink.
   */
  @Nullable
  public final Type type;

  @Flags
  public final byte flags;

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

  /** @deprecated use {@linkplain #isWritable()} (to remove in IDEA 14) */
  @SuppressWarnings("unused") public final int permissions;


  public FileAttributes(boolean directory, boolean special, boolean symlink, boolean hidden, long length, long lastModified, boolean writable) {
    this(type(directory, special), flags(symlink, hidden, !writable), length, lastModified);
  }

  /** @deprecated use {@linkplain #FileAttributes(boolean, boolean, boolean, boolean, long, long, boolean)} (to remove in IDEA 14) */
  @SuppressWarnings("unused")
  public FileAttributes(boolean directory, boolean special, boolean symlink, long length, long lastModified, int permissions) {
    this(type(directory, special), flags(symlink, false, true), length, lastModified);
  }

  @SuppressWarnings("deprecation")
  private FileAttributes(@Nullable Type type, @Flags byte flags, long length, long lastModified) {
    this.type = type;
    this.flags = flags;
    this.length = length;
    this.lastModified = lastModified;
    this.permissions = -1;
  }

  private static Type type(boolean isDirectory, boolean isSpecial) {
    return isDirectory ? Type.DIRECTORY : isSpecial ? Type.SPECIAL : Type.FILE;
  }

  @Flags
  private static byte flags(boolean isSymlink, boolean isHidden, boolean isReadOnly) {
    @Flags byte flags = 0;
    if (isSymlink) flags |= SYM_LINK;
    if (isHidden) flags |= HIDDEN;
    if (isReadOnly) flags |= READ_ONLY;
    return flags;
  }

  public boolean isFile() {
    return type == Type.FILE;
  }

  public boolean isDirectory() {
    return type == Type.DIRECTORY;
  }

  public boolean isSpecial() {
    return type == Type.SPECIAL;
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
    if (type != that.type) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = type != null ? type.hashCode() : 0;
    result = 31 * result + (int)flags;
    result = 31 * result + (int)(length ^ (length >>> 32));
    result = 31 * result + (int)(lastModified ^ (lastModified >>> 32));
    return result;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();

    sb.append("[type:");
    if (type == Type.FILE) sb.append('f');
    else if (type == Type.DIRECTORY) sb.append('d');
    else if (type == Type.SPECIAL) sb.append('!');
    else sb.append('-');

    if (isSet(flags, SYM_LINK)) sb.append('l');
    if (isSet(flags, HIDDEN)) sb.append('.');

    if (isSet(flags, READ_ONLY)) sb.append(" ro");

    sb.append(" length:").append(length);

    sb.append(" modified:").append(lastModified);

    sb.append(']');
    return sb.toString();
  }
}
