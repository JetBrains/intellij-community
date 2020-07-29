// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.io;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileFilter;

public final class FileFilters {
  private FileFilters() { }

  /**
   * @return file filter which accepts files and directories with the given {@code extension}
   */
  public static FileFilter withExtension(@NotNull final String extension) {
    return new FileFilter() {
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
      public boolean accept(File pathname) {
        return FileUtilRt.extensionEquals(pathname.getPath(), extension) && pathname.isFile();
      }
    };
  }
}