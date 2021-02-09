// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;

final class PersistentFSPaths {
  @NonNls private static final String DEPENDENT_PERSISTENT_LIST_START_PREFIX = "vfs_enum_";
  static final String VFS_FILES_EXTENSION = System.getProperty("idea.vfs.files.extension", ".dat");

  @NotNull
  private final String myCachesDir;

  PersistentFSPaths(@NotNull String dir) {
    myCachesDir = dir;
  }

  @NotNull File getCorruptionMarkerFile() {
    return new File(new File(myCachesDir), "corruption.marker");
  }

  @NotNull File getVfsEnumBaseFile() {
    return new File(new File(myCachesDir), DEPENDENT_PERSISTENT_LIST_START_PREFIX);
  }

  @NotNull File getVfsEnumFile(@NotNull String enumName) {
    return new File(new File(myCachesDir), DEPENDENT_PERSISTENT_LIST_START_PREFIX + enumName + VFS_FILES_EXTENSION);
  }

  Path getRootsFile() {
    if (FSRecords.ourStoreRootsSeparately) return new File(myCachesDir).getAbsoluteFile().toPath().resolve("roots" + VFS_FILES_EXTENSION);
    else return null;
  }
}
