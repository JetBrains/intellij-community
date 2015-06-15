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

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.CurrentRevision;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBList;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitBranch;
import git4idea.GitFileRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.history.GitDiffFromHistoryHandler;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitUIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
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
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getRequiredData(CommonDataKeys.PROJECT);
    VirtualFile file = getAffectedFile(event);
    GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForFile(file);
    assert repository != null;

    GitBranch currentBranch = repository.getCurrentBranch();
    String head;
    if (currentBranch == null) {
      String currentRevision = repository.getCurrentRevision();
      if (currentRevision == null) {
        LOG.error("Current revision is null for " + repository + ". Compare with branch shouldn't be available for fresh repository");
        return;
      }
      head = DvcsUtil.getShortHash(currentRevision);
    }
    else {
      head = currentBranch.getName();
    }
    List<String> branchNames = getBranchNamesExceptCurrent(repository);

    JBList list = new JBList(branchNames);
    JBPopupFactory.getInstance()
      .createListPopupBuilder(list)
      .setTitle("Select branch to compare")
      .setItemChoosenCallback(new OnBranchChooseRunnable(project, file, head, list))
      .setAutoselectOnMouseMove(true)
      .setFilteringEnabled(new Function<Object, String>() {
        @Override
        public String fun(Object o) {
          return o.toString();
        }
      })
      .createPopup()
      .showInBestPositionFor(event.getDataContext());
  }

  @NotNull
  private static List<String> getBranchNamesExceptCurrent(@NotNull GitRepository repository) {
    List<GitBranch> localBranches = new ArrayList<GitBranch>(repository.getBranches().getLocalBranches());
    Collections.sort(localBranches);
    List<GitBranch> remoteBranches = new ArrayList<GitBranch>(repository.getBranches().getRemoteBranches());
    Collections.sort(remoteBranches);
    
    if (repository.isOnBranch()) {
      localBranches.remove(repository.getCurrentBranch());
    }
    
    List<String> branchNames = ContainerUtil.newArrayList();
    for (GitBranch branch : localBranches) {
      branchNames.add(branch.getName());
    }
    for (GitBranch branch : remoteBranches) {
      branchNames.add(branch.getName());
    }
    return branchNames;
  }

  private static VirtualFile getAffectedFile(@NotNull AnActionEvent event) {
    final VirtualFile[] vFiles = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    assert vFiles != null && vFiles.length == 1 && vFiles[0] != null : "Illegal virtual files selected: " + Arrays.toString(vFiles);
    return vFiles[0];
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    if (project == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    VirtualFile[] vFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (vFiles == null || vFiles.length != 1 || vFiles[0] == null) { // only 1 file for now
      presentation.setEnabled(false);
      presentation.setVisible(true);
      return;
    }

    GitRepositoryManager manager = GitUtil.getRepositoryManager(project);

    GitRepository repository = manager.getRepositoryForFile(vFiles[0]);
    if (repository == null || repository.isFresh() || noBranchesToCompare(repository)) {
      presentation.setEnabled(false);
      presentation.setVisible(true);
      return;
    }

    presentation.setEnabled(true);
    presentation.setVisible(true);
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
    private final JList myList;

    OnBranchChooseRunnable(@NotNull Project project, @NotNull VirtualFile file, @NotNull String head, @NotNull JList list) {
      myProject = project;
      myFile = file;
      myHead = head;
      myList = list;
    }

    @Override
    public void run() {
      Object selectedValue = myList.getSelectedValue();
      if (selectedValue == null) {
        LOG.error("Selected value is unexpectedly null");
        return;
      }
      String branchToCompare = selectedValue.toString();
      try {
        showDiffWithBranch(myProject, myFile, myHead, branchToCompare);
      }
      catch (VcsException e) {
        if (e.getMessage().contains("exists on disk, but not in")) {
          fileDoesntExistInBranchError(myProject, myFile, branchToCompare);
        } else {
          GitUIUtil.notifyError(myProject, "Couldn't compare with branch",
                                String.format("Couldn't compare file [%s] with selected branch [%s]", myFile, selectedValue),
                                false, e);
        }
      }
    }

    private static void showDiffWithBranch(@NotNull Project project, @NotNull VirtualFile file, @NotNull String head,
                                           @NotNull String branchToCompare) throws VcsException {
      FilePath filePath = VcsUtil.getFilePath(file);
      // we could use something like GitRepository#getCurrentRevision here,
      // but this way we can easily identify if the file is available in the branch
      GitRevisionNumber compareRevisionNumber = (GitRevisionNumber)GitHistoryUtils.getCurrentRevision(project, filePath, branchToCompare);
      if (compareRevisionNumber == null) {
        fileDoesntExistInBranchError(project, file, branchToCompare);
        return;
      }

      GitRevisionNumber currentRevisionNumber = (GitRevisionNumber)GitHistoryUtils.getCurrentRevision(project, filePath, head);
      if (currentRevisionNumber == null) {
        LOG.error(String.format("Current revision number is null for file [%s] and branch [%s]", filePath, head));
        return;
      }

      // constructing the revision with human readable name (will work for files comparison however).
      VcsFileRevision compareRevision = new GitFileRevision(project, filePath,
                                                            new GitRevisionNumber(branchToCompare, compareRevisionNumber.getTimestamp()));
      CurrentRevision currentRevision = new CurrentRevision(file, new GitRevisionNumber(head, currentRevisionNumber.getTimestamp()));
      new GitDiffFromHistoryHandler(project).showDiffForTwo(project, VcsUtil.getFilePath(file), compareRevision, currentRevision);
    }

    private static void fileDoesntExistInBranchError(@NotNull Project project, @NotNull VirtualFile file, @NotNull String branchToCompare) {
      GitUIUtil.notifyMessage(project, GitUtil.fileOrFolder(file) + " doesn't exist in branch",
                              String.format("%s <code>%s</code> doesn't exist in branch <code>%s</code>",
                                            GitUtil.fileOrFolder(file), file.getPresentableUrl(), branchToCompare),
                              false, null);
    }
  }

}
