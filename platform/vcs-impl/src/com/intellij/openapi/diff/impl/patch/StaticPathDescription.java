// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class StaticPathDescription implements PathDescription {
  private final Path myPath;
  private final boolean myIsDirectory;
  private final long myLastModified;

  StaticPathDescription(boolean isDirectory, @NotNull Path path) {
    myIsDirectory = isDirectory;

    long lastModified;
    try {
      lastModified = Files.getLastModifiedTime(path).toMillis();
    }
    catch (IOException e) {
      lastModified = 0;
    }
    myLastModified = lastModified;

    myPath = path;
  }

  @Override
  public @NotNull Path getPath() {
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
