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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.CurrentRevision;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryUtil;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBList;
import git4idea.GitBranch;
import git4idea.GitFileRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitUIUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Compares selected file with itself in another branch.
 * @author Kirill Likhodedov
 */
public class GitCompareWithBranchAction extends DumbAwareAction {

  private static final Logger LOG = Logger.getInstance(GitCompareWithBranchAction.class.getName());

  @Override
  public void actionPerformed(final AnActionEvent event) {
    final Project project = event.getProject();
    assert project != null;

    final VirtualFile file = getAffectedFile(event);

    GitRepositoryManager manager = GitUtil.getRepositoryManager(project);
    if (manager == null) {
      return;
    }
    GitRepository repository = manager.getRepositoryForFile(file);
    assert repository != null;

    final String head = repository.getCurrentRevision();
    final List<String> branchNames = getBranchNamesExceptCurrent(repository);

    // prepare and invoke popup
    final JBList list = new JBList(branchNames);

    JBPopupFactory.getInstance()
      .createListPopupBuilder(list)
      .setTitle("Select branch to compare")
      .setItemChoosenCallback(new OnBranchChooseRunnable(project, file, head, list))
      .setAutoselectOnMouseMove(true)
      .createPopup()
      .showInBestPositionFor(event.getDataContext());
  }

  private static List<String> getBranchNamesExceptCurrent(GitRepository repository) {
    List<GitBranch> localBranches = new ArrayList<GitBranch>(repository.getBranches().getLocalBranches());
    Collections.sort(localBranches);
    List<GitBranch> remoteBranches = new ArrayList<GitBranch>(repository.getBranches().getRemoteBranches());
    Collections.sort(remoteBranches);
    
    if (repository.isOnBranch()) {
      localBranches.remove(repository.getCurrentBranch());
    }
    
    final List<String> branchNames = new ArrayList<String>();
    for (GitBranch branch : localBranches) {
      branchNames.add(branch.getName());
    }
    for (GitBranch branch : remoteBranches) {
      branchNames.add(branch.getName());
    }
    return branchNames;
  }

  private static VirtualFile getAffectedFile(AnActionEvent event) {
    final VirtualFile[] vFiles = event.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    assert vFiles != null && vFiles.length == 1 && vFiles[0] != null : "Illegal virtual files selected: " + Arrays.toString(vFiles);
    return vFiles[0];
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    if (project == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    VirtualFile[] vFiles = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    if (vFiles == null || vFiles.length != 1 || vFiles[0] == null || vFiles[0].isDirectory()) { // only 1 file for now, not for dirs
      presentation.setEnabled(false);
      presentation.setVisible(true);
      return;
    }

    GitRepositoryManager manager = GitUtil.getRepositoryManager(project);
    if (manager == null) {
      presentation.setEnabled(false);
      presentation.setVisible(true);
      return;
    }

    GitRepository repository = manager.getRepositoryForFile(vFiles[0]);
    if (repository == null || repository.isFresh() || noBranchesToCompare(repository)) {
      presentation.setEnabled(false);
      presentation.setVisible(true);
    }
  }

  private static boolean noBranchesToCompare(@NotNull GitRepository repository) {
    int locals = repository.getBranches().getLocalBranches().size();
    boolean haveRemotes = !repository.getBranches().getRemoteBranches().isEmpty();
    if (repository.isOnBranch()) {  // there are other branches to compare
      return locals < 2 && !haveRemotes;
    }
    return locals == 0 && !haveRemotes; // there are at least 1 branch to compare
  }

  private static class OnBranchChooseRunnable implements Runnable {
    private final Project myProject;
    private final VirtualFile myFile;
    private final String myHead;
    private final JBList myList;

    public OnBranchChooseRunnable(Project project, VirtualFile file, String head, JBList list) {
      myProject = project;
      myFile = file;
      myHead = head;
      myList = list;
    }

    @Override
    public void run() {
      new Task.Backgroundable(myProject, "Comparing...") {
        @Override public void run(@NotNull ProgressIndicator indicator) {
          String branchToCompare = myList.getSelectedValue().toString();
          try {
            showDiffWithBranch(myProject, myFile, myHead, branchToCompare);
          }
          catch (VcsException e) {
            if (e.getMessage().contains("exists on disk, but not in")) {
              fileDoesntExistInBranchError(myProject, myFile, branchToCompare);
            } else {
              GitUIUtil.notifyError(myProject, "Couldn't compare with branch",
                                    "Couldn't compare file [" + myFile + "] with selected branch [" + myList.getSelectedValue() + "]",
                                    false, e);
            }
          }
        }
      }.queue();
    }

    private static void showDiffWithBranch(@NotNull Project project, @NotNull VirtualFile file, @NotNull String head, @NotNull String branchToCompare) throws VcsException {
      final FilePath filePath = new FilePathImpl(file);
      final VcsRevisionNumber currentRevisionNumber = GitHistoryUtils.getCurrentRevision(project, filePath, head);
      final VcsRevisionNumber compareRevisionNumber = GitHistoryUtils.getCurrentRevision(project, filePath, branchToCompare);
      if (compareRevisionNumber == null) {
        fileDoesntExistInBranchError(project, file, branchToCompare);
        return;
      }
      final VcsFileRevision compareRevision = new GitFileRevision(project, filePath, (GitRevisionNumber)compareRevisionNumber, false);
      final String currentTitle = currentRevisionNumber != null ? ((GitRevisionNumber)currentRevisionNumber).getShortRev() + " on " + head : "Local changes on " + head;
      final String compareTitle = ((GitRevisionNumber)compareRevisionNumber).getShortRev() + " on " + branchToCompare;

      try {
        VcsHistoryUtil.showDiff(project, filePath, new CurrentRevision(file, currentRevisionNumber), compareRevision, currentTitle, compareTitle);
      }
      catch (IOException e) {
        throw new VcsException(String.format("Couldn't show diff for file [%s], head [%s] and branch [%s]", file.getPresentableUrl(), head, branchToCompare), e);
      }
    }

    private static void fileDoesntExistInBranchError(Project project, VirtualFile file, String branchToCompare) {
      GitUIUtil.notifyMessage(project, "File doesn't exist in branch",
                              String.format("File <code>%s</code> doesn't exist in branch <code>%s</code>", file.getPresentableUrl(), branchToCompare),
                              NotificationType.WARNING, true, null);
    }
  }

}
