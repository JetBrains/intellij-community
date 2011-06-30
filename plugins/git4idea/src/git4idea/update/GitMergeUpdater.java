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
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.ChangeListViewerDialog;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import git4idea.GitUtil;
import git4idea.branch.GitBranchPair;
import git4idea.commands.*;
import git4idea.merge.GitMergeConflictResolver;
import git4idea.merge.GitMerger;
import git4idea.ui.GitUIUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles <code>git pull</code> via merge.
 */
public class GitMergeUpdater extends GitUpdater {
  private static final Logger LOG = Logger.getInstance(GitMergeUpdater.class);

  private final ChangeListManager myChangeListManager;

  public GitMergeUpdater(Project project,
                         VirtualFile root,
                         final Map<VirtualFile, GitBranchPair> trackedBranches,
                         ProgressIndicator progressIndicator,
                         UpdatedFiles updatedFiles) {
    super(project, root, trackedBranches, progressIndicator, updatedFiles);
    myChangeListManager = ChangeListManager.getInstance(myProject);
  }

  @Override
  @NotNull
  protected GitUpdateResult doUpdate() {
    LOG.info("doUpdate ");
    final GitMerger merger = new GitMerger(myProject);
    final GitLineHandler mergeHandler = new GitLineHandler(myProject, myRoot, GitCommand.MERGE);
    mergeHandler.addParameters("--no-stat", "-v");
    mergeHandler.addParameters(myTrackedBranches.get(myRoot).getTracked().getName());

    final MergeLineListener mergeLineListener = new MergeLineListener();
    mergeHandler.addLineListener(mergeLineListener);

    final GitTask mergeTask = new GitTask(myProject, mergeHandler, "Merging changes");
    mergeTask.setProgressIndicator(myProgressIndicator);
    mergeTask.setProgressAnalyzer(new GitStandardProgressAnalyzer());
    final AtomicReference<GitUpdateResult> updateResult = new AtomicReference<GitUpdateResult>();
    final AtomicBoolean failure = new AtomicBoolean();
    mergeTask.executeInBackground(true, new GitTaskResultHandlerAdapter() {
      @Override protected void onSuccess() {
        updateResult.set(GitUpdateResult.SUCCESS);
      }

      @Override protected void onCancel() {
        cancel();
        updateResult.set(GitUpdateResult.CANCEL);
      }

      @Override protected void onFailure() {
        failure.set(true);
      }
    });

    if (failure.get()) {
      updateResult.set(handleMergeFailure(mergeLineListener, merger, mergeHandler));
    }
    return updateResult.get();
  }

