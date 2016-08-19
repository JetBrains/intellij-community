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
import org.zmlx.hg4idea.execution.HgCommandException;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResultHandler;
import org.zmlx.hg4idea.repo.HgRepositoryManager;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.ArrayList;
import java.util.List;

public class HgTagCreateCommand {

  private final Project project;
  private final VirtualFile repo;
  private final String tagName;
  private final String revisionNumberOrHash;

  public HgTagCreateCommand(Project project, @NotNull VirtualFile repo, String tagName, String revisionNumberOrHash) {
    this.project = project;
    this.repo = repo;
    this.tagName = tagName;
    this.revisionNumberOrHash = revisionNumberOrHash;
  }

  public void executeAsynchronously(HgCommandResultHandler resultHandler) throws HgCommandException {
    if (StringUtil.isEmptyOrSpaces(tagName)) {
      throw new HgCommandException("tag name is empty");
    }
    List<String> arguments = new ArrayList<>();
    arguments.add(tagName);
    if (!StringUtil.isEmptyOrSpaces(revisionNumberOrHash)) {
      arguments.add("--rev");
      arguments.add(revisionNumberOrHash);
    }
    new HgCommandExecutor(project).execute(repo, "tag", arguments, resultHandler);
    if (!project.isDisposed()) {
      HgRepositoryManager manager = HgUtil.getRepositoryManager(project);
      manager.updateRepository(repo);
    }
  }
}
