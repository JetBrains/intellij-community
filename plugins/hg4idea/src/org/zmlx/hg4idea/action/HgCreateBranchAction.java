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
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.containers.hash.HashSet;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class HgCreateBranchAction extends AnAction {

  public HgCreateBranchAction() {
    super("Create a new branch");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    String newBranchName = Messages.showInputDialog(e.getProject(), "Branch name :", "Create a new branch", null);

    if (newBranchName != null && !newBranchName.trim().isEmpty()) {

      VirtualFile[] roots = (VirtualFile[])e.getDataContext().getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY.getName());
      Set<VirtualFile> uniqueRoots = new HashSet<VirtualFile>();

      for (VirtualFile root : roots) {
        VirtualFile repository = HgUtil.getHgRootOrNull(e.getProject(), root);
        if (repository != null) {
          uniqueRoots.add(repository);
        }
      }

      for (VirtualFile repository : uniqueRoots) {

        List<String> branches = getBranchNames(e.getProject(), repository);
        for (String branch : branches) {
          if (branch.equals(newBranchName)) {
            Messages
              .showErrorDialog("A branch named " + newBranchName + " already exists.\nOperation aborted.", "Impossible to create branch");
            return;
          }
        }

        List<String> tags = getTagNames(e.getProject(), repository);
        for (String tag : tags) {
          if (tag.equals(newBranchName)) {
            Messages
              .showErrorDialog("A tag named " + newBranchName + " already exists.\nOperation aborted.", "Impossible to create branch");
            return;
          }
        }

        HgCommandResult result = createBranch(e.getProject(), repository, newBranchName);
        if (result.getExitValue() != 0) {
          Messages.showErrorDialog(result.getRawError(), "Impossible to create branch");
        }
        else {
          showSuccessNotification(e.getProject(), newBranchName);
        }
      }
    }
  }

  private void showSuccessNotification(Project project, String branchName) {
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);

    project.getMessageBus().syncPublisher(HgVcs.BRANCH_TOPIC).update(project);

    JBPopupFactory.getInstance()
      .createHtmlTextBalloonBuilder("Branch '" + branchName + "' was successfully created and your<br/>working copy is updated.",
                                    MessageType.INFO, null).setFadeoutTime(5000).createBalloon()
      .show(RelativePoint.getCenterOf(HgVcs.getInstance(project).getCurrentBranchStatus().getComponent()), Balloon.Position.atRight);
  }

  private List<String> getBranchNames(Project project, VirtualFile repository) {
    HgCommandResult result = new HgCommandExecutor(project).executeInCurrentThread(repository, "branches", null);
    List<String> nameList = new LinkedList<String>();

    for (String output : result.getOutputLines()) {
      nameList.add(output.substring(0, output.indexOf(" ")));
    }

    return nameList;
  }

  private List<String> getTagNames(Project project, VirtualFile repository) {
    HgCommandResult result = new HgCommandExecutor(project).executeInCurrentThread(repository, "tags", null);
    List<String> nameList = new LinkedList<String>();

    for (String output : result.getOutputLines()) {
      nameList.add(output.substring(0, output.indexOf(" ")));
    }

    return nameList;
  }

  private HgCommandResult createBranch(Project project, VirtualFile repository, String branchName) {
    List<String> args = new LinkedList<String>();
    args.add(branchName);

    return new HgCommandExecutor(project).executeInCurrentThread(repository, "branch", args);
  }
}
