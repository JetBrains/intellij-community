/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.patch.apply;

import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchForBaseRevisionTexts;
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryContentRevision;
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFilePatch;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;

import java.io.IOException;

public class ApplyBinaryShelvedFilePatch extends ApplyFilePatchBase<ShelvedBinaryFilePatch> {
  public ApplyBinaryShelvedFilePatch(ShelvedBinaryFilePatch patch) {
    super(patch);
  }

  protected void applyCreate(Project project, final VirtualFile newFile, CommitContext commitContext) throws IOException {
    applyChange(project, newFile, null, null);
  }

  protected Result applyChange(Project project, final VirtualFile fileToPatch, FilePath pathBeforeRename, Getter<CharSequence> baseContents)
    throws IOException {
    try {
      ContentRevision contentRevision = myPatch.getShelvedBinaryFile().createChange(project).getAfterRevision();
      if (contentRevision != null) {
        assert (contentRevision instanceof ShelvedBinaryContentRevision);
        byte[] binaryContent = ((ShelvedBinaryContentRevision)contentRevision).getBinaryContent();
        //it may be new empty binary file
        fileToPatch.setBinaryContent(binaryContent != null ? binaryContent : ArrayUtil.EMPTY_BYTE_ARRAY);
      }
    }
    catch (VcsException e) {
      LOG.error("Couldn't apply shelved binary patch", e);
      return new Result(ApplyPatchStatus.FAILURE) {

        @Override
        public ApplyPatchForBaseRevisionTexts getMergeData() {
          return null;
        }
      };
    }
    return SUCCESS;
  }
}
