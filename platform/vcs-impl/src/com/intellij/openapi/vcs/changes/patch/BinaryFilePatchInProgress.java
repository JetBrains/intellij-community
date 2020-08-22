// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.diff.impl.patch.BinaryFilePatch;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.shelf.SimpleBinaryContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public final class BinaryFilePatchInProgress extends CommonBinaryFilePatchInProgress<BinaryFilePatch> {
  BinaryFilePatchInProgress(@NotNull BinaryFilePatch patch,
                            @Nullable Collection<VirtualFile> autoBases,
                            @NotNull VirtualFile baseDir) {
    super(patch.copy(), autoBases, baseDir);
  }

  @NotNull
  @Override
  protected BinaryContentRevision createNewContentRevision(@NotNull FilePath newFilePath) {
    return new SimpleBinaryContentRevision(newFilePath) {
      @Override
      public byte @Nullable [] getBinaryContent() {
        return myPatch.getAfterContent();
      }
    };
  }

  @NotNull
  @Override
  protected Change createChange(Project project) {
    ContentRevision before = null;
    ContentRevision after = null;
    if (!myPatch.isNewFile()) {
      before = new CurrentBinaryContentRevision(getFilePath()) {
        @NotNull
        @Override
        public VcsRevisionNumber getRevisionNumber() {
          return new TextRevisionNumber(VcsBundle.message("local.version.title"));
        }
      };
    }
    if (!myPatch.isDeletedFile()) {
      after = createNewContentRevision(getFilePath());
    }
    return new Change(before, after);
  }
}
