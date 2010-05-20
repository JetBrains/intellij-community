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
import com.intellij.vcsUtil.VcsUtil;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.ui.HgUsernamePasswordDialog;
import org.zmlx.hg4idea.HgVcs;

import java.util.LinkedList;
import java.util.List;
import java.net.URISyntaxException;

public class HgPushCommand {

  private final Project project;
  private final VirtualFile repo;
  private final String destination;

  private String revision;

  public HgPushCommand(Project project, @NotNull VirtualFile repo, String destination) {
    this.project = project;
    this.repo = repo;
    this.destination = destination;
  }

  public void setRevision(String revision) {
    this.revision = revision;
  }

  public HgCommandResult execute() {
    List<String> arguments = new LinkedList<String>();
    if (StringUtils.isNotBlank(revision)) {
      arguments.add("-r");
      arguments.add(revision);
    }
    arguments.add(destination);
    HgCommandResult result = HgCommandService.getInstance(project).execute(repo, "push", arguments);
    if (HgErrorUtil.isAbort(result) && HgErrorUtil.isAuthorizationRequiredAbort(result)) {
      try {
        HgUrl hgUrl = new HgUrl(destination);
        if (hgUrl.supportsAuthentication()) {
          HgUsernamePasswordDialog dialog = new HgUsernamePasswordDialog(project);
          dialog.setUsername(hgUrl.getUsername());
          dialog.show();
          if (dialog.isOK()) {
            hgUrl.setUsername(dialog.getUsername());
            hgUrl.setPassword(String.valueOf(dialog.getPassword()));
            arguments.set(arguments.size() - 1, hgUrl.asString());
            result = HgCommandService.getInstance(project).execute(repo, "push", arguments);
          }
        }
      } catch (URISyntaxException e) {
        VcsUtil.showErrorMessage(project, "Invalid destination: " + destination, "Error");
      }
    }

    project.getMessageBus().syncPublisher(HgVcs.OUTGOING_TOPIC).update(project);

    return result;
  }
}
