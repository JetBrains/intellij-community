// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.BinaryContentRevision;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryContentRevision;
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFilePatch;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@ApiStatus.Internal
public final class ShelvedBinaryFilePatchInProgress extends CommonBinaryFilePatchInProgress<ShelvedBinaryFilePatch> {
  ShelvedBinaryFilePatchInProgress(ShelvedBinaryFilePatch patch, Collection<VirtualFile> autoBases, VirtualFile baseDir) {
    super(ShelvedBinaryFilePatch.patchCopy(patch), autoBases, baseDir);
  }

  @Override
  protected @NotNull BinaryContentRevision createNewContentRevision(@NotNull FilePath newFilePath) {
    return new ShelvedBinaryContentRevision(newFilePath, myPatch.getShelvedBinaryFile().SHELVED_PATH);
  }

  @Override
  protected @NotNull Change createChange(Project project) {
    return myPatch.getShelvedBinaryFile().createChange(project);
  }
}
