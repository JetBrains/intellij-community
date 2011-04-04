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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitBranch;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.branch.GitBranchPair;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.commands.StringScanner;
import git4idea.config.GitConfigUtil;
import git4idea.config.GitVcsSettings;
import git4idea.merge.MergeChangeCollector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

/**
 * Updates a single repository via merge or rebase.
 * @see GitRebaseUpdater
 * @see GitMergeUpdater
 */
public abstract class GitUpdater {
  private static final Logger LOG = Logger.getInstance(GitUpdater.class);

  protected final Project myProject;
  protected final VirtualFile myRoot;
  protected final GitUpdateProcess myUpdateProcess;
  protected final ProgressIndicator myProgressIndicator;
  protected final UpdatedFiles myUpdatedFiles;
  protected final AbstractVcsHelper myVcsHelper;
  protected final GitVcs myVcs;

  protected GitRevisionNumber myBefore; // The revision that was before update

  protected GitUpdater(Project project,
                       VirtualFile root,
                       GitUpdateProcess gitUpdateProcess,
                       ProgressIndicator progressIndicator,
                       UpdatedFiles updatedFiles) {
    myProject = project;
    myRoot = root;
    myUpdateProcess = gitUpdateProcess;
    myProgressIndicator = progressIndicator;
    myUpdatedFiles = updatedFiles;
    myVcsHelper = AbstractVcsHelper.getInstance(project);
    myVcs = GitVcs.getInstance(project);
  }

  /**
   * Returns proper updater based on the update policy (merge or rebase) selected by user or stored in his .git/config
   *
   *
   * @param gitUpdateProcess
   * @param root
   * @param progressIndicator
   * @return {@link GitMergeUpdater} or {@link GitRebaseUpdater}.
   */
  public static GitUpdater getUpdater(Project project,
                                      GitUpdateProcess gitUpdateProcess,
                                      VirtualFile root,
                                      ProgressIndicator progressIndicator,
                                      UpdatedFiles updatedFiles) {
    final GitVcsSettings settings = GitVcsSettings.getInstance(project);
    if (settings == null) {
      return getDefaultUpdaterForBranch(project, root, gitUpdateProcess, progressIndicator, updatedFiles);
    }
    switch (settings.getUpdateType()) {
      case REBASE:
        return new GitRebaseUpdater(project, root, gitUpdateProcess, progressIndicator, updatedFiles);
      case MERGE:
        return new GitMergeUpdater(project, root, gitUpdateProcess, progressIndicator, updatedFiles);
      case BRANCH_DEFAULT:
        // use default for the branch
        return getDefaultUpdaterForBranch(project, root, gitUpdateProcess, progressIndicator, updatedFiles);
    }
    return getDefaultUpdaterForBranch(project, root, gitUpdateProcess, progressIndicator, updatedFiles);
  }

  private static GitUpdater getDefaultUpdaterForBranch(Project project,
                                                       VirtualFile root,
                                                       GitUpdateProcess gitUpdateProcess,
                                                       ProgressIndicator progressIndicator,
                                                       UpdatedFiles updatedFiles) {
    try {
      final GitBranch branchName = GitBranch.current(project, root);
      final String rebase = GitConfigUtil.getValue(project, root, "branch." + branchName + ".rebase");
      if (rebase != null && rebase.equalsIgnoreCase("true")) {
        return new GitRebaseUpdater(project, root, gitUpdateProcess, progressIndicator, updatedFiles);
      }
    } catch (VcsException e) {
      LOG.info("getDefaultUpdaterForBranch branch", e);
    }
    return new GitMergeUpdater(project, root, gitUpdateProcess, progressIndicator, updatedFiles);
  }

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
    GitBranchPair gitBranchPair = myUpdateProcess.getTrackedBranches().get(myRoot);
    String currentBranch = gitBranchPair.getBranch().getName();
    String remoteBranch = gitBranchPair.getTracked().getName();
    Collection<String> remotelyChanged = getRemotelyChangedPaths(currentBranch, remoteBranch);
    if (remotelyChanged.isEmpty()) {
      LOG.info("isSaveNeeded No remote changes, save is not needed");
      return false;
    }
    return true;
  }

  /**
   * Performs update (via rebase or merge - depending on the implementing classes).
   */
  protected abstract GitUpdateResult doUpdate();

  protected void markStart(VirtualFile root) throws VcsException {
    // remember the current position
    myBefore = GitRevisionNumber.resolve(myProject, root, "HEAD");
  }

  protected void markEnd(VirtualFile root) throws VcsException {
    // find out what have changed, this is done even if the process was cancelled.
    final MergeChangeCollector collector = new MergeChangeCollector(myProject, root, myBefore);
    final ArrayList<VcsException> exceptions = new ArrayList<VcsException>();
    collector.collect(myUpdatedFiles, exceptions);
    if (!exceptions.isEmpty()) {
      throw exceptions.get(0);
    }
  }

  /**
   * Returns paths which have changed remotely comparing to the current branch, i.e. performs
   * <code>git diff --name-only master..origin/master</code>
   */
  protected @NotNull Collection<String> getRemotelyChangedPaths(@NotNull String currentBranch, @NotNull String remoteBranch) throws VcsException {
    final GitSimpleHandler toPull = new GitSimpleHandler(myProject, myRoot, GitCommand.DIFF);
    toPull.addParameters("--name-only", "--pretty=format:");
    toPull.addParameters(currentBranch + ".." + remoteBranch);
    toPull.setNoSSH(true);
    toPull.setStdoutSuppressed(true);
    toPull.setStderrSuppressed(true);
    final String output = toPull.run();

    final Collection<String> remoteChanges = new HashSet<String>();
    for (StringScanner s = new StringScanner(output); s.hasMoreData();) {
      final String relative = s.line();
      if (StringUtil.isEmptyOrSpaces(relative)) {
        continue;
      }
      final String path = myRoot.getPath() + "/" + GitUtil.unescapePath(relative);
      remoteChanges.add(path);
    }
    return remoteChanges;
  }

}
