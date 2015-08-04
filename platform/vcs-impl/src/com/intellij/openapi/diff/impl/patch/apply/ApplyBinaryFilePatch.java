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

import com.intellij.openapi.diff.impl.patch.BinaryFilePatch;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public class ApplyBinaryFilePatch extends ApplyFilePatchBase<BinaryFilePatch> {
  public ApplyBinaryFilePatch(BinaryFilePatch patch) {
    super(patch);
  }

  protected void applyCreate(Project project, final VirtualFile newFile, CommitContext commitContext) throws IOException {
    newFile.setBinaryContent(myPatch.getAfterContent());
  }

  protected Result applyChange(Project project, final VirtualFile fileToPatch, FilePath pathBeforeRename, Getter<CharSequence> baseContents) throws IOException {
    fileToPatch.setBinaryContent(myPatch.getAfterContent());
    return SUCCESS;
  }
}
