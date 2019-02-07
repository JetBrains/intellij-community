// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
  protected void applyCreate(Project project, final VirtualFile newFile, CommitContext commitContext) throws IOException {
    newFile.setBinaryContent(myPatch.getAfterContent());
  }

  @Override
  protected Result applyChange(Project project, final VirtualFile fileToPatch, FilePath pathBeforeRename, Getter<? extends CharSequence> baseContents) throws IOException {
    fileToPatch.setBinaryContent(myPatch.getAfterContent());
    return SUCCESS;
  }
}
