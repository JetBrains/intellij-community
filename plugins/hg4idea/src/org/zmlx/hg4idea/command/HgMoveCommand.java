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
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;

import java.io.File;

import static com.intellij.openapi.util.io.FileUtil.getRelativePath;
import static java.util.Arrays.asList;

public class HgMoveCommand {

  private final Project project;

  public HgMoveCommand(Project project) {
    this.project = project;
  }

  public HgCommandResult execute(@NotNull VirtualFile repoRoot, @NotNull FilePath sourcePath, @NotNull FilePath targetPath) {
    // hg wlock error may occur while execute this command
    // since hg version 2017 config timeout.warn=time_in_seconds can be added as argument to wait before warn
    File repoFile = VfsUtilCore.virtualToIoFile(repoRoot);
    return new HgCommandExecutor(project).executeInCurrentThread(repoRoot, "rename",
                                                                 asList("--after", getRelativePath(repoFile, sourcePath.getIOFile()),
                                                                        getRelativePath(repoFile, targetPath.getIOFile())));
  }

}
