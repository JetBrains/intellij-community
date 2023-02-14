// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.cherrypick;

import com.intellij.dvcs.cherrypick.VcsCherryPicker;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IntRef;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsFullCommitDetails;
import git4idea.GitApplyChangesProcess;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.actions.GitAbortOperationAction;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandlerListener;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import kotlin.Unit;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static git4idea.GitProtectedBranchesKt.isCommitPublished;

public class GitCherryPicker extends VcsCherryPicker {
  private static final Logger LOG = Logger.getInstance(GitCherryPicker.class);

  @NotNull private final Project myProject;
  @NotNull private final GitRepositoryManager myRepositoryManager;
  @NotNull private final GitVcsSettings mySettings;

  public GitCherryPicker(@NotNull Project project) {
    myProject = project;
    myRepositoryManager = GitUtil.getRepositoryManager(myProject);
    mySettings = GitVcsSettings.getInstance(myProject);
  }

  @Override
  public void cherryPick(@NotNull List<? extends VcsFullCommitDetails> commits) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setIndeterminate(false);
    }
    IntRef cherryPickedCommitsCount = new IntRef(1);
    new GitApplyChangesProcess(myProject, commits, true,
                               GitBundle.message("cherry.pick.name"), GitBundle.message("cherry.pick.applied"),
                               (repository, commit, autoCommit, listeners) -> {
                                 GitCommandResult result = cherryPickSingleCommit(
                                   repository, commit, autoCommit, listeners,
                                   indicator, cherryPickedCommitsCount.get(), commits.size()
                                 );
                                 cherryPickedCommitsCount.inc();
                                 return result;
                               },
                               new GitAbortOperationAction.CherryPick(),
                               result -> isNothingToCommitMessage(result),
                               (repository, commit) -> createCommitMessage(repository, commit),
                               true,
                               (repository, autoCommit) -> cancelCherryPick(repository, autoCommit)).execute();
  }

  private GitCommandResult cherryPickSingleCommit(
    @NotNull GitRepository repository,
    @NotNull VcsCommitMetadata commit,
    boolean autoCommit,
    @NotNull List<? extends GitLineHandlerListener> listeners,
    @Nullable ProgressIndicator indicator,
    int alreadyCherryPickedCount,
    int totalCommitsToCherryPick
  ) {
    if (indicator != null) {
      updateCherryPickIndicatorText(indicator, commit, alreadyCherryPickedCount, totalCommitsToCherryPick);
    }
    GitCommandResult result = Git.getInstance().cherryPick(
      repository, commit.getId().asString(), autoCommit, shouldAddSuffix(repository, commit.getId()),
      listeners.toArray(new GitLineHandlerListener[0])
    );
    if (indicator != null) {
      indicator.setFraction((double)alreadyCherryPickedCount / totalCommitsToCherryPick);
    }
    return result;
  }

  private static void updateCherryPickIndicatorText(
    @NotNull ProgressIndicator indicator,
    @NotNull VcsCommitMetadata commit,
    int alreadyCherryPickedCount,
    int totalCommitsToCherryPick
  ) {
    if (totalCommitsToCherryPick > 1) {
      indicator.setText(DvcsBundle.message(
        "cherry.picking.process.commit",
        StringUtil.trimMiddle(commit.getSubject(), 30),
        alreadyCherryPickedCount,
        totalCommitsToCherryPick
      ));
    }
    else {
      indicator.setText(DvcsBundle.message(
        "cherry.picking.process.commit.single",
        StringUtil.trimMiddle(commit.getSubject(), 30)
      ));
    }
  }

  private static boolean isNothingToCommitMessage(@NotNull GitCommandResult result) {
    String stdout = result.getOutputAsJoinedString();
    return stdout.contains("nothing to commit") || stdout.contains("previous cherry-pick is now empty");
  }

  @NotNull
  private String createCommitMessage(@NotNull GitRepository repository, @NotNull VcsFullCommitDetails commit) {
    String message = commit.getFullMessage();
    if (shouldAddSuffix(repository, commit.getId())) {
      message += String.format("\n\n(cherry picked from commit %s)", commit.getId().asString()); //NON-NLS Do not i18n commit template
    }
    return message;
  }

  private boolean shouldAddSuffix(@NotNull GitRepository repository, @NotNull Hash commit) {
    return mySettings.shouldAddSuffixToCherryPicksOfPublishedCommits() &&
           isCommitPublished(repository, commit);
  }

  /**
   * We control the cherry-pick workflow ourselves + we want to use partial commits ('git commit --only'), which is prohibited during
   * cherry-pick, i.e. until the CHERRY_PICK_HEAD exists.
   */
  private static Unit cancelCherryPick(@NotNull GitRepository repository, boolean autoCommit) {
    if (autoCommit) { // `git cherry-pick -n` doesn't create the CHERRY_PICK_HEAD
      removeCherryPickHead(repository);
    }
    return Unit.INSTANCE;
  }

  private static void removeCherryPickHead(@NotNull GitRepository repository) {
    File cherryPickHeadFile = repository.getRepositoryFiles().getCherryPickHead();
    if (cherryPickHeadFile.exists()) {
      boolean deleted = FileUtil.delete(cherryPickHeadFile);
      if (!deleted) {
        LOG.warn("Couldn't delete " + cherryPickHeadFile);
      }
    }
    else {
      LOG.info("Cancel cherry-pick in " + repository.getPresentableUrl() + ": no CHERRY_PICK_HEAD found");
    }
  }

  @NotNull
  @Override
  public VcsKey getSupportedVcs() {
    return GitVcs.getKey();
  }


  @Override
  @NotNull
  @Nls(capitalization = Nls.Capitalization.Title)
  public String getActionTitle() {
    return DvcsBundle.message("cherry.pick.action.text");
  }

  @Override
  public boolean canHandleForRoots(@NotNull Collection<? extends VirtualFile> roots) {
    return ContainerUtil.all(roots, r -> myRepositoryManager.getRepositoryForRootQuick(r) != null);
  }
}
