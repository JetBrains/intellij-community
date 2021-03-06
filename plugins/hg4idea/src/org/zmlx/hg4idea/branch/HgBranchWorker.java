// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.branch;

import com.intellij.dvcs.branch.DvcsBranchUtil;
import com.intellij.dvcs.ui.CompareBranchesDialog;
import com.intellij.dvcs.util.CommitCompareInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.action.HgCompareWithBranchAction;
import org.zmlx.hg4idea.log.HgHistoryUtil;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.zmlx.hg4idea.HgNotificationIdsHolder.COMPARE_WITH_BRANCH_ERROR;

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
    try {
      final CommitCompareInfo myCompareInfo = loadCommitsToCompare(repositories, branchName);
      ApplicationManager.getApplication().invokeLater(() -> displayCompareDialog(branchName, getCurrentBranchOrRev(repositories), myCompareInfo, selectedRepository));
    }
    catch (VcsException e) {
      VcsNotifier.getInstance(myProject).notifyError(COMPARE_WITH_BRANCH_ERROR,
                                                     HgBundle.message("hg4idea.branch.compare.error"),
                                                     e.getMessage());
    }
  }

  private void displayCompareDialog(@NotNull String branchName, @NotNull String currentBranch, @NotNull CommitCompareInfo compareInfo,
                                    @NotNull HgRepository selectedRepository) {
    if (compareInfo.isEmpty()) {
      Messages.showInfoMessage(myProject, XmlStringUtil
                                 .wrapInHtml(HgBundle.message("hg4idea.branch.compare.no.changes.msg", currentBranch, branchName)),
                               HgBundle.message("hg4idea.branch.compare.no.changes"));
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

  @NotNull
  private CommitCompareInfo loadCommitsToCompare(List<HgRepository> repositories, String branchName) throws VcsException {
    CommitCompareInfo compareInfo = new CommitCompareInfo();
    for (HgRepository repository : repositories) {
      List<VcsFullCommitDetails> headToBranch = loadCommitsBetween(repository, CURRENT_REVISION, branchName);
      List<VcsFullCommitDetails> branchToHead = loadCommitsBetween(repository, branchName, CURRENT_REVISION);
      compareInfo.put(repository, headToBranch, branchToHead);
      compareInfo.putTotalDiff(repository, loadTotalDiff(repository, branchName));
    }
    return compareInfo;
  }

  @NotNull
  private List<VcsFullCommitDetails> loadCommitsBetween(HgRepository repository, String fromRev, String toRev) throws VcsException {
    List<String> parameters = Arrays.asList("-r", "reverse(\"" + toRev + "\"%\"" + fromRev + "\")");
    return HgHistoryUtil.history(myProject, repository.getRoot(), -1, parameters, true);
  }

  @NotNull
  private static Collection<Change> loadTotalDiff(@NotNull HgRepository repository, @NotNull String branchName) throws VcsException {
    // return diff between current working directory and branchName: working dir should be displayed as a 'left' one (base)
    HgRevisionNumber branchRevisionNumber = HgCompareWithBranchAction.getBranchRevisionNumber(repository, branchName);
    VirtualFile root = repository.getRoot();
    List<Change> changes = HgUtil.getDiff(repository.getProject(), root, VcsUtil.getFilePath(root), branchRevisionNumber, null);
    return DvcsBranchUtil.swapRevisions(changes);
  }
}
