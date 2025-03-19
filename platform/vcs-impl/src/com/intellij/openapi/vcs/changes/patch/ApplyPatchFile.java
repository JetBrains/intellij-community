// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @see ApplyPatchUtil#getPatchFile(File)
 */
@ApiStatus.Internal
public final class ApplyPatchFile {
  private final @NotNull VirtualFile file;

  ApplyPatchFile(@NotNull VirtualFile file) { this.file = file; }

  public @NotNull VirtualFile getFile() {
    return file;
  }

  @Override
  public String toString() {
    return "PatchFile{" +
           "file=" + file +
           '}';
  }
}