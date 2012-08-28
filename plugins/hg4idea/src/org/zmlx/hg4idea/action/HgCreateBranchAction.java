/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zmlx.hg4idea.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.command.HgTagBranchCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.util.HgCustomUtil;

import java.util.List;
import java.util.Set;

public class HgCreateBranchAction extends AnAction {

  public HgCreateBranchAction() {
    super("Create a new branch");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Set<VirtualFile> repositories = HgCustomUtil.getRepositories(e);
    if (repositories.isEmpty()) {
      Messages.showErrorDialog(e.getProject(), "Select at least one repository.", "Can't create branch.");
      return;
    }

    String newBranchName = Messages.showInputDialog(e.getProject(), "Branch name :", "Create a new branch", null);
    // If no or empty name, abort the operation.
    if (newBranchName == null || newBranchName.trim().isEmpty()) {
      return;
    }

    for (VirtualFile repository : repositories) {
      HgTagBranchCommand tagBranchCommand = new HgTagBranchCommand(e.getProject(), repository);

      List<String> branches = HgCustomUtil.getBranchNames(e.getProject(), repository);
      for (String branch : branches) {
        if (branch.equals(newBranchName)) {
          Messages
            .showErrorDialog("A branch named " + newBranchName + " already exists.\nOperation aborted.", "Impossible to create branch");
          return;
        }
      }

      List<String> tags = HgCustomUtil.getTagNames(e.getProject(), repository);
      for (String tag : tags) {
        if (tag.equals(newBranchName)) {
          Messages.showErrorDialog("A tag named " + newBranchName + " already exists.\nOperation aborted.", "Impossible to create branch");
          return;
        }
      }

      HgCommandResult result = HgCustomUtil.createBranch(e.getProject(), repository, newBranchName);
      if (result.getExitValue() != 0) {
        Messages.showErrorDialog(result.getRawError(), "Impossible to create branch");
      }
      else {
        e.getProject().getMessageBus().syncPublisher(HgVcs.BRANCH_TOPIC).update(e.getProject());
        HgCustomUtil.showSuccessNotification(e.getProject(),
                                "Branch '" + newBranchName + "' was successfully created and your<br/>working copy is updated.");
      }
    }
  }
}
