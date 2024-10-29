// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Override
  protected BinaryContentRevision createNewContentRevision(@NotNull FilePath newFilePath) {
    return new ShelvedBinaryContentRevision(newFilePath, myPatch.getShelvedBinaryFile().SHELVED_PATH);
  }

  @Override
  @NotNull
  protected Change createChange(Project project) {
    return myPatch.getShelvedBinaryFile().createChange(project);
  }
}
