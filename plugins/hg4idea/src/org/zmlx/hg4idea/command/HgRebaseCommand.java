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
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.repo.HgRepository;

import java.util.Arrays;

public class HgRebaseCommand {

  @NotNull private final Project project;
  @NotNull private final HgRepository repo;

  public HgRebaseCommand(@NotNull Project project, @NotNull HgRepository repo) {
    this.project = project;
    this.repo = repo;
  }

  public HgCommandResult continueRebase() {
    HgCommandResult result = new HgCommandExecutor(project).executeInCurrentThread(repo.getRoot(), "rebase", Arrays.asList("--continue"), null);
    repo.update();
    project.getMessageBus().syncPublisher(HgVcs.BRANCH_TOPIC).update(project, null);
    return result;
  }

  public HgCommandResult abortRebase() {
    HgCommandResult result = new HgCommandExecutor(project).executeInCurrentThread(repo.getRoot(), "rebase", Arrays.asList("--abort"), null);
    repo.update();
    project.getMessageBus().syncPublisher(HgVcs.BRANCH_TOPIC).update(project, null);
    return result;
  }
}
