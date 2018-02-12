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
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.*;
import git4idea.branch.GitBranchPair;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.config.GitConfigUtil;
import git4idea.config.GitVersionSpecialty;
import git4idea.config.UpdateMethod;
import git4idea.merge.MergeChangeCollector;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import static git4idea.GitUtil.HEAD;
import static git4idea.config.UpdateMethod.MERGE;
import static git4idea.config.UpdateMethod.REBASE;

/**
 * Updates a single repository via merge or rebase.
 * @see GitRebaseUpdater
 * @see GitMergeUpdater
 */
public abstract class GitUpdater {
  private static final Logger LOG = Logger.getInstance(GitUpdater.class);

  @NotNull protected final Project myProject;
  @NotNull protected final Git myGit;
  @NotNull protected final VirtualFile myRoot;
  @NotNull protected final GitRepository myRepository;
  @NotNull protected final GitBranchPair myBranchPair;
  @NotNull protected final ProgressIndicator myProgressIndicator;
  @NotNull protected final UpdatedFiles myUpdatedFiles;
  @NotNull protected final AbstractVcsHelper myVcsHelper;
  @NotNull protected final GitRepositoryManager myRepositoryManager;
  protected final GitVcs myVcs;

  protected GitRevisionNumber myBefore; // The revision that was before update

  protected GitUpdater(@NotNull Project project,
                       @NotNull Git git,
                       @NotNull GitRepository repository,
                       @NotNull GitBranchPair branchAndTracked,
                       @NotNull ProgressIndicator progressIndicator,
                       @NotNull UpdatedFiles updatedFiles) {
    myProject = project;
    myGit = git;
    myRoot = repository.getRoot();
    myRepository = repository;
    myBranchPair = branchAndTracked;
    myProgressIndicator = progressIndicator;
    myUpdatedFiles = updatedFiles;
    myVcsHelper = AbstractVcsHelper.getInstance(project);
    myVcs = GitVcs.getInstance(project);
    myRepositoryManager = GitUtil.getRepositoryManager(myProject);
  }

  /**
   * Returns proper updater based on the update policy (merge or rebase) selected by user or stored in his .git/config
   * @return {@link GitMergeUpdater} or {@link GitRebaseUpdater}.
   */
  @NotNull
  public static GitUpdater getUpdater(@NotNull Project project,
                                      @NotNull Git git,
                                      @NotNull GitBranchPair trackedBranches,
                                      @NotNull GitRepository repository,
                                      @NotNull ProgressIndicator progressIndicator,
                                      @NotNull UpdatedFiles updatedFiles,
                                      @NotNull UpdateMethod updateMethod) {
    if (updateMethod == UpdateMethod.BRANCH_DEFAULT) {
      updateMethod = resolveUpdateMethod(repository);
    }
    return updateMethod == UpdateMethod.REBASE ?
           new GitRebaseUpdater(project, git, repository, trackedBranches, progressIndicator, updatedFiles):
           new GitMergeUpdater(project, git, repository, trackedBranches, progressIndicator, updatedFiles);
  }

  @NotNull
  public static UpdateMethod resolveUpdateMethod(@NotNull GitRepository repository) {
    Project project = repository.getProject();
    GitLocalBranch branch = repository.getCurrentBranch();
    if (branch != null) {
      String branchName = branch.getName();
      try {
        String rebaseValue = GitConfigUtil.getValue(project, repository.getRoot(), "branch." + branchName + ".rebase");
        if (rebaseValue != null) {
          if (isRebaseValue(rebaseValue)) {
            return REBASE;
          }
          if (GitConfigUtil.getBooleanValue(rebaseValue) == Boolean.FALSE) {
            // explicit override of a more generic pull.rebase config value
            return MERGE;
          }
          LOG.warn("Unknown value for branch." + branchName + ".rebase: " + rebaseValue);
        }
      }
      catch (VcsException e) {
        LOG.warn("Couldn't get git config branch." + branchName + ".rebase");
      }
    }

    if (GitVersionSpecialty.KNOWS_PULL_REBASE.existsIn(GitVcs.getInstance(project).getVersion())) {
      try {
        String pullRebaseValue = GitConfigUtil.getValue(project, repository.getRoot(), "pull.rebase");
        if (pullRebaseValue != null && isRebaseValue(pullRebaseValue)) return REBASE;
      }
      catch (VcsException e) {
        LOG.warn("Couldn't get git config pull.rebase");
      }
    }

    return MERGE;
  }

  private static boolean isRebaseValue(@NotNull String configValue) {
    return GitConfigUtil.getBooleanValue(configValue) == Boolean.TRUE ||
           configValue.equalsIgnoreCase("interactive") ||
           configValue.equalsIgnoreCase("preserve"); // 'yes' is not specified in the man, but actually works
  }

  @NotNull
  public GitUpdateResult update() throws VcsException {
    markStart(myRoot);
    try {
      return doUpdate();
    } finally {
      markEnd(myRoot);
    }
  }

  /**
   * Checks the repository if local changes need to be saved before update.
   * For rebase local changes need to be saved always, 
   * for merge - only in the case if merge affects the same files or there is something in the index.
   * @return true if local changes from this root need to be saved, false if not.
   */
  public abstract boolean isSaveNeeded();

  /**
   * Checks if update is needed, i.e. if there are remote changes that weren't merged into the current branch.
   * @return true if update is needed, false otherwise.
   */
  public boolean isUpdateNeeded() throws VcsException {
    GitBranch dest = myBranchPair.getDest();
    assert dest != null;
    String remoteBranch = dest.getName();
    if (!hasRemoteChanges(remoteBranch)) {
      LOG.info("isUpdateNeeded: No remote changes, update is not needed");
      return false;
    }
    return true;
  }

  /**
   * Performs update (via rebase or merge - depending on the implementing classes).
   */
  @NotNull
  protected abstract GitUpdateResult doUpdate();

  @NotNull
  GitBranchPair getSourceAndTarget() {
    return myBranchPair;
  }

  protected void markStart(VirtualFile root) throws VcsException {
    // remember the current position
    myBefore = GitRevisionNumber.resolve(myProject, root, "HEAD");
  }

  protected void markEnd(VirtualFile root) throws VcsException {
    // find out what have changed, this is done even if the process was cancelled.
    final MergeChangeCollector collector = new MergeChangeCollector(myProject, root, myBefore);
    final ArrayList<VcsException> exceptions = new ArrayList<>();
    collector.collect(myUpdatedFiles, exceptions);
    if (!exceptions.isEmpty()) {
      throw exceptions.get(0);
    }
  }

  protected boolean hasRemoteChanges(@NotNull String remoteBranch) throws VcsException {
    GitLineHandler handler = new GitLineHandler(myProject, myRoot, GitCommand.REV_LIST);
    handler.setSilent(true);
    handler.addParameters("-1");
    handler.addParameters(HEAD + ".." + remoteBranch);
    String output = myGit.runCommand(handler).getOutputOrThrow();
    return output != null && !output.isEmpty();
  }
}
