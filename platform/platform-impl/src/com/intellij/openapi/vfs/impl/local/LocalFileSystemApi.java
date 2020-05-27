// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * We have two roles behind the {@link LocalFileSystem} class so far.
 * The first role - is an API entry point to exchange some local files
 * with a {@link VirtualFile}. Secondly, it is implementation of the
 * filesystem itself used from {@link VirtualFile} implementations.
 * <br />
 * We'd like to provide a transparent {@link VirtualFileLookupService}
 * to implement all major VirtualFile lookup needs from one hand. From
 * the other hand, we'd like to separate platform service from the
 * filesystem implementation.
 */
public abstract class LocalFileSystemApi extends LocalFileSystem {
  @NotNull
  private VirtualFileLookupImpl getLookup() {
    return VirtualFileLookupServiceImpl.getInstance().newLookup(this);
  }

  @Nullable
  @Override
  public VirtualFile findFileByPathIfCached(@NotNull String path) {
    return getLookup().onlyIfCached().fromPath(path);
  }

  @Nullable
  @Override
  public VirtualFile findFileByPath(@NotNull String path) {
    return getLookup().fromPath(path);
  }

  @Nullable
  @Override
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return getLookup().withRefresh().fromPath(path);
  }

  @Nullable
  @Override
  public VirtualFile findFileByIoFile(@NotNull File file) {
    return getLookup().fromIoFile(file);
  }

  @Nullable
  @Override
  public VirtualFile refreshAndFindFileByIoFile(@NotNull File file) {
    return getLookup().withRefresh().fromIoFile(file);
  }
}
