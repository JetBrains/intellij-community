// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchReader;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.BinaryContentRevision;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;

@ApiStatus.Internal
public abstract class CommonBinaryFilePatchInProgress<T extends FilePatch> extends AbstractFilePatchInProgress<T> {
  protected CommonBinaryFilePatchInProgress(T patch, Collection<VirtualFile> autoBases, VirtualFile baseDir) {
    super(patch, autoBases, baseDir);
  }

  @Override
  public ContentRevision getNewContentRevision() {
    if (FilePatchStatus.DELETED.equals(myStatus)) return null;

    if (myNewContentRevision != null) return myNewContentRevision;
    if (myPatch.getAfterFileName() != null) {
      FilePath newFilePath = getFilePath();
      myNewContentRevision = createNewContentRevision(newFilePath);
    }
    return myNewContentRevision;
  }

  @NotNull
  protected abstract BinaryContentRevision createNewContentRevision(@NotNull FilePath newFilePath);

  @NotNull
  protected abstract Change createChange(Project project);

  @NotNull
  @Override
  public DiffRequestProducer getDiffRequestProducers(final Project project, final PatchReader baseContents) {
    return new DiffRequestProducer() {
      @Override
      public @NotNull DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
        throws DiffRequestProducerException, ProcessCanceledException {
        return PatchDiffRequestFactory.createDiffRequest(project, createChange(project), getName(), context, indicator);
      }

      @Override
      public @NotNull String getName() {
        File file1 = new File(VfsUtilCore.virtualToIoFile(getBase()), myPatch.getAfterName() == null ? myPatch.getBeforeName() : myPatch.getAfterName());
        return FileUtil.toSystemDependentName(file1.getPath());
      }

      @Override
      public @NotNull FileType getContentType() {
        return getFilePath().getFileType();
      }
    };
  }
}
