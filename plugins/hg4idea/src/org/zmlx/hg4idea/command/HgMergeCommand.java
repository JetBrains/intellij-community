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
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.HgDeleteModifyPromptHandler;

import java.util.LinkedList;
import java.util.List;

public class HgMergeCommand {

  @NotNull private final Project project;
  @NotNull private final VirtualFile repo;

  private String revision;

  public HgMergeCommand(@NotNull Project project, @NotNull VirtualFile repo) {
    this.project = project;
    this.repo = repo;
  }

  public void setRevision(@NotNull String revision) {
    this.revision = revision;
  }

  @Nullable
  public HgCommandResult execute() {
    HgCommandExecutor commandExecutor = new HgCommandExecutor(project);
    commandExecutor.setShowOutput(true);
    List<String> arguments = new LinkedList<String>();
    if (!StringUtil.isEmptyOrSpaces(revision)) {
      arguments.add("--rev");
      arguments.add(revision);
    }
    final HgCommandResult result =
      commandExecutor.executeInCurrentThread(repo, "merge", arguments, new HgDeleteModifyPromptHandler());
    project.getMessageBus().syncPublisher(HgVcs.BRANCH_TOPIC).update(project, null);
    return result;
  }
}
