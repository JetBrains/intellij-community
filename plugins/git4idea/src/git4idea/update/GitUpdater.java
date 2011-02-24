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
import git4idea.GitBranch;
import git4idea.GitRevisionNumber;
import git4idea.GitVcs;
import git4idea.config.GitConfigUtil;
import git4idea.config.GitVcsSettings;
import git4idea.merge.MergeChangeCollector;

import java.util.ArrayList;

/**
 * Updates a single repository via merge or rebase.
 * @see GitRebaseUpdater
 * @see GitMergeUpdater
 */
public abstract class GitUpdater {
  private static final Logger LOG = Logger.getInstance(GitUpdater.class);

  protected final Project myProject;
  protected final VirtualFile myRoot;
  protected final ProgressIndicator myProgressIndicator;
  protected final UpdatedFiles myUpdatedFiles;
  protected final AbstractVcsHelper myVcsHelper;
  protected final GitVcs myVcs;

  protected GitRevisionNumber myBefore; // The revision that was before update

  protected GitUpdater(Project project, VirtualFile root, ProgressIndicator progressIndicator, UpdatedFiles updatedFiles) {
    myProject = project;
    myRoot = root;
    myProgressIndicator = progressIndicator;
    myUpdatedFiles = updatedFiles;
    myVcsHelper = AbstractVcsHelper.getInstance(project);
    myVcs = GitVcs.getInstance(project);
  }

  /**
   * Returns proper updater based on the update policy (merge or rebase) selected by user or stored in his .git/config
   *
   * @param root
   * @param progressIndicator
   * @return {@link GitMergeUpdater} or {@link GitRebaseUpdater}.
   */
  public static GitUpdater getUpdater(Project project, VirtualFile root, ProgressIndicator progressIndicator, UpdatedFiles updatedFiles) {
    final GitVcsSettings settings = GitVcsSettings.getInstance(project);
    if (settings == null) {
      return getDefaultUpdaterForBranch(project, root, progressIndicator, updatedFiles);
    }
    switch (settings.getUpdateType()) {
      case REBASE:
        return new GitRebaseUpdater(project, root, progressIndicator, updatedFiles);
      case MERGE:
        return new GitMergeUpdater(project, root, progressIndicator, updatedFiles);
      case BRANCH_DEFAULT:
        // use default for the branch
        return getDefaultUpdaterForBranch(project, root, progressIndicator, updatedFiles);
    }
    return getDefaultUpdaterForBranch(project, root, progressIndicator, updatedFiles);
  }

  private static GitUpdater getDefaultUpdaterForBranch(Project project, VirtualFile root, ProgressIndicator progressIndicator, UpdatedFiles updatedFiles) {
    try {
      final GitBranch branchName = GitBranch.current(project, root);
      final String rebase = GitConfigUtil.getValue(project, root, "branch." + branchName + ".rebase");
      if (rebase != null && rebase.equalsIgnoreCase("true")) {
        return new GitRebaseUpdater(project, root, progressIndicator, updatedFiles);
      }
    } catch (VcsException e) {
      LOG.info("getDefaultUpdaterForBranch branch", e);
    }
    return new GitMergeUpdater(project, root, progressIndicator, updatedFiles);
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
}
