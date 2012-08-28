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
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.util.HgCustomUtil;

import java.util.Set;

public class HgCloseBranchAction extends AnAction {

  public HgCloseBranchAction() {
    super("Close the current branch");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Set<VirtualFile> repositories = HgCustomUtil.getRepositories(e);

    if (repositories.size() > 1) {
      Messages.showErrorDialog(e.getProject(), "For security reasons, you must close branches in one repository at a time.",
                               "Cannot close the current branch");
      return;
    }

    for (VirtualFile root : repositories) {
      String currentBranchName = HgCustomUtil.getCurrentBranch(e.getProject(), root);

      if (Messages.showYesNoDialog(e.getProject(), "Do you really want to close the current branch ('" + currentBranchName + "') ?",
                                   "Close branch", "Yes", "No", null) == 0) {

        HgCommandResult closeBranchResult = HgCustomUtil.closeBranch(e.getProject(), root, currentBranchName);
        if (closeBranchResult.getExitValue() != 0) {
          Messages.showErrorDialog(e.getProject(), closeBranchResult.getRawError(), "Cannot close branch.");
          return;
        }

        HgCommandResult hgUpdateResult = HgCustomUtil.updateToDefault(e.getProject(), root);
        if (hgUpdateResult.getExitValue() != 0) {
          Messages.showErrorDialog(e.getProject(), closeBranchResult.getRawError(), "Cannot close branch.");
          return;
        }

        e.getProject().getMessageBus().syncPublisher(HgVcs.BRANCH_TOPIC).update(e.getProject());
        HgCustomUtil.showSuccessNotification(e.getProject(), "Branch '" +
                                                             currentBranchName +
                                                             "' was successfully created and your<br/>working copy is updated to 'default'.");
      }
      else {
        return;
      }
    }
  }


}
