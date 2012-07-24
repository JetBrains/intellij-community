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
package com.intellij.openapi.util.io.win32;

import org.intellij.lang.annotations.MagicConstant;

/**
 * Do not use this class directly.
 *
 * @author Dmitry Avdeev
 * @since 12.0
 */
public class FileInfo {
  public static final int BROKEN_SYMLINK = -1;

  public static final int FILE_ATTRIBUTE_READONLY = 0x0001;
  public static final int FILE_ATTRIBUTE_HIDDEN = 0x0002;
  public static final int FILE_ATTRIBUTE_DIRECTORY = 0x0010;
  public static final int FILE_ATTRIBUTE_DEVICE = 0x0040;
  public static final int FILE_ATTRIBUTE_REPARSE_POINT = 0x0400;  // is set only for symlinks

  public String name;
  @MagicConstant(flagsFromClass = FileInfo.class)
  public int attributes;
  public long timestamp;
  public long length;

  public String toString() {
    return name;
  }
}
