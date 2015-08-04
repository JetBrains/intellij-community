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

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileFilter;

/**
 * @author nik
 */
public class FileFilters {
  private FileFilters() {
  }

  /**
   * @return file filter which accepts files and directories with the given {@code extension}
   */
  public static FileFilter withExtension(@NotNull final String extension) {
    return new FileFilter() {
      @Override
      public boolean accept(File pathname) {
        return FileUtilRt.extensionEquals(pathname.getPath(), extension);
      }
    };
  }

  /**
   * @return file filter which accepts files with the given {@code extension}
   */
  public static FileFilter filesWithExtension(@NotNull final String extension) {
    return new FileFilter() {
      @Override
      public boolean accept(File pathname) {
        return FileUtilRt.extensionEquals(pathname.getPath(), extension) && pathname.isFile();
      }
    };
  }
}
