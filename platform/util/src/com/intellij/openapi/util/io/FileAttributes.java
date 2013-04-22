/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
  public static final byte HIDDEN = 0x20;

  @MagicConstant(flags = {SYM_LINK, HIDDEN})
  public @interface Flags { }

  public static final int OWNER_READ = 0400;
  public static final int OWNER_WRITE = 0200;
  public static final int OWNER_EXECUTE = 0100;
  public static final int GROUP_READ = 0040;
  public static final int GROUP_WRITE = 0020;
  public static final int GROUP_EXECUTE = 0010;
  public static final int OTHERS_READ = 0004;
  public static final int OTHERS_WRITE = 0002;
  public static final int OTHERS_EXECUTE = 0001;

  @MagicConstant(flags = {OWNER_READ, OWNER_WRITE, OWNER_EXECUTE, GROUP_READ, GROUP_WRITE, GROUP_EXECUTE, OTHERS_READ, OTHERS_WRITE, OTHERS_EXECUTE})
  public @interface Permissions { }

  public static final FileAttributes BROKEN_SYMLINK = new FileAttributes(null, SYM_LINK, 0, 0, -1);

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

  /**
   * UNIX permission bits (for Windows only OWNER_WRITE matters and OWNER_READ/EXECUTE are always set), or {@code -1} if not supported.<br/>
   * For symlinks - permissions of a link target.
   */
  @Permissions
  public final int permissions;


  public FileAttributes(final boolean isDirectory,
                        final boolean isSpecial,
                        final boolean isSymlink,
                        final boolean isHidden,
                        final long length,
                        final long lastModified,
                        final boolean isWritable) {
    this(type(isDirectory, isSpecial), flags(isSymlink, isHidden), length, lastModified,
         OWNER_READ | OWNER_EXECUTE | (isWritable ? OWNER_WRITE : 0));
  }

  public FileAttributes(final boolean isDirectory,
                        final boolean isSpecial,
                        final boolean isSymlink,
                        final long length,
                        final long lastModified,
                        @Permissions final int permissions) {
    this(type(isDirectory, isSpecial), flags(isSymlink, false), length, lastModified, permissions);
  }

  private FileAttributes(@Nullable final Type type,
                         @Flags final byte flags,
                         final long length,
                         final long lastModified,
                         @Permissions final int permissions) {
    this.type = type;
    this.flags = flags;
    this.length = length;
    this.lastModified = lastModified;
    this.permissions = permissions;
  }

  private static Type type(final boolean isDirectory, final boolean isSpecial) {
    return isDirectory ? Type.DIRECTORY : isSpecial ? Type.SPECIAL : Type.FILE;
  }

  @Flags
  private static byte flags(final boolean isSymlink, final boolean isHidden) {
    @Flags byte flags = 0;
    if (isSymlink) flags |= SYM_LINK;
    if (isHidden) flags |= HIDDEN;
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
    return permissions == -1 || isSet(permissions, OWNER_WRITE) || isSet(permissions, GROUP_WRITE) || isSet(permissions, OTHERS_WRITE);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FileAttributes that = (FileAttributes)o;
    if (flags != that.flags) return false;
    if (lastModified != that.lastModified) return false;
    if (length != that.length) return false;
    if (permissions != that.permissions) return false;
    if (type != that.type) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = type != null ? type.hashCode() : 0;
    result = 31 * result + (int)flags;
    result = 31 * result + (int)(length ^ (length >>> 32));
    result = 31 * result + (int)(lastModified ^ (lastModified >>> 32));
    result = 31 * result + permissions;
    return result;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append("[type:");
    if (type == Type.FILE) sb.append('f');
    else if (type == Type.DIRECTORY) sb.append('d');
    else if (type == Type.SPECIAL) sb.append('!');
    else sb.append('-');

    if (isSet(flags, SYM_LINK)) sb.append('l');
    if (isSet(flags, HIDDEN)) sb.append('.');

    sb.append(" length:").append(length);

    sb.append(" modified:").append(lastModified);

    if (permissions != -1) {
      sb.append(" mode:").append(Integer.toOctalString(permissions));
    }

    sb.append(']');
    return sb.toString();
  }
}
