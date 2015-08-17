/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.diff.impl.patch.PatchReader;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.actions.ChangeDiffRequestPresentable;
import com.intellij.openapi.vcs.changes.actions.DiffRequestPresentable;
import com.intellij.openapi.vcs.changes.actions.DiffRequestPresentableProxy;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager.ShelvedBinaryFilePatch;
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryContentRevision;
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFile;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;

public class BinaryFilePatchInProgress extends AbstractFilePatchInProgress<ShelvedBinaryFilePatch> {

  protected BinaryFilePatchInProgress(ShelvedBinaryFilePatch patch,
                                      Collection<VirtualFile> autoBases,
                                      VirtualFile baseDir) {
    super(ShelvedBinaryFilePatch.patchCopy(patch), autoBases, baseDir);
  }

  @Override
  public ContentRevision getNewContentRevision() {
    if (FilePatchStatus.DELETED.equals(myStatus)) return null;

    if (myNewContentRevision != null) return myNewContentRevision;
    if (myPatch.getAfterFileName() != null) {
      final FilePath newFilePath = FilePatchStatus.ADDED.equals(myStatus)
                                   ? VcsUtil.getFilePathOnNonLocal(myIoCurrentBase.getAbsolutePath(),
                                                                   false)
                                   : detectNewFilePathForMovedOrModified();
      myNewContentRevision = new ShelvedBinaryContentRevision(newFilePath, myPatch.getShelvedBinaryFile().SHELVED_PATH);
    }
    return myNewContentRevision;
  }

  @Override
  public DiffRequestPresentable getDiffRequestPresentable(final Project project, final PatchReader baseContents) {
    final ShelvedBinaryFile file = getPatch().getShelvedBinaryFile();
    return new DiffRequestPresentableProxy() {
      @NotNull
      @Override
      public DiffRequestPresentable init() throws VcsException {
        return new ChangeDiffRequestPresentable(project, file.createChange(project));
      }

      @Override
      public String getPathPresentation() {
        final File file1 = new File(VfsUtilCore.virtualToIoFile(getBase()),
                                    file.AFTER_PATH == null ? file.BEFORE_PATH : file.AFTER_PATH);
        return FileUtil.toSystemDependentName(file1.getPath());
      }
    };
  }
}
