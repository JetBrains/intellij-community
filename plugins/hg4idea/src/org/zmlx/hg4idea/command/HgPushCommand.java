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
import org.zmlx.hg4idea.execution.HgCommandResultHandler;

import java.util.LinkedList;
import java.util.List;

public class HgPushCommand {

  private final Project myProject;
  private final VirtualFile myRepo;
  private final String myDestination;

  private String myRevision;
  private boolean myForce;
  private HgTagBranch myBranch;
  private boolean myIsNewBranch;

  public HgPushCommand(Project project, @NotNull VirtualFile repo, String destination) {
    myProject = project;
    myRepo = repo;
    myDestination = destination;
  }

  public void setRevision(String revision) {
    myRevision = revision;
  }

  public void setForce(boolean force) {
    myForce = force;
  }

  public void setBranch(HgTagBranch branch) {
    myBranch = branch;
  }

  public void setIsNewBranch(boolean isNewBranch) {
      myIsNewBranch = isNewBranch;
    }

  public void execute(final HgCommandResultHandler resultHandler) {
    final List<String> arguments = new LinkedList<String>();
    if (!StringUtil.isEmptyOrSpaces(myRevision)) {
      arguments.add("-r");
      arguments.add(myRevision);
    }
    if (myBranch != null) {
      if (myIsNewBranch) {
        arguments.add("--new-branch");
      }
      else {
        arguments.add("-b");
        arguments.add(myBranch.getName());
      }
    }
    if (myForce) {
      arguments.add("-f");
    }
    arguments.add(myDestination);

    final HgCommandExecutor executor = new HgCommandExecutor(myProject, myDestination);
    executor.setShowOutput(true);
    executor.execute(myRepo, "push", arguments, new HgCommandResultHandler() {
      @Override
      public void process(@Nullable HgCommandResult result) {
        if (!myProject.isDisposed()) {
          myProject.getMessageBus().syncPublisher(HgVcs.REMOTE_TOPIC).update(myProject, null);
        }
        resultHandler.process(result);
      }
    });
  }

  public VirtualFile getRepo() {
    return myRepo;
  }
}
