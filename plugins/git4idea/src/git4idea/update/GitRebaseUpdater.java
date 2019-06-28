// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.impl.LocalChangesUnderRoots;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitUtil;
import git4idea.branch.GitBranchPair;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.rebase.GitRebaser;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

import static java.util.Collections.singletonList;

/**
 * Handles 'git pull --rebase'
 */
public class GitRebaseUpdater extends GitUpdater {
  private static final Logger LOG = Logger.getInstance(GitRebaseUpdater.class.getName());
  private final GitRebaser myRebaser;
  private final ChangeListManager myChangeListManager;
  private final ProjectLevelVcsManager myVcsManager;
  @NotNull private final GitBranchPair myBranchPair;

  public GitRebaseUpdater(@NotNull Project project,
                          @NotNull Git git,
                          @NotNull GitRepository repository,
                          @NotNull GitBranchPair branchPair,
                          @NotNull ProgressIndicator progressIndicator,
                          @NotNull UpdatedFiles updatedFiles) {
    super(project, git, repository, progressIndicator, updatedFiles);
    myRebaser = new GitRebaser(myProject, git, myProgressIndicator);
    myChangeListManager = ChangeListManager.getInstance(project);
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    myBranchPair = branchPair;
  }

  @Override
  public boolean isSaveNeeded() {
    Collection<Change> localChanges = new LocalChangesUnderRoots(myChangeListManager, myVcsManager)
      .getChangesUnderRoots(singletonList(myRoot)).get(myRoot);
    try {
      return !ContainerUtil.isEmpty(localChanges) ||
             GitUtil.hasLocalChanges(true, myProject, myRoot);
    }
    catch (VcsException e) {
      LOG.info("isSaveNeeded failed to check local changes", e);
      return true;
    }
  }

  @NotNull
  @Override
  protected GitUpdateResult doUpdate() {
    LOG.info("doUpdate ");
    String remoteBranch = getRemoteBranchToMerge();
    List<String> params = singletonList(remoteBranch);
    return myRebaser.rebase(myRoot, params, () -> cancel(), null);
  }

  @NotNull
  private String getRemoteBranchToMerge() {
    return myBranchPair.getTarget().getName();
  }

  public void cancel() {
    myRebaser.abortRebase(myRoot);
    myProgressIndicator.setText2("Refreshing files for the root " + myRoot.getPath());
    myRoot.refresh(false, true);
  }

  @NotNull
  GitBranchPair getSourceAndTarget() {
    return myBranchPair;
  }

  @Override
  public String toString() {
    return "Rebase updater";
  }

  /**
   * Tries to execute {@code git merge --ff-only}.
   * @return true, if everything is successful; false for any error (to let a usual "fair" update deal with it).
   */
  public boolean fastForwardMerge() {
    LOG.info("Trying fast-forward merge for " + myRoot);
    GitRepository repository = GitUtil.getRepositoryManager(myProject).getRepositoryForRoot(myRoot);
    if (repository == null) {
      LOG.error("Repository is null for " + myRoot);
      return false;
    }
    try {
      markStart(myRoot);
    }
    catch (VcsException e) {
      LOG.info("Couldn't mark start for repository " + myRoot, e);
      return false;
    }

    GitCommandResult result = myGit.merge(repository, getRemoteBranchToMerge(), singletonList("--ff-only"));

    try {
      markEnd(myRoot);
    }
    catch (VcsException e) {
      // this is not critical, and update has already happened,
      // so we just notify the user about problems with collecting the updated changes.
      LOG.info("Couldn't mark end for repository " + myRoot, e);
      VcsNotifier.getInstance(myProject).
        notifyMinorWarning("Couldn't collect the updated files info",
                           String.format("Update of %s was successful, but we couldn't collect the updated changes because of an error",
                                         myRoot));
    }
    return result.success();
  }

}
