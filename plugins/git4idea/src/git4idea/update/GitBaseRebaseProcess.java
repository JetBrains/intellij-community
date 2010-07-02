/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFile;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChange;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitBranch;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.changes.GitChangeUtils;
import git4idea.commands.*;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;
import git4idea.rebase.GitRebaseUtils;
import git4idea.ui.GitConvertFilesDialog;
import git4idea.ui.GitUIUtil;
import git4idea.vfs.GitVFSListener;

import javax.swing.event.ChangeEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract class that implement rebase operation for several roots based on rebase operation (for example update operation)
 */
public abstract class GitBaseRebaseProcess {
  /**
   * The logger
   */
  private static final Logger LOG = Logger.getInstance("#git4idea.update.GitUpdateProcess");
  /**
   * The context project
   */
  protected Project myProject;
  /**
   * The vcs service
   */
  protected GitVcs myVcs;
  /**
   * The exception list
   */
  protected List<VcsException> myExceptions;
  /**
   * Copy of local change list
   */
  private List<LocalChangeList> myListsCopy;
  /**
   * The changes sorted by root
   */
  private final Map<VirtualFile, List<Change>> mySortedChanges = new HashMap<VirtualFile, List<Change>>();
  /**
   * The change list manager
   */
  private final ChangeListManagerEx myChangeManager;
  /**
   * Roots to stash
   */
  private final HashSet<VirtualFile> myRootsToStash = new HashSet<VirtualFile>();
  /**
   * True if the stash was created (root local variable)
   */
  private boolean stashCreated;
  /**
   * The stash message
   */
  private String myStashMessage;
  /**
   * Shelve manager instance
   */
  private ShelveChangesManager myShelveManager;
  /**
   * The shelved change list (used when {@code SHELVE} policy is selected)
   */
  private ShelvedChangeList myShelvedChangeList;
  /**
   * The progress indicator to use
   */
  private ProgressIndicator myProgressIndicator;

  public GitBaseRebaseProcess(final GitVcs vcs, final Project project, List<VcsException> exceptions) {
    myVcs = vcs;
    myProject = project;
    myExceptions = exceptions;
    myChangeManager = (ChangeListManagerEx)ChangeListManagerEx.getInstance(myProject);
  }

