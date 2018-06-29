// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
class NonProjectDirectoryInfo extends DirectoryInfo {
  public static final NonProjectDirectoryInfo IGNORED = new NonProjectDirectoryInfo("ignored") {
    @Override
    public boolean isIgnored() {
      return true;
    }
  };
  public static final NonProjectDirectoryInfo EXCLUDED = new NonProjectDirectoryInfo("excluded from project") {
    @Override
    public boolean isExcluded() {
      return true;
    }
  };
  public static final NonProjectDirectoryInfo NOT_UNDER_PROJECT_ROOTS = new NonProjectDirectoryInfo("not under project roots");
  public static final NonProjectDirectoryInfo INVALID = new NonProjectDirectoryInfo("invalid");
  public static final NonProjectDirectoryInfo NOT_SUPPORTED_VIRTUAL_FILE_IMPLEMENTATION = new NonProjectDirectoryInfo("not supported VirtualFile implementation");
  private final String myDebugName;

  private NonProjectDirectoryInfo(String debugName) {
    myDebugName = debugName;
  }

  @Override
  public boolean isInProject(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public String toString() {
    return "DirectoryInfo: " + myDebugName;
  }

  @Override
  public boolean equals(Object o) {
    return this == o;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  public boolean isIgnored() {
    return false;
  }

  @Nullable
  public VirtualFile getSourceRoot() {
    return null;
  }

  public VirtualFile getLibraryClassRoot() {
    return null;
  }

  @Nullable
  public VirtualFile getContentRoot() {
    return null;
  }

  @Override
  public boolean isInLibrarySource(@NotNull VirtualFile file) {
    return false;
  }

  public boolean isExcluded() {
    return false;
  }

  @Override
  public boolean isExcluded(@NotNull VirtualFile file) {
    return isExcluded();
  }

  @Override
  public boolean isInModuleSource(@NotNull VirtualFile file) {
    return false;
  }

  public Module getModule() {
    return null;
  }

  @Override
  public String getUnloadedModuleName() {
    return null;
  }

  @Nullable
  @Override
  public SourceFolder getSourceRootFolder() {
    return null;
  }
}
