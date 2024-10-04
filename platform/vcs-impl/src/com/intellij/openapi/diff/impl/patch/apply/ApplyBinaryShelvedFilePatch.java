// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch.apply;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFile;
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFilePatch;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class ApplyBinaryShelvedFilePatch extends ApplyFilePatchBase<ShelvedBinaryFilePatch> {
  public ApplyBinaryShelvedFilePatch(ShelvedBinaryFilePatch patch) {
    super(patch);
  }

  @Override
  protected void applyCreate(@NotNull Project project,
                             @NotNull VirtualFile newFile,
                             @Nullable CommitContext commitContext) throws IOException {
    writeContentTo(newFile);
  }

  @Override
  protected Result applyChange(@NotNull Project project,
                               @NotNull VirtualFile fileToPatch,
                               @NotNull FilePath pathBeforeRename,
                               @Nullable Supplier<? extends CharSequence> baseContents) throws IOException {
    writeContentTo(fileToPatch);
    return SUCCESS;
  }

  private void writeContentTo(@NotNull VirtualFile fileToPatch) throws IOException {
    ShelvedBinaryFile shelvedBinaryFile = myPatch.getShelvedBinaryFile();
    if (shelvedBinaryFile.SHELVED_PATH == null) {
      fileToPatch.delete(this);
    }
    else {
      File fromFile = new File(shelvedBinaryFile.SHELVED_PATH);
      File toFile = VfsUtilCore.virtualToIoFile(fileToPatch);
      FileUtil.copyContent(fromFile, toFile);
      VfsUtil.markDirty(false, false, fileToPatch);
    }
  }
}