  /**
   * Perform rebase operation
   *
   * @param progressIndicator the progress indicator to use
   * @param roots             the vcs roots
   */
  public void doUpdate(ProgressIndicator progressIndicator, Set<VirtualFile> roots) {
    ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    projectManager.blockReloadingProjectOnExternalChanges();
    this.myProgressIndicator = progressIndicator;
    try {
      if (areRootsUnderRebase(roots)) return;
      if (!saveProjectChangesBeforeUpdate()) return;
      try {
        for (final VirtualFile root : roots) {
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
            final Ref<Boolean> cancelled = new Ref<Boolean>(false);
            final Ref<Throwable> ex = new Ref<Throwable>();
            try {
              saveRootChangesBeforeUpdate(root);
              try {
                markStart(root);
                try {
                  GitLineHandler h = makeStartHandler(root);
                  RebaseConflictDetector rebaseConflictDetector = new RebaseConflictDetector();
                  h.addLineListener(rebaseConflictDetector);
                  try {
                    GitHandlerUtil
                      .doSynchronouslyWithExceptions(h, progressIndicator, GitHandlerUtil.formatOperationName("Updating", root));
                  }
                  finally {
                    if (!rebaseConflictDetector.isRebaseConflict()) {
                      myExceptions.addAll(h.errors());
                    }
                    cleanupHandler(root, h);
                  }
                  while (rebaseConflictDetector.isRebaseConflict() && !cancelled.get()) {
                    mergeFiles(root, cancelled, ex);
                    //noinspection ThrowableResultOfMethodCallIgnored
                    if (ex.get() != null) {
                      //noinspection ThrowableResultOfMethodCallIgnored
                      throw GitUtil.rethrowVcsException(ex.get());
                    }
                    checkLocallyModified(root, cancelled, ex);
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
                                                         GitBundle.getString("update.rebase.no.change.cancel")}, 0,
                                                       Messages.getErrorIcon());
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
                  if (cancelled.get()) {
                    //noinspection ThrowableInstanceNeverThrown
                    myExceptions.add(new VcsException("The update process was cancelled for " + root.getPresentableUrl()));
                    doRebase(progressIndicator, root, rebaseConflictDetector, "--abort");
                  }
                }
                finally {
                  markEnd(root, cancelled.get());
                }
              }
              finally {
                restoreRootChangesAfterUpdate(root);
              }
            }
            finally {
              mergeFiles(root, cancelled, ex);
              //noinspection ThrowableResultOfMethodCallIgnored
              if (ex.get() != null) {
                //noinspection ThrowableResultOfMethodCallIgnored
                myExceptions.add(GitUtil.rethrowVcsException(ex.get()));
              }
            }
          }
          catch (VcsException ex) {
            myExceptions.add(ex);
          }
        }
      }
      finally {
        restoreProjectChangesAfterUpdate();
      }
    }
    finally {
      projectManager.unblockReloadingProjectOnExternalChanges();
    }
  }

  /**
   * Restore project changes after update
   */
  private void restoreProjectChangesAfterUpdate() {
    if (getUpdatePolicy() == GitVcsSettings.UpdateChangesPolicy.SHELVE) {
      if (myShelvedChangeList != null) {
        // The changes are temporary copied to the first local change list, the next operation will restore them back
        myProgressIndicator.setText(GitBundle.getString("update.unshelving.changes"));
        VirtualFile baseDir = myProject.getBaseDir();
        assert baseDir != null;
        String projectPath = baseDir.getPath() + "/";
        // Refresh files that might be affected by unshelve
        HashSet<File> filesToRefresh = new HashSet<File>();
        for (ShelvedChange c : myShelvedChangeList.getChanges()) {
          if (c.getBeforePath() != null) {
            filesToRefresh.add(new File(projectPath + c.getBeforePath()));
          }
          if (c.getAfterPath() != null) {
            filesToRefresh.add(new File(projectPath + c.getAfterPath()));
          }
        }
        for (ShelvedBinaryFile f : myShelvedChangeList.getBinaryFiles()) {
          if (f.BEFORE_PATH != null) {
            filesToRefresh.add(new File(projectPath + f.BEFORE_PATH));
          }
          if (f.AFTER_PATH != null) {
            filesToRefresh.add(new File(projectPath + f.BEFORE_PATH));
          }
        }
        LocalFileSystem.getInstance().refreshIoFiles(filesToRefresh);
        // Do unshevle
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          public void run() {
            GitVFSListener l = GitVcs.getInstance(myProject).getVFSListener();
            l.setEventsSuppressed(true);
            try {
              myShelveManager
                .unshelveChangeList(myShelvedChangeList, myShelvedChangeList.getChanges(), myShelvedChangeList.getBinaryFiles(),
                                    myChangeManager.getDefaultChangeList(), false);
            }
            finally {
              l.setEventsSuppressed(false);
            }
          }
        });
        Collection<FilePath> paths = new ArrayList<FilePath>();
        for (ShelvedChange c : myShelvedChangeList.getChanges()) {
          if (c.getBeforePath() == null || !c.getBeforePath().equals(c.getAfterPath()) || c.getFileStatus() == FileStatus.ADDED) {
            paths.add(VcsUtil.getFilePath(projectPath + c.getAfterPath()));
          }
        }
        for (ShelvedBinaryFile f : myShelvedChangeList.getBinaryFiles()) {
          if (f.BEFORE_PATH == null || !f.BEFORE_PATH.equals(f.AFTER_PATH) || f.getFileStatus() == FileStatus.ADDED) {
            paths.add(VcsUtil.getFilePath(projectPath + f.AFTER_PATH));
          }
        }
        Map<VirtualFile, List<FilePath>> map = GitUtil.sortGitFilePathsByGitRoot(paths);
        for (Map.Entry<VirtualFile, List<FilePath>> e : map.entrySet()) {
          try {
            GitFileUtils.addPaths(myProject, e.getKey(), e.getValue());
          }
          catch (VcsException e1) {
            myExceptions.add(e1);
          }
        }
      }
    }
    // Move files back to theirs change lists
    if (getUpdatePolicy() == GitVcsSettings.UpdateChangesPolicy.SHELVE || getUpdatePolicy() == GitVcsSettings.UpdateChangesPolicy.STASH) {
      VcsDirtyScopeManager m = VcsDirtyScopeManager.getInstance(myProject);
      final boolean isStash = getUpdatePolicy() == GitVcsSettings.UpdateChangesPolicy.STASH;
      HashSet<File> filesToRefresh = isStash ? new HashSet<File>() : null;
      for (LocalChangeList changeList : myListsCopy) {
        for (Change c : changeList.getChanges()) {
          ContentRevision after = c.getAfterRevision();
          if (after != null) {
            m.fileDirty(after.getFile());
            if (isStash) {
              filesToRefresh.add(after.getFile().getIOFile());
            }
          }
          ContentRevision before = c.getBeforeRevision();
          if (before != null) {
            m.fileDirty(before.getFile());
            if (isStash) {
              filesToRefresh.add(before.getFile().getIOFile());
            }
          }
        }
      }
      if (isStash) {
        LocalFileSystem.getInstance().refreshIoFiles(filesToRefresh);
      }
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        public void run() {
          myChangeManager.invokeAfterUpdate(new Runnable() {
            public void run() {
              for (LocalChangeList changeList : myListsCopy) {
                final Collection<Change> changes = changeList.getChanges();
                if (!changes.isEmpty()) {
                  LOG.debug("After restoring files: moving " + changes.size() + " changes to '" + changeList.getName() + "'");
                  myChangeManager.moveChangesTo(changeList, changes.toArray(new Change[changes.size()]));
                }
              }
            }
          }, InvokeAfterUpdateMode.BACKGROUND_NOT_CANCELLABLE, GitBundle.getString("update.restoring.change.lists"),
                                            ModalityState.NON_MODAL);
        }
      });
    }
  }

  /**
   * Restore per-root changes after update
   *
   * @param root the just updated root
   */
  private void restoreRootChangesAfterUpdate(VirtualFile root) {
    if (stashCreated && getUpdatePolicy() == GitVcsSettings.UpdateChangesPolicy.STASH) {
      myProgressIndicator.setText(GitHandlerUtil.formatOperationName("Unstashing changes to", root));
      unstash(root);
    }
  }

  /**
   * Save per-root changes before update
   *
   * @param root the root to save changes for
   * @throws VcsException if there is a problem with saving changes
   */
  private void saveRootChangesBeforeUpdate(VirtualFile root) throws VcsException {
    if (getUpdatePolicy() == GitVcsSettings.UpdateChangesPolicy.STASH) {
      stashCreated = false;
      if (myRootsToStash.contains(root)) {
        myProgressIndicator.setText(GitHandlerUtil.formatOperationName("Stashing changes from", root));
        stashCreated = GitStashUtils.saveStash(myProject, root, myStashMessage);
      }
    }
  }

  /**
   * Do the project level work required to save the changes
   *
   * @return false, if update process needs to be aborted
   */
  private boolean saveProjectChangesBeforeUpdate() {
    if (getUpdatePolicy() == GitVcsSettings.UpdateChangesPolicy.STASH || getUpdatePolicy() == GitVcsSettings.UpdateChangesPolicy.SHELVE) {
      myStashMessage = makeStashMessage();
      myListsCopy = myChangeManager.getChangeListsCopy();
      for (LocalChangeList l : myListsCopy) {
        final Collection<Change> changeCollection = l.getChanges();
        LOG.debug("Stashing " + changeCollection.size() + " changes from '" + l.getName() + "'");
        for (Change c : changeCollection) {
          ContentRevision after = c.getAfterRevision();
          if (after != null) {
            VirtualFile r = GitUtil.getGitRootOrNull(after.getFile());
            if (r != null) {
              myRootsToStash.add(r);
              List<Change> changes = mySortedChanges.get(r);
              if (changes == null) {
                changes = new ArrayList<Change>();
                mySortedChanges.put(r, changes);
              }
              changes.add(c);
            }
          }
          else {
            ContentRevision before = c.getBeforeRevision();
            if (before != null) {
              VirtualFile r = GitUtil.getGitRootOrNull(before.getFile());
              if (r != null) {
                myRootsToStash.add(r);
              }
            }
          }
        }
      }
    }
    if (getUpdatePolicy() == GitVcsSettings.UpdateChangesPolicy.STASH) {
      GitVcsSettings settings = GitVcsSettings.getInstance(myProject);
      boolean result = GitConvertFilesDialog.showDialogIfNeeded(myProject, settings, mySortedChanges, myExceptions);
      if (!result) {
        if (myExceptions.isEmpty()) {
          //noinspection ThrowableInstanceNeverThrown
          myExceptions.add(new VcsException("Conversion of line separators failed."));
        }
        return false;
      }
    }
    if (getUpdatePolicy() == GitVcsSettings.UpdateChangesPolicy.SHELVE) {
      myShelveManager = ShelveChangesManager.getInstance(myProject);
      ArrayList<Change> changes = new ArrayList<Change>();
      for (LocalChangeList l : myListsCopy) {
        changes.addAll(l.getChanges());
      }
      if (changes.size() > 0) {
        try {
          myProgressIndicator.setText(GitBundle.getString("update.shelving.changes"));
          myShelvedChangeList = myShelveManager.shelveChanges(changes, myStashMessage);
          myProject.getMessageBus().syncPublisher(ShelveChangesManager.SHELF_TOPIC).stateChanged(new ChangeEvent(this));
        }
        catch (IOException e) {
          //noinspection ThrowableInstanceNeverThrown
          myExceptions.add(new VcsException("Shelving changes failed.", e));
          return false;
        }
        catch (VcsException e) {
          myExceptions.add(e);
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Clean up the start handler
   *
   * @param root the root
   * @param h    the handler
   */
  @SuppressWarnings({"UnusedDeclaration"})
  protected void cleanupHandler(VirtualFile root, GitLineHandler h) {
    // do nothing by default
  }

  /**
   * Make handler that starts operation
   *
   * @param root the vcs root
   * @return the handler that starts rebase operation
   * @throws VcsException in if there is problem with running git
   */
  protected abstract GitLineHandler makeStartHandler(VirtualFile root) throws VcsException;

  /**
   * Unstash changes and restore them in change list
   *
   * @param root the vcs root
   */
  private void unstash(VirtualFile root) {
    try {
      GitStashUtils.popLastStash(myProject, root);
    }
    catch (final VcsException ue) {
      myExceptions.add(ue);
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        public void run() {
          GitUIUtil.showOperationError(myProject, ue, "Auto-unstash");
        }
      });
    }
  }

  /**
   * Mark the start of the operation
   *
   * @param root the vcs root
   * @throws VcsException the exception
   */
  protected void markStart(VirtualFile root) throws VcsException {

  }

  /**
   * Mark the end of the operation
   *
   * @param root      the vcs operation
   * @param cancelled true if the operation was cancelled due to update operation
   */
  protected void markEnd(VirtualFile root, boolean cancelled) {

  }

  /**
   * @return a stash message for the operation
   */
  protected abstract String makeStashMessage();

  /**
   * @return the policy of autosaving change
   */
  protected abstract GitVcsSettings.UpdateChangesPolicy getUpdatePolicy();

  /**
   * Check if some roots are under the rebase operation and show a message in this case
   *
   * @param roots the roots to check
   * @return true if some roots are being rebased
   */
  private boolean areRootsUnderRebase(Set<VirtualFile> roots) {
    Set<VirtualFile> rebasingRoots = new TreeSet<VirtualFile>(GitUtil.VIRTUAL_FILE_COMPARATOR);
    for (final VirtualFile root : roots) {
      if (GitRebaseUtils.isRebaseInTheProgress(root)) {
        rebasingRoots.add(root);
      }
    }
    if (!rebasingRoots.isEmpty()) {
      final StringBuilder files = new StringBuilder();
      for (VirtualFile r : rebasingRoots) {
        files.append(GitBundle.message("update.root.rebasing.item", r.getPresentableUrl()));
        //noinspection ThrowableInstanceNeverThrown
        myExceptions.add(new VcsException(GitBundle.message("update.root.rebasing", r.getPresentableUrl())));
      }
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        public void run() {
          Messages.showErrorDialog(myProject, GitBundle.message("update.root.rebasing.message", files.toString()),
                                   GitBundle.message("update.root.rebasing.title"));
        }
      });
      return true;
    }
    return false;
  }

  /**
   * Merge files
   *
   * @param root      the project root
   * @param cancelled the cancelled indicator
   * @param ex        the exception holder
   */
  private void mergeFiles(final VirtualFile root, final Ref<Boolean> cancelled, final Ref<Throwable> ex) {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      public void run() {
        try {
          List<VirtualFile> affectedFiles = GitChangeUtils.unmergedFiles(myProject, root);
          while (affectedFiles.size() != 0) {
            AbstractVcsHelper.getInstance(myProject).showMergeDialog(affectedFiles, myVcs.getMergeProvider());
            affectedFiles = GitChangeUtils.unmergedFiles(myProject, root);
            if (affectedFiles.size() != 0) {
              int result = Messages
                .showYesNoDialog(myProject, GitBundle.message("update.rebase.unmerged", affectedFiles.size(), root.getPresentableUrl()),
                                 GitBundle.getString("update.rebase.unmerged.title"), Messages.getErrorIcon());
              if (result != 0) {
                cancelled.set(true);
                return;
              }
            }
          }
        }
        catch (Throwable t) {
          ex.set(t);
        }
      }
    });
  }

  /**
   * Check and process locally modified files
   *
   * @param root      the project root
   * @param cancelled the cancelled indicator
   * @param ex        the exception holder
   */
  private void checkLocallyModified(final VirtualFile root, final Ref<Boolean> cancelled, final Ref<Throwable> ex) {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      public void run() {
        try {
          if (!GitUpdateLocallyModifiedDialog.showIfNeeded(myProject, root)) {
            cancelled.set(true);
          }
        }
        catch (Throwable t) {
          ex.set(t);
        }
      }
    });
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
    GitLineHandler rh = new GitLineHandler(myProject, root, GitCommand.REBASE);
    // ignore failure for abort
    rh.ignoreErrorCode(1);
    rh.addParameters(action);
    rebaseConflictDetector.reset();
    rh.addLineListener(rebaseConflictDetector);
    if (!"--abort".equals(action)) {
      configureRebaseEditor(root, rh);
    }
    try {
      GitHandlerUtil.doSynchronouslyWithExceptions(rh, progressIndicator, GitHandlerUtil.formatOperationName("Rebasing ", root));
    }
    finally {
      cleanupHandler(root, rh);
    }
  }

  /**
   * Configure rebase editor
   *
   * @param root the vcs root
   * @param h    the handler to configure
   */
  @SuppressWarnings({"UnusedDeclaration"})
  protected void configureRebaseEditor(VirtualFile root, GitLineHandler h) {
    // do nothing by default
  }

  /**
   * The detector of conflict conditions for rebase operation
   */
  static class RebaseConflictDetector extends GitLineHandlerAdapter {
    /**
     * The line that indicates that there is a rebase conflict.
     */
    private final static String[] REBASE_CONFLICT_INDICATORS = {"When you have resolved this problem run \"git rebase --continue\".",
      "Automatic cherry-pick failed.  After resolving the conflicts,"};
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
      for (String i : REBASE_CONFLICT_INDICATORS) {
        if (line.startsWith(i)) {
          rebaseConflict.set(true);
          break;
        }
      }
      if (line.startsWith(REBASE_NO_CHANGE_INDICATOR)) {
        noChange.set(true);
      }
    }
  }
}
