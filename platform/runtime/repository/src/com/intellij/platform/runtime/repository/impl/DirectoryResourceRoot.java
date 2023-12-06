// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.impl;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

class DirectoryResourceRoot extends ResourceRoot {
  private final Path myDirectory;

  DirectoryResourceRoot(Path directory) {
    myDirectory = directory;
  }

  @Override
  public InputStream openFile(@NotNull String relativePath) throws IOException {
    Path file = myDirectory.resolve(relativePath);
    if (!Files.exists(file)) {
      return null;
    }
    return new BufferedInputStream(Files.newInputStream(file));
  }

  @Override
  public @NotNull Path getRootPath() {
    return myDirectory;
  }

  @Override
  public String toString() {
    return "DirectoryResourceRoot{directory=" + myDirectory + "}";
  }
}
