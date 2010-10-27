/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.actions;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.CurrentRevision;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryUtil;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitBranch;
import git4idea.GitFileRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitVcs;
import git4idea.history.GitHistoryUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Compares selected file with itself in one of other branches.
 * @author Kirill Likhodedov
 */
public class GitCompareWithBranchAction extends DumbAwareAction {

  private static final Logger LOG = Logger.getInstance(GitCompareWithBranchAction.class.getName());

  @Override
  public void actionPerformed(AnActionEvent event) {
    // get basic information
    final Project project = event.getData(PlatformDataKeys.PROJECT);
    if (project == null || project.isDisposed()) {
      notifyError(project, "Project is null. " + event.getPlace() + ", " + event.getDataContext(), null);
      return;
    }
    final VirtualFile[] vFiles = event.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    if (vFiles == null || vFiles.length != 1 || vFiles[0] == null) {
      notifyError(project, "Selected incorrect virtual files array: " + Arrays.toString(vFiles), null);
      return;
    }
    final VirtualFile file = vFiles[0];
    final VirtualFile vcsRoot = VcsUtil.getVcsRootFor(project, file);
    if (vcsRoot == null) {
      notifyError(project, "The file " + file + " is not under Git version control.", null);
      return;
    }

    // get all branches and current branch
    final List<String> branches = new ArrayList<String>();
    GitBranch curBranch = null;
    try {
      GitBranch.listAsStrings(project, vcsRoot, true, true, branches, null); // make it return current branch not to call the command twice.
      curBranch = GitBranch.current(project, vcsRoot);
    } catch (VcsException e) {
      notifyError(project, "Couldn't get information about current branch", e);
    }
    if (curBranch == null) {
      notifyError(project, "Current branch is null.", null);
      return;
    }

    // invoke popup
    final AtomicReference<ListPopup> popup = new AtomicReference<ListPopup>();
    final String currentBranch = curBranch.getName();
    final ListPopupStep<String> branchesStep = new BaseListPopupStep<String>("Select branch to compare", branches) {
      @Override
      public PopupStep onChosen(final String selectedValue, boolean finalChoice) {
        return doFinalStep(new Runnable() {
          public void run() {
            if (project.isDisposed()) { return; }
            try {
              showDiffWithBranch(project, file, currentBranch, selectedValue);
              popup.get().cancel();
            } catch (Exception e) {
              notifyError(project, "Couldn't compare file [" + file + "] with selected branch [" + selectedValue + "]", e);
            }
          }
        });
      }
    };
    popup.set(new ListPopupImpl(branchesStep));
    popup.get().showInBestPositionFor(event.getDataContext());
  }

  private static void notifyError(Project project, String message, Throwable t) {
    if (t != null) {
      LOG.info(message, t);
    } else {
      LOG.info(message);
    }
    Notifications.Bus.notify(new Notification(GitVcs.NOTIFICATION_GROUP_ID, "Couldn't compare with branch", message, NotificationType.WARNING), project);
  }

  private static void showDiffWithBranch(Project project, VirtualFile file, String currentBranch, String compareBranch) throws VcsException, IOException {
    final FilePath filePath = new FilePathImpl(file);
    final VcsRevisionNumber currentRevisionNumber = GitHistoryUtils.getCurrentRevision(project, filePath, currentBranch);
    final VcsRevisionNumber compareRevisionNumber = GitHistoryUtils.getCurrentRevision(project, filePath, compareBranch);
    if (currentRevisionNumber == null || compareRevisionNumber == null) {
      throw new VcsException("Null revision number. Current: [" + currentRevisionNumber + "], compare: [" + compareRevisionNumber + "]");
    }
    final VcsFileRevision compareRevision = new GitFileRevision(project, filePath, (GitRevisionNumber)compareRevisionNumber);
    VcsHistoryUtil.showDiff(project, filePath, new CurrentRevision(file, currentRevisionNumber), compareRevision,
                            ((GitRevisionNumber)currentRevisionNumber).getShortRev() + " on " + currentBranch,
                            ((GitRevisionNumber)compareRevisionNumber).getShortRev() + " on " + compareBranch);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    Presentation presentation = e.getPresentation();
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    VirtualFile[] vFiles = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    if (vFiles == null || vFiles.length == 0 || vFiles.length > 1) { // only 1 file for now
      presentation.setEnabled(false);
      presentation.setVisible(true);
      return;
    }
    GitVcs vcs = GitVcs.getInstance(project);
    boolean enabled = ProjectLevelVcsManager.getInstance(project).checkAllFilesAreUnder(vcs, vFiles);
    enabled &= vFiles[0] != null && !vFiles[0].isDirectory(); // not for dirs for now

    presentation.setEnabled(enabled);
    presentation.setVisible(enabled);
  }

}
