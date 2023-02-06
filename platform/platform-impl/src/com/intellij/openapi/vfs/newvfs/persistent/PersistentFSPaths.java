// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public final class PersistentFSPaths {
  /** @deprecated remove as soon as {@link VfsDependentEnum} class is removed */
  @Deprecated
  @NonNls private static final String DEPENDENT_PERSISTENT_LIST_START_PREFIX = "vfs_enum_";

  @NonNls private static final String ROOTS_START_PREFIX = "roots_";
  static final String VFS_FILES_EXTENSION = System.getProperty("idea.vfs.files.extension", ".dat");

  @NotNull
  private final Path storagesDir;

  PersistentFSPaths(final @NotNull Path storagesDir) {
    this.storagesDir = storagesDir.toAbsolutePath();
  }

  public @NotNull Path getCorruptionMarkerFile() {
    return storagesDir.resolve("corruption.marker");
  }

  //@NotNull File getVfsEnumBaseFile() {
  //  return new File(new File(myCachesDir), DEPENDENT_PERSISTENT_LIST_START_PREFIX);
  //}

  /** @deprecated remove as soon as {@link VfsDependentEnum} is removed */
  @Deprecated
  public @NotNull Path getVfsEnumFile(@NotNull String enumName) {
    return storagesDir.resolve(DEPENDENT_PERSISTENT_LIST_START_PREFIX + enumName + VFS_FILES_EXTENSION);
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
}
