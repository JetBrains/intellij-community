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

import com.intellij.openapi.diff.impl.patch.ApplyPatchContext;
import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchForBaseRevisionTexts;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public interface ApplyFilePatch {
  Result SUCCESS = new Result(ApplyPatchStatus.SUCCESS);
  Result FAILURE = new Result(ApplyPatchStatus.FAILURE);

  Result apply(VirtualFile fileToPatch,
               ApplyPatchContext context,
               Project project,
               FilePath pathBeforeRename,
               Getter<CharSequence> baseContents,
               CommitContext commitContext) throws IOException;

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
