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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.branch.GitBranchPair;
import git4idea.commands.*;
import git4idea.merge.GitMergeConflictResolver;
import git4idea.merge.GitMerger;
import git4idea.ui.GitUIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles <code>git pull</code> via merge.
 */
public class GitMergeUpdater extends GitUpdater {
  private static final Logger LOG = Logger.getInstance(GitMergeUpdater.class);

  private final GitUpdateProcess myUpdateProcess;

  private enum MergeError {
    CONFLICT,
    LOCAL_CHANGES,
    OTHER
  }

  public GitMergeUpdater(Project project,
                         VirtualFile root,
                         GitUpdateProcess gitUpdateProcess,
                         ProgressIndicator progressIndicator,
                         UpdatedFiles updatedFiles) {
    super(project, root, progressIndicator, updatedFiles);
    myUpdateProcess = gitUpdateProcess;
  }

  @Override
  protected GitUpdateResult doUpdate() {
    final GitMerger merger = new GitMerger(myProject);
    final GitLineHandler mergeHandler = makeMergeHandler(myRoot);
    final AtomicReference<MergeError> mergeError = new AtomicReference<MergeError>(MergeError.OTHER);
    mergeHandler.addLineListener(new GitLineHandlerAdapter() {
      @Override
      public void onLineAvailable(String line, Key outputType) {
        if (line.contains("Automatic merge failed; fix conflicts and then commit the result")) {
          mergeError.set(MergeError.CONFLICT);
        } else if (line.contains("Please, commit your changes or stash them before you can merge")) {
          mergeError.set(MergeError.LOCAL_CHANGES);
        }
      }
    });

    final GitTask mergeTask = new GitTask(myProject, mergeHandler, "git merge");
    mergeTask.setExecuteResultInAwt(false);
    mergeTask.setProgressAnalyzer(new GitStandardProgressAnalyzer());
    final AtomicReference<GitUpdateResult> updateResult = new AtomicReference<GitUpdateResult>();
    mergeTask.executeInBackground(true, new GitTaskResultHandlerAdapter() {
      @Override protected void onSuccess() {
        updateResult.set(GitUpdateResult.SUCCESS);
      }

      @Override protected void onCancel() {
        cancel();
        updateResult.set(GitUpdateResult.CANCEL);
      }

      @Override protected void onFailure() {
        final MergeError error = mergeError.get();
        if (error == MergeError.CONFLICT) {
          final boolean allMerged =
            new GitMergeConflictResolver(myProject, true, "Merge conflicts detected. Resolve them before continuing update.",
                                         "Can't update", "") {
              @Override protected boolean proceedIfNothingToMerge() throws VcsException {
                merger.mergeCommit(myRoot);
                return true;
              }

              @Override protected boolean proceedAfterAllMerged() throws VcsException {
                merger.mergeCommit(myRoot);
                return true;
              }
            }.mergeFiles(Collections.singleton(myRoot));
          updateResult.set(allMerged ? GitUpdateResult.SUCCESS : GitUpdateResult.INCOMPLETE);
        } else {
          GitUIUtil.notifyImportantError(myProject, "Error merging", GitUIUtil.stringifyErrors(mergeHandler.errors()));
          updateResult.set(GitUpdateResult.ERROR);
        }
      }
    });
    return updateResult.get();
  }

  @Override
  public boolean isSaveNeeded() {
    boolean fetchSuccess = fetch();
    if (!fetchSuccess) {
      return true; // fail safe: fetch failed, will save the root.
    }

    // git log --name-status master..origin/master
    GitBranchPair gitBranchPair = myUpdateProcess.getTrackedBranches().get(myRoot);
    String currentBranch = gitBranchPair.getBranch().getName();
    String remoteBranch = gitBranchPair.getTracked().getName();
    try {
      Collection<String> remotelyChanged = getRemotelyChangedPaths(currentBranch, remoteBranch);
      Collection<FilePath> locallyChanged = myUpdateProcess.getSaver().getChangedFiles();
      for (FilePath localPath : locallyChanged) {
        if (remotelyChanged.contains(localPath.getPath())) { // found a file which was changed locally and remotely => need to save
          return true;
        }
      }
      return false;
    } catch (VcsException e) {
      LOG.info("failed to get remotely changed files for " + currentBranch + ".." + remoteBranch, e);
      return true; // fail safe
    }
  }

  /**
   * Fetches the tracked remote for current branch.
   * @return true if fetch was successful, false in the case of error.
   * @param remote
   */
  private boolean fetch() {
    final GitLineHandler h = new GitLineHandler(myProject, myRoot, GitCommand.FETCH);

    final GitTask fetchTask = new GitTask(myProject, h, "Fetching changes...");
    fetchTask.setProgressAnalyzer(new GitStandardProgressAnalyzer());
    final AtomicBoolean success = new AtomicBoolean();
    fetchTask.executeInBackground(true, new GitTaskResultHandlerAdapter() {
      @Override protected void onSuccess() {
        success.set(true);
      }

      @Override protected void onCancel() {
        LOG.info("Cancelled fetch.");
      }

      @Override protected void onFailure() {
        LOG.info("Error fetching: " + h.errors());
      }
    });
    return success.get();
  }

  // git log --name-status master..origin/master
  private @NotNull Collection<String> getRemotelyChangedPaths(String currentBranch, String remoteBranch) throws VcsException {
    final GitSimpleHandler toPull = new GitSimpleHandler(myProject, myRoot, GitCommand.LOG);
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

  private void cancel() {
    try {
      GitSimpleHandler h = new GitSimpleHandler(myProject, myRoot, GitCommand.RESET);
      h.addParameters("-v", "--merge");
      h.run();
    } catch (VcsException e) {
      LOG.info("cancel git reset --merge", e);
      GitUIUtil.notifyImportantError(myProject, "Couldn't reset merge", e.getLocalizedMessage());
    }
  }

  private GitLineHandler makeMergeHandler(VirtualFile root) {
    GitLineHandler h = new GitLineHandler(myProject, root, GitCommand.PULL);
    h.addParameters("--no-rebase");
    h.addParameters("--no-stat");
    h.addParameters("-v");
    return h;
  }
}
