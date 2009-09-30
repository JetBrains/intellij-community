/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.SequentialUpdatesContext;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vcs.update.UpdateSession;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import git4idea.GitBranch;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.changes.GitChangeUtils;
import git4idea.commands.*;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;
import git4idea.merge.MergeChangeCollector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Git update environment implementation. The environment does just a simple
 * {@code git pull -v} for each content root.
 */
public class GitUpdateEnvironment implements UpdateEnvironment {
  /**
   * The vcs instance
   */
  private GitVcs myVcs;
  /**
   * The context project
   */
  private final Project myProject;
  /**
   * The project settings
   */
  private final GitVcsSettings mySettings;

  /**
   * A constructor from settings
   *
   * @param project a project
   */
  public GitUpdateEnvironment(@NotNull Project project, @NotNull GitVcs vcs, GitVcsSettings settings) {
    myVcs = vcs;
    myProject = project;
    mySettings = settings;
  }

  /**
   * {@inheritDoc}
   */
  public void fillGroups(UpdatedFiles updatedFiles) {
    //unused, there are no custom categories yet
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  public UpdateSession updateDirectories(@NotNull FilePath[] filePaths,
                                         UpdatedFiles updatedFiles,
                                         ProgressIndicator progressIndicator,
                                         @NotNull Ref<SequentialUpdatesContext> sequentialUpdatesContextRef)
    throws ProcessCanceledException {
    List<VcsException> exceptions = new ArrayList<VcsException>();
    for (final VirtualFile root : GitUtil.gitRoots(Arrays.asList(filePaths))) {
      try {
        // check if there is a remote for the branch
        final GitBranch branch = GitBranch.current(myProject, root);
        if (branch == null) {
          continue;
        }
        final String value = branch.getTrackedRemoteName(myProject, root);
        if (value == null || value.length() == 0) {
          continue;
        }
        boolean stashCreated =
          mySettings.UPDATE_STASH && GitStashUtils.saveStash(myProject, root, "Uncommitted changes before update operation");
        try {
          // remember the current position
          GitRevisionNumber before = GitRevisionNumber.resolve(myProject, root, "HEAD");
          // do pull
          GitLineHandler h = new GitLineHandler(myProject, root, GitHandler.PULL);
          // ignore merge failure for the pull
          h.ignoreErrorCode(1);
          switch (mySettings.UPDATE_TYPE) {
            case REBASE:
              h.addParameters("--rebase");
              break;
            case MERGE:
              h.addParameters("--no-rebase");
              break;
            case BRANCH_DEFAULT:
              // use default for the branch
              break;
            default:
              assert false : "Unknown update type: " + mySettings.UPDATE_TYPE;
          }
          h.addParameters("--no-stat");
          h.addParameters("-v");
          final Ref<Boolean> cancelled = new Ref<Boolean>(false);
          try {
            RebaseConflictDetector rebaseConflictDetector = new RebaseConflictDetector();
            h.addLineListener(rebaseConflictDetector);
            try {
              GitHandlerUtil.doSynchronouslyWithExceptions(h, progressIndicator);
            }
            finally {
              if (!rebaseConflictDetector.isRebaseConflict()) {
                exceptions.addAll(h.errors());
              }
            }
            do {
              final Ref<Throwable> ex = new Ref<Throwable>();
              UIUtil.invokeAndWaitIfNeeded(new Runnable() {
                public void run() {
                  try {
                    List<VirtualFile> affectedFiles = GitChangeUtils.unmergedFiles(myProject, root);
                    while (affectedFiles.size() != 0) {
                      AbstractVcsHelper.getInstance(myProject).showMergeDialog(affectedFiles, myVcs.getMergeProvider());
                      affectedFiles = GitChangeUtils.unmergedFiles(myProject, root);
                      if (affectedFiles.size() != 0) {
                        int result = Messages.showYesNoDialog(myProject, GitBundle.message("update.rebase.unmerged", affectedFiles.size(),
                                                                                           root.getPresentableUrl()),
                                                              GitBundle.getString("update.rebase.unmerged.title"), Messages.getErrorIcon());
                        if (result != 0) {
                          cancelled.set(true);
                          return;
                        }
                      }
                    }
                    if(!GitUpdateLocallyModifiedDialog.showIfNeeded(myProject, root)) {
                      cancelled.set(true);
                    }
                  }
                  catch (Throwable t) {
                    ex.set(t);
                  }
                }
              });
              //noinspection ThrowableResultOfMethodCallIgnored
              if (ex.get() != null) {
                //noinspection ThrowableResultOfMethodCallIgnored
                throw GitUtil.rethrowVcsException(ex.get());
              }
              if (cancelled.get()) {
                break;
              }
              doRebase(progressIndicator, root, rebaseConflictDetector, "--continue");
              final Ref<Integer> result = new Ref<Integer>();
              noChangeLoop:
              while (rebaseConflictDetector.isNoChange()) {
                UIUtil.invokeAndWaitIfNeeded(new Runnable() {
                  public void run() {
                    int rc = Messages.showDialog(myProject, GitBundle.message("update.rebase.no.change", root.getPresentableUrl()),
                                                 GitBundle.getString("update.rebase.no.change.title"),
                                                 new String[]{GitBundle.getString("update.rebase.no.change.skip"),
                                                   GitBundle.getString("update.rebase.no.change.retry"),
                                                   GitBundle.getString("update.rebase.no.change.cancel")}, 0, Messages.getErrorIcon());
                    result.set(rc);
                  }
                });
                switch (result.get()) {
                  case 0:
                    doRebase(progressIndicator, root, rebaseConflictDetector, "--skip");
                    continue noChangeLoop;
                  case 1:
                    continue noChangeLoop;
                  case 2:
                    cancelled.set(true);
                    break noChangeLoop;
                }
              }
            }
            while (rebaseConflictDetector.isRebaseConflict() && !cancelled.get());
            if (cancelled.get()) {
              //noinspection ThrowableInstanceNeverThrown
              exceptions.add(new VcsException("The update process was cancelled for " + root.getPresentableUrl()));
              doRebase(progressIndicator, root, rebaseConflictDetector, "--abort");
            }
          }
          finally {
            if (!cancelled.get()) {
              // find out what have changed
              MergeChangeCollector collector = new MergeChangeCollector(myProject, root, before, updatedFiles);
              collector.collect(exceptions);
            }
          }
        }
        finally {
          if (stashCreated) {
            GitStashUtils.popLastStash(myProject, root);
          }
        }
      }
      catch (VcsException ex) {
        exceptions.add(ex);
      }
    }
    return new GitUpdateSession(exceptions);
  }

  /**
   * Do rebase operation as part of update operator
   *
   * @param progressIndicator      the progress indicator for the update
   * @param root                   the vcs root
   * @param rebaseConflictDetector the detector of conflicts in rebase operation
   * @param action                 the rebase action to execute
   */
  private void doRebase(ProgressIndicator progressIndicator,
                        VirtualFile root,
                        RebaseConflictDetector rebaseConflictDetector,
                        final String action) {
    GitLineHandler rh = new GitLineHandler(myProject, root, GitHandler.REBASE);
    // ignore failure for abort
    rh.ignoreErrorCode(1);
    rh.addParameters(action);
    rebaseConflictDetector.reset();
    rh.addLineListener(rebaseConflictDetector);
    GitHandlerUtil.doSynchronouslyWithExceptions(rh, progressIndicator);
  }

  /**
   * {@inheritDoc}
   */
  public boolean validateOptions(Collection<FilePath> filePaths) {
    for (FilePath p : filePaths) {
      if (!GitUtil.isUnderGit(p)) {
        return false;
      }
    }
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Nullable
  public Configurable createConfigurable(Collection<FilePath> files) {
    return new GitUpdateConfigurable(mySettings);
  }

  /**
   * The detector of conflict conditions for rebase operation
   */
  static class RebaseConflictDetector extends GitLineHandlerAdapter {
    /**
     * The line that indicates that there is a rebase conflict.
     */
    private final static String REBASE_CONFLICT_INDICATOR = "When you have resolved this problem run \"git rebase --continue\".";
    /**
     * The line that indicates "no change" condition.
     */
    private static final String REBASE_NO_CHANGE_INDICATOR = "No changes - did you forget to use 'git add'?";
    /**
     * if true, the rebase conflict happened
     */
    AtomicBoolean rebaseConflict = new AtomicBoolean(false);
    /**
     * if true, the no changes were detected in the rebase operations
     */
    AtomicBoolean noChange = new AtomicBoolean(false);

    /**
     * Reset detector before new operation
     */
    public void reset() {
      rebaseConflict.set(false);
      noChange.set(false);
    }

    /**
     * @return true if "no change" condition was detected during the operation
     */
    public boolean isNoChange() {
      return noChange.get();
    }

    /**
     * @return true if conflict during rebase was detected
     */
    public boolean isRebaseConflict() {
      return rebaseConflict.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLineAvailable(String line, Key outputType) {
      if (line.startsWith(REBASE_CONFLICT_INDICATOR)) {
        rebaseConflict.set(true);
      }
      if (line.startsWith(REBASE_NO_CHANGE_INDICATOR)) {
        noChange.set(true);
      }
    }
  }
}
