// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch;

import org.jetbrains.annotations.NotNull;

final class StaticPathDescription implements PathDescription {
  private final String myPath;
  private final boolean myIsDirectory;
  private final long myLastModified;

  StaticPathDescription(boolean isDirectory, long lastModified, String path) {
    myIsDirectory = isDirectory;
    myLastModified = lastModified;
    myPath = path;
  }

  @Override
  @NotNull
  public String getPath() {
    return myPath;
  }

  @Override
  public boolean isDirectory() {
    return myIsDirectory;
  }

  @Override
  public long lastModified() {
    return myLastModified;
  }
}
