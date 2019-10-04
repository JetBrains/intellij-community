// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

class FilePath {
  private final String myPath;

  FilePath(@NotNull String path) {
    myPath = path;
  }

  public String getPath() {
    return myPath;
  }

  @Override
  public String toString() {
    return myPath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    return FileUtil.pathsEqual(myPath, ((FilePath)o).myPath);
  }

  @Override
  public int hashCode() {
    return FileUtil.pathHashCode(myPath);
  }
}
