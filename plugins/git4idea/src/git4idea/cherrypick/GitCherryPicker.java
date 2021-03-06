// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.cherrypick;

import com.intellij.dvcs.cherrypick.VcsCherryPicker;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import git4idea.GitApplyChangesProcess;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandlerListener;
import git4idea.config.GitVcsApplicationSettings;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import kotlin.Unit;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

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
    new GitApplyChangesProcess(myProject, commits, isAutoCommit(),
                               GitBundle.message("cherry.pick.name"), GitBundle.message("cherry.pick.applied"),
                               (repository, commit, autoCommit, listeners) ->
                                 Git.getInstance()
                                   .cherryPick(repository, commit.asString(), autoCommit, shouldAddSuffix(repository, commit),
                                               listeners.toArray(new GitLineHandlerListener[0])),
                               result -> isNothingToCommitMessage(result),
                               (repository, commit) -> createCommitMessage(repository, commit),
                               true,
                               repository -> cancelCherryPick(repository)).execute();
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
  private static Unit cancelCherryPick(@NotNull GitRepository repository) {
    if (isAutoCommit()) {
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

  private static boolean isAutoCommit() {
    return GitVcsApplicationSettings.getInstance().isAutoCommitOnCherryPick();
  }

  @Override
  public boolean canHandleForRoots(@NotNull Collection<? extends VirtualFile> roots) {
    return roots.stream().allMatch(r -> myRepositoryManager.getRepositoryForRootQuick(r) != null);
  }
}
