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

/**
 * @version 11.1
 * @see FileSystemUtil#getAttributes(String)
 */
@SuppressWarnings("OctalInteger")
public final class FileAttributes {
  public static final int OWNER_READ = 0400;
  public static final int OWNER_WRITE = 0200;
  public static final int OWNER_EXECUTE = 0100;
  public static final int GROUP_READ = 0040;
  public static final int GROUP_WRITE = 0020;
  public static final int GROUP_EXECUTE = 0010;
  public static final int OTHERS_READ = 0004;
  public static final int OTHERS_WRITE = 0002;
  public static final int OTHERS_EXECUTE = 0001;

  @MagicConstant(flags = {
    OWNER_READ, OWNER_WRITE, OWNER_EXECUTE, GROUP_READ, GROUP_WRITE, GROUP_EXECUTE, OTHERS_READ, OTHERS_WRITE, OTHERS_EXECUTE
  })
  public @interface Permissions { }

  public final boolean isFile;
  public final boolean isDirectory;
  public final boolean isSymlink;
  public final boolean isSpecial;

  /** In bytes, 0 for symlinks and special files. */
  public final long length;

  /** In milliseconds (note that actual resolution may be less accurate). */
  public final long lastModified;

  /** UNIX permission bits (for Windows only OWNER_WRITE matters and OWNER_READ/EXECUTE are always set), or -1 if not supported. */
  @Permissions
  public final int permissions;

  // todo: hidden flag (?)

  public FileAttributes(final boolean isDirectory,
                        final boolean isSymlink,
                        final boolean isSpecial,
                        final long length,
                        final long lastModified,
                        final boolean writable) {
    this(isDirectory, isSymlink, isSpecial, length, lastModified, OWNER_READ | OWNER_EXECUTE | (writable ? OWNER_WRITE : 0));
  }

  public FileAttributes(final boolean isDirectory,
                        final boolean isSymlink,
                        final boolean isSpecial,
                        final long length,
                        final long lastModified,
                        @Permissions final int permissions) {
    this.isFile = !isDirectory && !isSymlink && !isSpecial;
    this.isDirectory = isDirectory;
    this.isSymlink = isSymlink;
    this.isSpecial = isSpecial;
    this.length = isSymlink || isSpecial ? 0 : length;
    this.lastModified = lastModified;
    this.permissions = permissions;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final FileAttributes that = (FileAttributes)o;

    if (isDirectory != that.isDirectory) return false;
    if (isFile != that.isFile) return false;
    if (isSpecial != that.isSpecial) return false;
    if (isSymlink != that.isSymlink) return false;
    if (lastModified != that.lastModified) return false;
    if (permissions != that.permissions) return false;
    if (length != that.length) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = (isFile ? 1 : 0);
    result = 31 * result + (isDirectory ? 1 : 0);
    result = 31 * result + (isSymlink ? 1 : 0);
    result = 31 * result + (isSpecial ? 1 : 0);
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
      if (isFile) sb.append('f');
      if (isDirectory) sb.append('d');
      if (isSymlink) sb.append('l');
      if (isSpecial) sb.append('!');

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
