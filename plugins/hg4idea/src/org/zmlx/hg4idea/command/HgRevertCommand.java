// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.command;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class HgRevertCommand {

  private final Project project;

  public HgRevertCommand(Project project) {
    this.project = project;
  }

  //all files should be already chunked
  @Nullable
  public HgCommandResult execute(@NotNull VirtualFile repo, @NotNull Collection<String> files, @Nullable HgRevisionNumber vcsRevisionNumber, boolean backupFile) {
    final List<String> options = new LinkedList<>();
    if (vcsRevisionNumber != null && !HgRevisionNumber.NULL_REVISION_NUMBER.equals(vcsRevisionNumber)) {
      options.add("--rev");
      if (!StringUtil.isEmptyOrSpaces(vcsRevisionNumber.getChangeset())) {
        options.add(vcsRevisionNumber.getChangeset());
      }
      else {
        options.add(vcsRevisionNumber.getRevision());
      }
    }
    if (!backupFile) {
      options.add("--no-backup");
    }
    options.addAll(files);
    return new HgCommandExecutor(project).executeInCurrentThread(repo, "revert", options);
  }
}
