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

import com.intellij.util.StringBuilderSpinAllocator;
import org.intellij.lang.annotations.MagicConstant;

import static com.intellij.util.BitUtil.isSet;
import static com.intellij.util.BitUtil.notSet;

/**
 * @version 11.1
 * @see FileSystemUtil#getAttributes(String)
 */
@SuppressWarnings("OctalInteger")
public final class FileAttributes {
  public static final int DIRECTORY = 0x0001;
  public static final int SPECIAL = 0x0002;
  public static final int SYM_LINK = 0x0010;
  public static final int HIDDEN = 0x0100;

  @MagicConstant(flags = {DIRECTORY, SPECIAL, SYM_LINK, HIDDEN})
  public @interface FileType { }

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


  /**
   * {@linkplain #DIRECTORY} and {@linkplain #SPECIAL} bits are mutually exclusive, none of them set means a regular file.<br/>
   * {@linkplain #SYM_LINK} bit may be set along with above ones (which then denote a type of a link target).<br/>
   * {@linkplain #HIDDEN} bit may be only set on Windows.
   */
  @FileType
  public final int type;

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
   * UNIX permission bits (for Windows only OWNER_WRITE matters and OWNER_READ/EXECUTE are always set), or -1 if not supported.<br/>
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
    this(type(isDirectory, isSpecial, isSymlink, isHidden), length, lastModified, OWNER_READ | OWNER_EXECUTE | (isWritable ? OWNER_WRITE : 0));
  }

  public FileAttributes(final boolean isDirectory,
                        final boolean isSpecial,
                        final boolean isSymlink,
                        final long length,
                        final long lastModified,
                        @Permissions final int permissions) {
    this(type(isDirectory, isSpecial, isSymlink, false), length, lastModified, permissions);
  }

  private FileAttributes(@FileType final int type,
                         final long length,
                         final long lastModified,
                         @Permissions final int permissions) {
    if (isSet(type, DIRECTORY) && isSet(type, SPECIAL)) {
      throw new IllegalArgumentException("DIRECTORY and SPECIAL bits cannot be set simultaneously");
    }

    this.type = type;
    this.length = length;
    this.lastModified = lastModified;
    this.permissions = permissions;
  }

  @FileType
  private static int type(final boolean isDirectory, final boolean isSpecial, final boolean isSymlink, final boolean isHidden) {
    @FileType int type = 0;
    if (isDirectory) type |= DIRECTORY;
    if (isSpecial) type |= SPECIAL;
    if (isSymlink) type |= SYM_LINK;
    if (isHidden) type |= HIDDEN;
    return type;
  }

  /** Is {@code true} for files and symlinks to files (see {@linkplain #isRegularFile()}). */
  public boolean isFile() {
    return notSet(type, DIRECTORY | SPECIAL);
  }

  /** Is {@code true} for pure regular files only (see {@linkplain #isFile()}). */
  public boolean isRegularFile() {
    return notSet(type, DIRECTORY | SPECIAL | SYM_LINK);
  }

  public boolean isSymLink() {
    return isSet(type, SYM_LINK);
  }

  public boolean isWritable() {
    return permissions == -1 || isSet(permissions, OWNER_WRITE) || isSet(permissions, GROUP_WRITE) || isSet(permissions, OTHERS_WRITE);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final FileAttributes that = (FileAttributes)o;

    if (type != that.type) return false;
    if (lastModified != that.lastModified) return false;
    if (permissions != that.permissions) return false;
    if (length != that.length) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = type;
    result = 31 * result + (int)(length ^ (length >>> 32));
    result = 31 * result + (int)(lastModified ^ (lastModified >>> 32));
    result = 31 * result + permissions;
    return result;
  }

  @Override
  public String toString() {
    final StringBuilder sb = StringBuilderSpinAllocator.alloc();
    try {
      sb.append("[type:");
      if (isSet(type, DIRECTORY)) sb.append('d');
      else if (isSet(type, SPECIAL)) sb.append('!');
      else sb.append('f');
      if (isSet(type, SYM_LINK)) sb.append('l');

      sb.append(" length:").append(length);

      sb.append(" modified:").append(lastModified);

      if (permissions != -1) {
        sb.append(" mode:").append(Integer.toOctalString(permissions));
      }

      sb.append(']');
      return sb.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(sb);
    }
  }
}
