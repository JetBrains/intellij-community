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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import git4idea.GitBranch;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.changes.GitChangeUtils;
import git4idea.commands.GitCommand;
import git4idea.commands.GitHandlerUtil;
import git4idea.commands.GitLineHandler;
import git4idea.commands.GitLineHandlerAdapter;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;
import git4idea.rebase.GitRebaseUtils;
import git4idea.ui.GitConvertFilesDialog;
import git4idea.ui.GitUIUtil;

import java.io.File;
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

  public GitBaseRebaseProcess(final GitVcs vcs, final Project project, List<VcsException> exceptions) {
    myVcs = vcs;
    myProject = project;
    myExceptions = exceptions;
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
    try {
      HashSet<VirtualFile> rootsToStash = new HashSet<VirtualFile>();
      List<LocalChangeList> listsCopy = null;
      ChangeListManagerEx changeManager = (ChangeListManagerEx)ChangeListManagerEx.getInstance(myProject);
      Map<VirtualFile, List<Change>> sortedChanges = new HashMap<VirtualFile, List<Change>>();
      if (isAutoStash()) {
        listsCopy = changeManager.getChangeListsCopy();
        for (LocalChangeList l : listsCopy) {
          final Collection<Change> changeCollection = l.getChanges();
          LOG.debug("Stashing " + changeCollection.size() + " changes from '" + l.getName() + "'");
          for (Change c : changeCollection) {
            if (c.getAfterRevision() != null) {
              VirtualFile r = GitUtil.getGitRootOrNull(c.getAfterRevision().getFile());
              if (r != null) {
                rootsToStash.add(r);
                List<Change> changes = sortedChanges.get(r);
                if (changes == null) {
                  changes = new ArrayList<Change>();
                  sortedChanges.put(r, changes);
                }
                changes.add(c);
              }
            }
            else if (c.getBeforeRevision() != null) {
              VirtualFile r = GitUtil.getGitRootOrNull(c.getBeforeRevision().getFile());
              if (r != null) {
                rootsToStash.add(r);
              }
            }
          }
        }
        GitVcsSettings settings = GitVcsSettings.getInstance(myProject);
        boolean result = GitConvertFilesDialog.showDialogIfNeeded(myProject, settings, sortedChanges, myExceptions);
        if (!result) {
          if (myExceptions.isEmpty()) {
            //noinspection ThrowableInstanceNeverThrown
            myExceptions.add(new VcsException("Conversion of line separators failed."));
          }
          return;
        }
      }
      if (areRootsUnderRebase(roots)) return;
      String stashMessage = makeStashMessage();
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
            boolean stashCreated = false;
            if (rootsToStash.contains(root)) {
              progressIndicator.setText(GitHandlerUtil.formatOperationName("Stashing changes from", root));
              stashCreated = GitStashUtils.saveStash(myProject, root, stashMessage);
            }
            try {
              markStart(root);
              try {
                GitLineHandler h = makeStartHandler(root);
                RebaseConflictDetector rebaseConflictDetector = new RebaseConflictDetector();
                h.addLineListener(rebaseConflictDetector);
                try {
                  GitHandlerUtil
                    .doSynchronouslyWithExceptions(h, progressIndicator, GitHandlerUtil.formatOperationName("Pulling changes into", root));
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
              if (stashCreated) {
                progressIndicator.setText(GitHandlerUtil.formatOperationName("Unstashing changes to", root));
                unstash(listsCopy, changeManager, root);
              }
            }
          }
          finally {
            HashSet<File> filesToRefresh = new HashSet<File>();
            VcsDirtyScopeManager m = VcsDirtyScopeManager.getInstance(myProject);
            for (LocalChangeList changeList : listsCopy) {
              final Collection<Change> changes = changeList.getChanges();
              if (!changes.isEmpty()) {
                LOG.debug("After unstash: moving " + changes.size() + " changes to '" + changeList.getName() + "'");
                changeManager.moveChangesTo(changeList, changes.toArray(new Change[changes.size()]));
              }
              for (Change c : changeList.getChanges()) {
                ContentRevision after = c.getAfterRevision();
                if (after != null) {
                  m.fileDirty(after.getFile());
                  filesToRefresh.add(after.getFile().getIOFile());
                }
                ContentRevision before = c.getBeforeRevision();
                if (before != null) {
                  m.fileDirty(before.getFile());
                  filesToRefresh.add(before.getFile().getIOFile());
                }
              }
            }
            LocalFileSystem.getInstance().refreshIoFiles(filesToRefresh);
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
      projectManager.unblockReloadingProjectOnExternalChanges();
    }
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
   * @param listsCopy     copy of change list
   * @param changeManager the change list manager
   * @param root          the vcs root
   */
  private void unstash(List<LocalChangeList> listsCopy, ChangeListManagerEx changeManager, VirtualFile root) {
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
   * @return true if auto-stash is requested
   */
  protected abstract boolean isAutoStash();

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
