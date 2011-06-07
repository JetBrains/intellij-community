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

import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.CurrentRevision;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryUtil;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBList;
import com.intellij.util.NotNullFunction;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitBranch;
import git4idea.GitFileRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitVcs;
import git4idea.history.GitHistoryUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.font.TextAttribute;
import java.io.IOException;
import java.util.*;

/**
 * Compares selected file with itself in one of other branches.
 * @author Kirill Likhodedov
 */
public class GitCompareWithBranchAction extends DumbAwareAction {

  private static final Logger LOG = Logger.getInstance(GitCompareWithBranchAction.class.getName());

  @Override
  public void actionPerformed(final AnActionEvent event) {
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

    // get branches information
    final List<GitBranch> branches = new ArrayList<GitBranch>();
    GitBranch curBranch = null;
    try {
      curBranch = GitBranch.list(project, vcsRoot, true, true, branches, null);
    } catch (VcsException e) {
      notifyError(project, "Couldn't get information about current branch", e);
    }
    if (curBranch == null) {
      notifyError(project, "Current branch is null.", null);
      return;
    }
    final String currentBranch = curBranch.getName();

    // prepare and invoke popup
    final JBList list = new JBList(branches);
    list.installCellRenderer(new NotNullFunction<GitBranch, JComponent>() { // display current branch in bold with asterisk
      @NotNull public JComponent fun(GitBranch branch) {
        if (branch.isActive()) {
          JLabel label = new JLabel(branch.getName() + " *");
          final Map<TextAttribute, Float> attributes = new HashMap<TextAttribute, Float>(1);
          attributes.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD);
          label.setFont(label.getFont().deriveFont(attributes));
          return label;
        }
        return new JLabel(branch.getName());
      }
    });

    JBPopupFactory.getInstance()
      .createListPopupBuilder(list)
      .setTitle("Select branch to compare")
      .setItemChoosenCallback(
        new Runnable() {
          public void run() {
            Application app = ApplicationManager.getApplication();
            if (project.isDisposed() || app == null || !app.isActive() || app.isDisposed() || app.isDisposeInProgress()) { // safe check
              return;
            }
            ApplicationManager.getApplication()
              .invokeLater(new Runnable() { // don't block awt thread - getting revision content may take long

                @Override
                public void run() {
                  try {
                    showDiffWithBranch(project, file, currentBranch, list.getSelectedValue().toString());
                  }
                  catch (Exception e) {
                    notifyError(project, "Couldn't compare file [" + file + "] with selected branch [" + list.getSelectedValue() + "]", e);
                  }
                }
              });
          }
        })
      .setAutoselectOnMouseMove(true)
      .createPopup()
      .showInBestPositionFor(event.getDataContext());
  }

  private static void notifyError(Project project, String message, Throwable t) {
    if (t != null) {
      LOG.info(message, t);
    } else {
      LOG.info(message);
    }
    GitVcs.IMPORTANT_ERROR_NOTIFICATION.createNotification("Couldn't compare with branch", message, NotificationType.WARNING, null).notify(project);
  }

  private static void showDiffWithBranch(Project project, VirtualFile file, String currentBranch, String compareBranch) throws VcsException, IOException {
    final FilePath filePath = new FilePathImpl(file);
    final VcsRevisionNumber currentRevisionNumber = GitHistoryUtils.getCurrentRevision(project, filePath, currentBranch);
    final VcsRevisionNumber compareRevisionNumber = GitHistoryUtils.getCurrentRevision(project, filePath, compareBranch);
    if (compareRevisionNumber == null) {
      GitVcs.IMPORTANT_ERROR_NOTIFICATION.createNotification("File doesn't exist in branch", "File " + file.getPresentableUrl() + " doesn't exist in branch [" + compareBranch + "]", NotificationType.INFORMATION, null).notify(project);
      return;
    }
    final VcsFileRevision compareRevision = new GitFileRevision(project, filePath, (GitRevisionNumber)compareRevisionNumber);
    final String currentTitle = currentRevisionNumber != null ? ((GitRevisionNumber)currentRevisionNumber).getShortRev() + " on " + currentBranch : "Local changes on " + currentBranch;
    final String compareTitle = ((GitRevisionNumber)compareRevisionNumber).getShortRev() + " on " + compareBranch;
    VcsHistoryUtil.showDiff(project, filePath, new CurrentRevision(file, currentRevisionNumber), compareRevision, currentTitle, compareTitle);
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
