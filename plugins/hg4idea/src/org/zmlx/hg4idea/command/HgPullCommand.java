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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.HgCommandResultHandler;
import org.zmlx.hg4idea.util.HgErrorUtil;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.LinkedList;
import java.util.List;

public class HgPullCommand {

  private final Project project;
  private final VirtualFile repo;

  private String source;
  private String revision;
  private boolean update = true;
  private boolean rebase = !update;
  private static final Logger LOG = Logger.getInstance(HgPullCommand.class);
  
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

  public void execute() {
    List<String> arguments = new LinkedList<String>();
    if (update) {
      arguments.add("--update");
    } else if (rebase) {
      arguments.add("--rebase");
    }

    if (StringUtils.isNotBlank(revision)) {
      arguments.add("--rev");
      arguments.add(revision);
    }

    arguments.add(source);

    final HgCommandExecutor executor = new HgCommandExecutor(project);
    executor.setShowOutput(true);
    executor.execute(repo, "pull", arguments, new HgCommandResultHandler() {
      @Override
      public void process(@Nullable HgCommandResult result) {
        if (HgErrorUtil.isAuthorizationError(result)) {
          HgUtil.notifyError(project, "Authorization required", "http authorization required for <code>" + source + "</code>");
        } else if (HgErrorUtil.isAbort(result)) {
          if (result != null) {
            LOG.error(new VcsException(result.getRawError()));
          } else {
            LOG.error("Error handing result of 'hg pull' execution.");
          }
        } else {
          project.getMessageBus().syncPublisher(HgVcs.REMOTE_TOPIC).update(project);
        }
      }
    });
  }

}
