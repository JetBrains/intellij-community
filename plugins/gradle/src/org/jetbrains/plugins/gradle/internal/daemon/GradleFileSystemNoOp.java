// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.internal.daemon;

import org.gradle.internal.file.FileException;
import org.gradle.internal.file.FileMetadata;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;

import java.io.File;

/**
 * This is a no-op implementation of the Gradle's file system.
 * It should not be used for anything other than accessing the Gradle Daemon registry.
 * No operations should be called from the instance, because the methods we use for daemon registry related operations,
 * do not result in calls to FileSystem methods.
 * This class is in Java because it ignores the nullability-contract of Gradle's internal API.
 */
class GradleFileSystemNoOp implements FileSystem {

  static final GradleFileSystemNoOp INSTANCE = new GradleFileSystemNoOp();

  @Override
  public boolean isCaseSensitive() {
    return false;
  }

  @Override
  public boolean canCreateSymbolicLink() {
    return false;
  }

  @Override
  public void createSymbolicLink(File link, File target) throws FileException {

  }

  @Override
  public boolean isSymlink(File suspect) {
    return false;
  }

  @Override
  public void chmod(File file, int mode) throws FileException {
    // noop
  }

  @Override
  public int getUnixMode(File f) throws FileException {
    return 0;
  }

  @Override
  public FileMetadata stat(File f) throws FileException {
    return null;
  }
}
