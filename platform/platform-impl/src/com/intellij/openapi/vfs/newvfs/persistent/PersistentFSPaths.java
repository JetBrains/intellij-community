// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public final class PersistentFSPaths {

  private static final @NonNls String ROOTS_START_PREFIX = "roots_";
  static final String VFS_FILES_EXTENSION = System.getProperty("idea.vfs.files.extension", ".dat");

  private final @NotNull Path storagesDir;

  PersistentFSPaths(final @NotNull Path storagesDir) {
    this.storagesDir = storagesDir.toAbsolutePath();
  }

  public @NotNull Path getCorruptionMarkerFile() {
    return storagesDir.resolve("corruption.marker");
  }

  public @NotNull Path getRootsBaseFile() {
    return storagesDir.resolve(ROOTS_START_PREFIX);
  }

  public @NotNull Path getRootsStorage(@NotNull String storageName) {
    return storagesDir.resolve(ROOTS_START_PREFIX + storageName + VFS_FILES_EXTENSION);
  }

  public @NotNull Path storagePath(final @NotNull String storageName) {
    return storagesDir.resolve(storageName + VFS_FILES_EXTENSION);
  }

  public @NotNull Path storagesSubDir(final @NotNull String name) {
    return storagesDir.resolve(name);
  }
}
