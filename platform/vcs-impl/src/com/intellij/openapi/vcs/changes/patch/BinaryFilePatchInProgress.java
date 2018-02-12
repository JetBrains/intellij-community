/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.diff.impl.patch.BinaryFilePatch;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.shelf.SimpleBinaryContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class BinaryFilePatchInProgress extends CommonBinaryFilePatchInProgress<BinaryFilePatch> {
  protected BinaryFilePatchInProgress(@NotNull BinaryFilePatch patch,
                                      @Nullable Collection<VirtualFile> autoBases,
                                      @NotNull VirtualFile baseDir) {
    super(patch.copy(), autoBases, baseDir);
  }

  @NotNull
  @Override
  protected BinaryContentRevision createNewContentRevision(@NotNull FilePath newFilePath) {
    return new SimpleBinaryContentRevision(newFilePath) {
      @Nullable
      @Override
      public byte[] getBinaryContent() {
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
