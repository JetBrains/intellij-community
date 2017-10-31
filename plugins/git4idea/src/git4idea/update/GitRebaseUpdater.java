/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import git4idea.GitBranch;
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

  public GitRebaseUpdater(@NotNull Project project,
                          @NotNull Git git,
                          @NotNull GitRepository repository,
                          @NotNull GitBranchPair branchAndTracked,
                          @NotNull ProgressIndicator progressIndicator,
                          @NotNull UpdatedFiles updatedFiles) {
    super(project, git, repository, branchAndTracked, progressIndicator, updatedFiles);
    myRebaser = new GitRebaser(myProject, git, myProgressIndicator);
    myChangeListManager = ChangeListManager.getInstance(project);
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
  }

  @Override
  public boolean isSaveNeeded() {
    Collection<Change> localChanges = new LocalChangesUnderRoots(myChangeListManager, myVcsManager)
      .getChangesUnderRoots(singletonList(myRoot)).get(myRoot);
    return !ContainerUtil.isEmpty(localChanges);
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
    GitBranch dest = myBranchPair.getDest();
    LOG.assertTrue(dest != null, String.format("Destination branch is null for source branch %s in %s",
                                               myBranchPair.getBranch().getName(), myRoot));
    return dest.getName();
  }

  public void cancel() {
    myRebaser.abortRebase(myRoot);
    myProgressIndicator.setText2("Refreshing files for the root " + myRoot.getPath());
    myRoot.refresh(false, true);
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
                                         myRoot), null
        );
    }
    return result.success();
  }

}
