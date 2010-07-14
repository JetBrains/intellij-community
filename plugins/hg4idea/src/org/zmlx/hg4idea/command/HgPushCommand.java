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
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;

import java.util.LinkedList;
import java.util.List;

public class HgPushCommand {

  private final Project myProject;
  private final VirtualFile myRepo;
  private final String myDestination;
  private final HgCommandAuthenticator authenticator = new HgCommandAuthenticator();

  private String myRevision;
  private boolean myForce;
  private HgTagBranch myBranch;

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

  public HgCommandResult execute() {
    final List<String> arguments = new LinkedList<String>();
    if (StringUtils.isNotBlank(myRevision)) {
      arguments.add("-r");
      arguments.add(myRevision);
    }
    if (myBranch != null) {
      arguments.add("-b");
      arguments.add(myBranch.getName());
    }
    if (myForce) {
      arguments.add("-f");
    }
    arguments.add(myDestination);

    final HgCommandResult result = authenticator.executeCommandAndAuthenticateIfNecessary(myProject, myRepo, myDestination, "push", arguments);
    myProject.getMessageBus().syncPublisher(HgVcs.OUTGOING_TOPIC).update(myProject);
    return result;
  }

}
