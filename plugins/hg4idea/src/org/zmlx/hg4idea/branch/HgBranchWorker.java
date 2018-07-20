// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.branch;

import com.intellij.dvcs.ui.CompareBranchesDialog;
import com.intellij.dvcs.util.CommitCompareInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgExecutionException;
import org.zmlx.hg4idea.action.HgCompareWithBranchAction;
import org.zmlx.hg4idea.log.HgCommit;
import org.zmlx.hg4idea.log.HgHistoryUtil;
import org.zmlx.hg4idea.repo.HgRepository;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class HgBranchWorker {
  private final static String CURRENT_REVISION = ".";
  private static final Logger LOG = Logger.getInstance(HgBranchWorker.class);
  @NotNull private final Project myProject;
  @SuppressWarnings("unused")
  @NotNull private final ProgressIndicator myIndicator;

  HgBranchWorker(@NotNull Project project, @NotNull ProgressIndicator indicator) {
    myProject = project;
    myIndicator = indicator;
  }

  public void compare(@NotNull final String branchName, @NotNull final List<HgRepository> repositories,
                      @NotNull final HgRepository selectedRepository) {
    final CommitCompareInfo myCompareInfo = loadCommitsToCompare(repositories, branchName);
    if (myCompareInfo == null) {
      LOG.error("The task to get compare info didn't finish. Repositories: \n" + repositories + "\nbranch name: " + branchName);
      return;
    }

    ApplicationManager.getApplication().invokeLater(
      () -> displayCompareDialog(branchName, getCurrentBranchOrRev(repositories), myCompareInfo, selectedRepository));
  }

  private void displayCompareDialog(@NotNull String branchName, @NotNull String currentBranch, @NotNull CommitCompareInfo compareInfo,
                                    @NotNull HgRepository selectedRepository) {
    if (compareInfo.isEmpty()) {
      Messages.showInfoMessage(myProject, String.format("<html>There are no changes between <code>%s</code> and <code>%s</code></html>",
                                                        currentBranch, branchName), "No Changes Detected");
    }
    else {
      new CompareBranchesDialog(new HgCompareBranchesHelper(myProject), branchName, currentBranch, compareInfo, selectedRepository, false).show();
    }
  }

  @NotNull
  private static String getCurrentBranchOrRev(@NotNull Collection<HgRepository> repositories) {
    String currentBranch;
    if (repositories.size() > 1) {
      HgMultiRootBranchConfig multiRootBranchConfig = new HgMultiRootBranchConfig(repositories);
      currentBranch = multiRootBranchConfig.getCurrentBranch();
    }
    else {
      assert !repositories.isEmpty() : "No repositories passed to HgBranchOperationsProcessor.";
      HgRepository repository = repositories.iterator().next();
      currentBranch = repository.getCurrentBranchName();
    }
    return currentBranch == null ? CURRENT_REVISION : currentBranch;
  }


  private CommitCompareInfo loadCommitsToCompare(List<HgRepository> repositories, String branchName) {
    CommitCompareInfo compareInfo = new CommitCompareInfo();
    for (HgRepository repository : repositories) {
      loadCommitsToCompare(repository, branchName, compareInfo);
      compareInfo.put(repository, loadTotalDiff(repository, branchName));
    }
    return compareInfo;
  }

  private void loadCommitsToCompare(@NotNull HgRepository repository, @NotNull final String branchName, @NotNull CommitCompareInfo compareInfo) {
    final List<HgCommit> headToBranch;
    final List<HgCommit> branchToHead;
    try {
      headToBranch = HgHistoryUtil.history(myProject, repository.getRoot(), 1000, Arrays.asList("-r", "reverse(" + branchName + "%" + CURRENT_REVISION + ")"), true);
      branchToHead = HgHistoryUtil.history(myProject, repository.getRoot(), 1000, Arrays.asList("-r", "reverse(" + CURRENT_REVISION + "%" + branchName + ")"), true);
    }
    catch (VcsException e) {
      // we treat it as critical and report an error
      throw new HgExecutionException("Couldn't get [hg log :" + branchName + "] on repository [" + repository.getRoot() + "]", e);
    }
    compareInfo.put(repository, headToBranch, branchToHead);
  }

  @NotNull
  private static Collection<Change> loadTotalDiff(@NotNull HgRepository repository, @NotNull String branchName) {
    try {
      // return git diff between current working directory and branchName: working dir should be displayed as a 'left' one (base)
      return HgCompareWithBranchActionCaller.doGetDiffChanges(repository.getProject(), repository.getRoot(), branchName);
    }
    catch (VcsException e) {
      // we treat it as critical and report an error
      throw new HgExecutionException("Couldn't get [hg diff -r " + branchName + "] on repository [" + repository.getRoot() + "]", e);
    }
  }

  private static class HgCompareWithBranchActionCaller extends HgCompareWithBranchAction {
    public static Collection<Change> doGetDiffChanges(@NotNull Project project,
                                                      @NotNull VirtualFile file,
                                                      @NotNull String branchToCompare) throws VcsException {
      return new HgCompareWithBranchActionCaller().getDiffChanges(project, file, branchToCompare);
    }
  }
}
