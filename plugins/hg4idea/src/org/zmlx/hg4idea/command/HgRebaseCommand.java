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

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.repo.HgRepository;

import java.util.List;

public class HgRebaseCommand {

  @NotNull private final Project project;
  @NotNull private final HgRepository repo;

  public HgRebaseCommand(@NotNull Project project, @NotNull HgRepository repo) {
    this.project = project;
    this.repo = repo;
  }

  @Nullable
  public HgCommandResult startRebase() {
    return performRebase(ArrayUtil.EMPTY_STRING_ARRAY);
  }

  @Nullable
  public HgCommandResult continueRebase() {
    return performRebase("--continue");
  }

  @Nullable
  public HgCommandResult abortRebase() {
    return performRebase("--abort");
  }

  @Nullable
  private HgCommandResult performRebase(@NotNull String... args) {
    AccessToken token = DvcsUtil.workingTreeChangeStarted(project);
    try {
      final List<String> list = ContainerUtil.newArrayList(args);
      list.add("--config");
      list.add("extensions.rebase=");
      HgCommandResult result =
        new HgCommandExecutor(project)
          .executeInCurrentThread(repo.getRoot(), "rebase", list);
      repo.update();
      return result;
    }
    finally {
      DvcsUtil.workingTreeChangeFinished(project, token);
    }
  }
}
