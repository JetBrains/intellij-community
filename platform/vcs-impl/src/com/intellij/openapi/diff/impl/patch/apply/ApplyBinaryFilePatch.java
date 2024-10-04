// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch.apply;

import com.intellij.openapi.diff.impl.patch.BinaryFilePatch;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class ApplyBinaryFilePatch extends ApplyFilePatchBase<BinaryFilePatch> {
  public ApplyBinaryFilePatch(BinaryFilePatch patch) {
    super(patch);
  }

  @Override
  protected void applyCreate(@NotNull Project project,
                             @NotNull VirtualFile newFile,
                             @Nullable CommitContext commitContext) throws IOException {
    newFile.setBinaryContent(myPatch.getAfterContent());
  }

  @Override
  protected Result applyChange(@NotNull Project project,
                               @NotNull VirtualFile fileToPatch,
                               @NotNull FilePath pathBeforeRename,
                               @Nullable Supplier<? extends CharSequence> baseContents) throws IOException {
    fileToPatch.setBinaryContent(myPatch.getAfterContent());
    return SUCCESS;
  }
}
