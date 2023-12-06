// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.impl;

import org.jetbrains.annotations.NotNull;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class JarResourceRoot extends ResourceRoot {
  private final Path myJarFile;

  JarResourceRoot(Path jarFile) {
    myJarFile = jarFile;
  }

  @Override
  public InputStream openFile(@NotNull String relativePath) throws IOException {
    ZipFile jarFile = new ZipFile(myJarFile.toFile());
    try {
      ZipEntry entry = jarFile.getEntry(relativePath);
      if (entry != null) {
        return new FilterInputStream(jarFile.getInputStream(entry)) {
          @Override
          public void close() throws IOException {
            super.close();
            jarFile.close();
          }
        };
      }
      return null;
    }
    catch (IOException e) {
      jarFile.close();
      throw e;
    }
  }

  @Override
  public @NotNull Path getRootPath() {
    return myJarFile;
  }

  @Override
  public String toString() {
    return "JarResourceRoot{jarFile=" + myJarFile + '}';
  }
}
