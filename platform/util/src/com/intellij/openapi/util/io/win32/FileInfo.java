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
package com.intellij.openapi.util.io.win32;

import com.intellij.openapi.util.io.FileAttributes;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.BitUtil.isSet;

/**
 * Do not use this class directly.
 *
 * @author Dmitry Avdeev
 * @since 12.0
 */
public class FileInfo {
  private static final int BROKEN_SYMLINK = -1;
  private static final int FILE_ATTRIBUTE_READONLY = 0x0001;
  private static final int FILE_ATTRIBUTE_HIDDEN = 0x0002;
  private static final int FILE_ATTRIBUTE_DIRECTORY = 0x0010;
  private static final int FILE_ATTRIBUTE_DEVICE = 0x0040;
  private static final int FILE_ATTRIBUTE_REPARSE_POINT = 0x0400;  // is set only for symlinks

  @SuppressWarnings("UnusedDeclaration") private String name;
  @SuppressWarnings("UnusedDeclaration") private int attributes;
  @SuppressWarnings("UnusedDeclaration") private long timestamp;
  @SuppressWarnings("UnusedDeclaration") private long length;

  public String getName() {
    return name;
  }

  @NotNull
  public FileAttributes toFileAttributes() {
    if (attributes == BROKEN_SYMLINK) return FileAttributes.BROKEN_SYMLINK;

    final boolean isDirectory = isSet(attributes, FILE_ATTRIBUTE_DIRECTORY);
    final boolean isSpecial = isSet(attributes, FILE_ATTRIBUTE_DEVICE);
    final boolean isSymlink = isSet(attributes, FILE_ATTRIBUTE_REPARSE_POINT);
    final boolean isHidden = isSet(attributes, FILE_ATTRIBUTE_HIDDEN);
    final boolean isWritable = !isSet(attributes, FILE_ATTRIBUTE_READONLY);
    final long javaTimestamp = timestamp / 10000 - 11644473600000L;
    return new FileAttributes(isDirectory, isSpecial, isSymlink, isHidden, length, javaTimestamp, isDirectory || isWritable);
  }

  @Override
  public String toString() {
    return name;
  }
}
