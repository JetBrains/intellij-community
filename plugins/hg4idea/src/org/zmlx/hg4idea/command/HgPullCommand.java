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
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.HgRemoteCommandExecutor;
import org.zmlx.hg4idea.util.HgErrorUtil;

import java.util.LinkedList;
import java.util.List;

import static org.zmlx.hg4idea.command.HgCommandExitCode.*;

public class HgPullCommand {

  private final Project project;
  private final VirtualFile repo;

  private String source;
  private String revision;
  private boolean update;
  private boolean rebase;

  public HgPullCommand(Project project, @NotNull VirtualFile repo) {
    this.project = project;
    this.repo = repo;
  }

  public void setRevision(String revision) {
    this.revision = revision;
  }

  public void setUpdate(boolean update) {
    this.update = update;
  }

  public void setRebase(boolean rebase) {
    this.rebase = rebase;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public HgCommandExitCode executeInCurrentThread() {
    List<String> arguments = new LinkedList<>();
    if (update) {
      arguments.add("--update");
    } else if (rebase) {
      arguments.add("--rebase");
    }

    if (!StringUtil.isEmptyOrSpaces(revision)) {
      arguments.add("--rev");
      arguments.add(revision);
    }

    arguments.add(source);

    final HgRemoteCommandExecutor executor = new HgRemoteCommandExecutor(project, source);
    executor.setShowOutput(true);
    HgCommandResult result = executor.executeInCurrentThread(repo, "pull", arguments);
    if (HgErrorUtil.isAuthorizationError(result)) {
      new HgCommandResultNotifier(project)
        .notifyError(result, "Authorization required", "http authorization required for <code>" + source + "</code>");
      return ERROR;
    }
    else if (HgErrorUtil.isAbort(result) || result.getExitValue() > 1) { //if result == null - > isAbort returns true
      new HgCommandResultNotifier(project).notifyError(result, "", "Pull failed");
      return ERROR;
    }
    else if (result.getExitValue() == 1) {
      return UNRESOLVED;
    }
    else {
      project.getMessageBus().syncPublisher(HgVcs.REMOTE_TOPIC).update(project, null);
      return SUCCESS;
    }
  }

}