  @NotNull
  private GitUpdateResult handleMergeFailure(MergeLineListener mergeLineListener, final GitMerger merger, GitLineHandler mergeHandler) {
    final MergeError error = mergeLineListener.getMergeError();
    LOG.info("doUpdate merge error: " + error);
    if (error == MergeError.CONFLICT) {
      final boolean allMerged =
        new GitMergeConflictResolver(myProject, false, "Merge conflicts detected. Resolve them before continuing update.",
                                     "Can't complete update", "") {
          @Override protected boolean proceedIfNothingToMerge() throws VcsException {
            merger.mergeCommit(myRoot);
            return true;
          }

          @Override protected boolean proceedAfterAllMerged() throws VcsException {
            merger.mergeCommit(myRoot);
            return true;
          }
        }.merge(Collections.singleton(myRoot));
      return allMerged ? GitUpdateResult.SUCCESS : GitUpdateResult.INCOMPLETE;
    }
    else if (error == MergeError.LOCAL_CHANGES) {
      final List<FilePath> paths = getFilesOverwrittenByMerge(mergeLineListener.getOutput());
      final Collection<Change> changes = getLocalChangesFilteredByFiles(paths);
      final ChangeListViewerDialog dialog = new ChangeListViewerDialog(myProject, changes, false) {
        @Override protected String getDescription() {
          return "Your local changes to the following files would be overwritten by merge.<br/>" +
                            "Please, commit your changes or stash them before you can merge.";
        }
      };
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        @Override public void run() {
          dialog.show();
        }
      });
      return GitUpdateResult.ERROR;
    } else {
      GitUIUtil.notifyImportantError(myProject, "Error merging", GitUIUtil.stringifyErrors(mergeHandler.errors()));
      return GitUpdateResult.ERROR;
    }
  }

  @Override
  public boolean isSaveNeeded() {
    try {
      if (hasStagedChanges()) {
        return true;
      }
    }
    catch (VcsException e) {
      LOG.info("isSaveNeeded failed to check staging area", e);
      return true;
    }

    // git log --name-status master..origin/master
    GitBranchPair gitBranchPair = myTrackedBranches.get(myRoot);
    String currentBranch = gitBranchPair.getBranch().getName();
    String remoteBranch = gitBranchPair.getTracked().getName();
    try {
      final Collection<String> remotelyChanged = getRemotelyChangedPaths(currentBranch, remoteBranch);
      final List<File> locallyChanged = myChangeListManager.getAffectedPaths();
      for (File localPath : locallyChanged) {
        if (remotelyChanged.contains(FilePathsHelper.convertPath(localPath.getPath()))) {
         // found a file which was changed locally and remotely => need to save
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
   * git diff --name-only --cached
   * @return true if there is anything in the staging area, false if the staging area is empty.
   */
  private boolean hasStagedChanges() throws VcsException {
    final GitSimpleHandler diff = new GitSimpleHandler(myProject, myRoot, GitCommand.DIFF);
    diff.addParameters("--name-only", "--cached");
    diff.setNoSSH(true);
    diff.setStdoutSuppressed(true);
    diff.setStderrSuppressed(true);
    final String output = diff.run();
    return !output.trim().isEmpty();
  }

  private void cancel() {
    try {
      GitSimpleHandler h = new GitSimpleHandler(myProject, myRoot, GitCommand.RESET);
      h.setNoSSH(true);
      h.addParameters("-v", "--merge");
      h.run();
    } catch (VcsException e) {
      LOG.info("cancel git reset --merge", e);
      GitUIUtil.notifyImportantError(myProject, "Couldn't reset merge", e.getLocalizedMessage());
    }
  }

  // parses the output of merge conflict returning files which would be overwritten by merge. These files will be stashed.
  private List<FilePath> getFilesOverwrittenByMerge(@NotNull List<String> mergeOutput) {
    final List<FilePath> paths = new ArrayList<FilePath>();
    for  (String line : mergeOutput) {
      if (StringUtil.isEmptyOrSpaces(line)) {
        continue;
      }
      if (line.contains("Please, commit your changes or stash them before you can merge")) {
        break;
      }
      line = line.trim();

      final String path;
      try {
        path = myRoot.getPath() + "/" + GitUtil.unescapePath(line);
        final File file = new File(path);
        if (file.exists()) {
          paths.add(new FilePathImpl(file, false));
        }
      } catch (VcsException e) { // just continue
      }
    }
    return paths;
  }

  private Collection<Change> getLocalChangesFilteredByFiles(List<FilePath> paths) {
    final Collection<Change> changes = new HashSet<Change>();
    for(LocalChangeList list : myChangeListManager.getChangeLists()) {
      for (Change change : list.getChanges()) {
        final ContentRevision afterRevision = change.getAfterRevision();
        final ContentRevision beforeRevision = change.getBeforeRevision();
        if ((afterRevision != null && paths.contains(afterRevision.getFile())) || (beforeRevision != null && paths.contains(beforeRevision.getFile()))) {
          changes.add(change);
        }
      }
    }
    return changes;
  }

  private enum MergeError {
    CONFLICT,
    LOCAL_CHANGES,
    OTHER
  }

  private static class MergeLineListener extends GitLineHandlerAdapter {
    private MergeError myMergeError;
    private List<String> myOutput = new ArrayList<String>();
    private boolean myLocalChangesError = false;

    @Override
    public void onLineAvailable(String line, Key outputType) {
      if (myLocalChangesError) {
        myOutput.add(line);
      } else if (line.contains("Automatic merge failed; fix conflicts and then commit the result")) {
        myMergeError = MergeError.CONFLICT;
      } else if (line.contains("Your local changes to the following files would be overwritten by merge")) {
        myMergeError = MergeError.LOCAL_CHANGES;
        myLocalChangesError = true;
      }
    }

    public MergeError getMergeError() {
      return myMergeError;
    }

    public List<String> getOutput() {
      return myOutput;
    }
  }
}
