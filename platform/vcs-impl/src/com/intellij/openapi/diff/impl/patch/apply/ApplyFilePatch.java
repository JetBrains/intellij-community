// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch.apply;

import com.intellij.openapi.diff.impl.patch.ApplyPatchContext;
import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchForBaseRevisionTexts;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.function.Supplier;

@ApiStatus.Internal
public interface ApplyFilePatch {
  Result SUCCESS = new Result(ApplyPatchStatus.SUCCESS);
  Result FAILURE = new Result(ApplyPatchStatus.FAILURE);

  Result apply(VirtualFile fileToPatch,
               ApplyPatchContext context,
               Project project,
               FilePath pathBeforeRename,
               Supplier<? extends CharSequence> baseContents,
               @Nullable CommitContext commitContext) throws IOException;

  class Result {
    private final ApplyPatchStatus myStatus;

    protected Result(ApplyPatchStatus status) {
      myStatus = status;
    }

    public ApplyPatchForBaseRevisionTexts getMergeData() {
      return null;
    }

    public ApplyPatchStatus getStatus() {
      return myStatus;
    }
  }
}
