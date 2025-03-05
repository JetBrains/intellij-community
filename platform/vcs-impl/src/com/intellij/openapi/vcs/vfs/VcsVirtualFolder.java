// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.vfs;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VcsVirtualFolder extends AbstractVcsVirtualFile {
  private final @Nullable VirtualFile myChild;

  /**
   * @deprecated {@link VcsFileSystem} cannot be overwritten
   */
  @Deprecated
  public VcsVirtualFolder(@NotNull String name, @Nullable VirtualFile child, @NotNull VirtualFileSystem ignored) {
    this(name, child);
  }

  public VcsVirtualFolder(@NotNull String name, @Nullable VirtualFile child) {
    super(name);
    myChild = child;
  }

  public VcsVirtualFolder(@NotNull FilePath path, @Nullable VirtualFile child) {
    super(path);
    myChild = child;
  }

  @Override
  public VirtualFile[] getChildren() {
    return myChild != null ? new VirtualFile[]{myChild} : EMPTY_ARRAY;
  }

  @Override
  public boolean isDirectory() {
    return true;
  }

  @Override
  public byte @NotNull [] contentsToByteArray() {
    throw new RuntimeException(VcsBundle.message("exception.text.internal.error.method.should.not.be.called"));
  }
}
