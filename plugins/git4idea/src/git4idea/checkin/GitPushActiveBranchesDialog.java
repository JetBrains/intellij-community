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
package git4idea.checkin;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import git4idea.GitBranch;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.actions.GitRepositoryAction;
import git4idea.actions.GitShowAllSubmittedFilesAction;
import git4idea.commands.*;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;
import git4idea.ui.GitUIUtil;
import git4idea.update.UpdatePolicyUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The dialog that allows pushing active branches.
 */
public class GitPushActiveBranchesDialog extends DialogWrapper {
  private static final int HASH_PREFIX_SIZE = 8; // Amount of digits to show in commit prefix

  private final Project myProject;
  private final List<VirtualFile> myVcsRoots;

  private JPanel myRootPanel;
  private JButton myViewButton; // view commits
  private JButton myFetchButton;
  private JButton myRebaseButton;
  private JButton myPushButton;

  private CheckboxTree myCommitTree; // The commit tree (sorted by vcs roots)
  private CheckedTreeNode myTreeRoot;

  private JRadioButton myStashRadioButton; // Save files policy option
  private JRadioButton myShelveRadioButton;
  private GitVcs myVcs;
  private static final Logger LOG = Logger.getInstance(GitPushActiveBranchesDialog.class.getName());

  /**
   * A modification of Runnable with the roots-parameter.
   * Also for user code simplification myInvokeInAwt variable stores the need of calling run in AWT thread.
   */
  private static abstract class PushActiveBranchRunnable {
    abstract void run(List<Root> roots);
  }

  /**
   * Constructs new dialog. Loads settings, registers listeners.
   * @param project  the project
   * @param vcsRoots the vcs roots
   * @param roots    the loaded information about roots
   */
  private GitPushActiveBranchesDialog(final Project project, List<VirtualFile> vcsRoots, List<Root> roots) {
    super(project, true);
    myVcs = GitVcs.getInstance(project);
    myProject = project;
    myVcsRoots = vcsRoots;

    updateTree(roots, null);
    updateUI();

    final GitVcsSettings settings = GitVcsSettings.getInstance(project);
    if (settings != null) {
      UpdatePolicyUtils.updatePolicyItem(settings.getPushActiveBranchesRebaseSavePolicy(), myStashRadioButton, myShelveRadioButton, null);
    }
    ChangeListener listener = new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        if (settings != null) {
          settings.setPushActiveBranchesRebaseSavePolicy(UpdatePolicyUtils.getUpdatePolicy(myStashRadioButton, myShelveRadioButton, null));
        }
      }
    };
    myStashRadioButton.addChangeListener(listener);
    myShelveRadioButton.addChangeListener(listener);
    myCommitTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        TreePath path = myCommitTree.getSelectionModel().getSelectionPath();
        if (path == null) {
          myViewButton.setEnabled(false);
          return;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
        myViewButton.setEnabled(node != null && myCommitTree.getSelectionCount() == 1 && node.getUserObject() instanceof Commit);
      }
    });
    myViewButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        TreePath path = myCommitTree.getSelectionModel().getSelectionPath();
        if (path == null) {
          return;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
        if (node == null || !(node.getUserObject() instanceof Commit)) {
          return;
        }
        Commit c = (Commit)node.getUserObject();
        GitShowAllSubmittedFilesAction.showSubmittedFiles(project, c.revision.asString(), c.root.root);
      }
    });
    myFetchButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        fetch();
      }
    });
    myRebaseButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        rebase();
      }
    });

    myPushButton.addActionListener(new ActionListener() {
      @Override public void actionPerformed(ActionEvent e) {
        push();
      }
    });

    setTitle(GitBundle.getString("push.active.title"));
    setOKButtonText(GitBundle.getString("push.active.rebase.and.push"));
    setCancelButtonText(GitBundle.getString("git.push.active.close"));
    init();
  }

  /**
   * Show dialog for the project
   */
  public static void showDialogForProject(final Project project) {
    GitVcs vcs = GitVcs.getInstance(project);
    List<VirtualFile> roots = GitRepositoryAction.getGitRoots(project, vcs);
    if (roots == null) {
      return;
    }
    List<VcsException> pushExceptions = new ArrayList<VcsException>();
    showDialog(project, roots, pushExceptions);
    vcs.showErrors(pushExceptions, GitBundle.getString("push.active.action.name"));
  }

  /**
   * Show the dialog
   * @param project    the context project
   * @param vcsRoots   the vcs roots in the project
   * @param exceptions the collected exceptions
   */
  public static void showDialog(final Project project, final List<VirtualFile> vcsRoots, final Collection<VcsException> exceptions) {
    final List<Root> emptyRoots = loadRoots(project, vcsRoots, exceptions, false); // collect roots without fetching - just to show dialog
    if (!exceptions.isEmpty()) {
      exceptions.addAll(exceptions);
      return;
    }
    final GitPushActiveBranchesDialog d = new GitPushActiveBranchesDialog(project, vcsRoots, emptyRoots);
    d.refreshTree(true, null, false); // start initial fetch
    d.show();
    if (d.isOK()) {
      d.rebaseAndPush();
    }
  }

  /**
   * This is called when "Rebase and Push" button (default button) is pressed.
   * 1. Closes the dialog.
   * 2. Fetches project and rebases.
   * 3. Repeats step 2 if needed - while current repository is behind the parent one.
   * 4. Then pushes.
   * It may fail on one of these steps (especially on rebasing with conflict) - then a notification error will be shown and the process
   * will be interrupted.
   */
  private void rebaseAndPush() {
    final Task.Backgroundable rebaseAndPushTask = new Task.Backgroundable(myProject, GitBundle.getString("push.active.fetching")) {
      public void run(@NotNull ProgressIndicator indicator) {
        List<VcsException> exceptions = new ArrayList<VcsException>();
        List<VcsException> pushExceptions = new ArrayList<VcsException>();
        for (int i = 0; i < 3; i++) {
          final RebaseInfo rebaseInfo = collectRebaseInfo();

          if (rebaseInfo.reorderedCommits.isEmpty()) { // if we have to reorder commits, rebase must pre
            final Collection<Root> rootsToPush = getRootsToPush(); // collect roots from the dialog
            exceptions = executePushCommand(rootsToPush);
            if (exceptions.isEmpty() && !rootsToPush.isEmpty()) { // if nothing to push, execute rebase anyway
              int commitsNum = 0;
              for (Root root : rootsToPush) {
                commitsNum += root.commits.size();
                Set<String> unchecked = rebaseInfo.uncheckedCommits.get(root.root);
                if (unchecked != null) {
                  commitsNum -= unchecked.size();
                }
              }
              GitUIUtil.notifySuccess(myProject, "Pushed successfully",
                                      "Pushed " + commitsNum + " " + StringUtil.pluralize("commit", commitsNum) + ".");
              return;
            }
            pushExceptions = new ArrayList<VcsException>(exceptions);
            exceptions.clear();
          }

          final List<Root> roots = loadRoots(myProject, myVcsRoots, exceptions, true); // fetch
          if (!exceptions.isEmpty()) {
            notifyException("Failed to fetch", exceptions);
            return;
          }
          updateTree(roots, rebaseInfo.uncheckedCommits);

          if (isRebaseNeeded()) {
            executeRebase(exceptions, rebaseInfo);
            if (!exceptions.isEmpty()) {
              notifyException("Failed to rebase", exceptions);
              return;
            }
            GitUtil.refreshFiles(myProject, rebaseInfo.roots);
          }
        }
        notifyException("Failed to push", pushExceptions);
      }
    };
    myVcs.runInBackground(rebaseAndPushTask);
  }

  /**
   * Notifies about errors during background rebase & push tasks.
   */
  private void notifyException(String title, Collection<VcsException> exceptions) {
    String content = StringUtil.join(exceptions, new Function<VcsException, String>() {
      @Override public String fun(VcsException e) {
        return e.getLocalizedMessage();
      }
    }, "<br/>");
    if (StringUtil.isEmptyOrSpaces(content)) {
      content = title;
    }
    LOG.info(title + " || " + content);
    Notifications.Bus.notify(new Notification(GitVcs.IMPORTANT_ERROR_NOTIFICATION, title, content, NotificationType.ERROR),
                             NotificationDisplayType.STICKY_BALLOON, myProject);
  }

  /**
   * Pushes selected commits synchronously in foreground.
   */
  private void push() {
    final Collection<Root> rootsToPush = getRootsToPush();
    final AtomicReference<Collection<VcsException>> errors = new AtomicReference<Collection<VcsException>>();

    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        errors.set(executePushCommand(rootsToPush));
      }
    }, GitBundle.getString("push.active.pushing"), true, myProject);
    if (errors.get() != null && !errors.get().isEmpty()) {
      GitUIUtil.showOperationErrors(myProject, errors.get(), GitBundle.getString("push.active.pushing"));
    }
    refreshTree(false, null);
  }

  /**
   * Executes 'git push' for the given roots to push.
   * Returns the list of errors if there were any.
   */
  private List<VcsException> executePushCommand(final Collection<Root> rootsToPush) {
    final ArrayList<VcsException> errors = new ArrayList<VcsException>();
    for (Root r : rootsToPush) {
      GitLineHandler h = new GitLineHandler(myProject, r.root, GitCommand.PUSH);
      String src = r.commitToPush != null ? r.commitToPush : r.branch;
      h.addParameters("-v", r.remote, src + ":" + r.remoteBranch);
      GitPushUtils.trackPushRejectedAsError(h, "Rejected push (" + r.root.getPresentableUrl() + "): ");
      errors.addAll(GitHandlerUtil.doSynchronouslyWithExceptions(h));
    }
    return errors;
  }

  /**
   * From the dialog collects roots and commits to be pushed.
   * @return roots to be pushed.
   */
  private Collection<Root> getRootsToPush() {
    final ArrayList<Root> rootsToPush = new ArrayList<Root>();
    for (int i = 0; i < myTreeRoot.getChildCount(); i++) {
      CheckedTreeNode node = (CheckedTreeNode) myTreeRoot.getChildAt(i);
      Root r = (Root)node.getUserObject();
      if (r.remote == null || r.commits.size() == 0) {
        continue;
      }
      boolean topCommit = true;
      for (int j = 0; j < node.getChildCount(); j++) {
        if (node.getChildAt(j) instanceof CheckedTreeNode) {
          CheckedTreeNode commitNode = (CheckedTreeNode)node.getChildAt(j);
          if (commitNode.isChecked()) {
            Commit commit = (Commit)commitNode.getUserObject();
            if (!topCommit) {
              r.commitToPush = commit.revision.asString();
            }
            rootsToPush.add(r);
            break;
          }
          topCommit = false;
        }
      }
    }
    return rootsToPush;
  }

  /**
   * Executes when FETCH button is pressed.
   * Fetches repository in background. Then updates the commit tree.
   */
  private void fetch() {
    Map<VirtualFile, Set<String>> unchecked = new HashMap<VirtualFile, Set<String>>();
    for (int i = 0; i < myTreeRoot.getChildCount(); i++) {
      Set<String> uncheckedCommits = new HashSet<String>();
      CheckedTreeNode node = (CheckedTreeNode)myTreeRoot.getChildAt(i);
      Root r = (Root)node.getUserObject();
      for (int j = 0; j < node.getChildCount(); j++) {
        if (node.getChildAt(j) instanceof CheckedTreeNode) {
          CheckedTreeNode commitNode = (CheckedTreeNode)node.getChildAt(j);
          if (!commitNode.isChecked()) {
            uncheckedCommits.add(((Commit)commitNode.getUserObject()).commitId());
          }
        }
      }
      if (!uncheckedCommits.isEmpty()) {
        unchecked.put(r.root, uncheckedCommits);
      }
    }
    refreshTree(true, unchecked);
  }

  /**
   * The rebase operation is needed if the current branch is behind remote branch or if some commit is not selected.
   * @return true if rebase is needed for at least one vcs root
   */
  private boolean isRebaseNeeded() {
    for (int i = 0; i < myTreeRoot.getChildCount(); i++) {
      CheckedTreeNode node = (CheckedTreeNode)myTreeRoot.getChildAt(i);
      Root r = (Root)node.getUserObject();
      if (r.commits.size() == 0) {
        continue;
      }
      boolean seenCheckedNode = false;
      for (int j = 0; j < node.getChildCount(); j++) {
        if (node.getChildAt(j) instanceof CheckedTreeNode) {
          CheckedTreeNode commitNode = (CheckedTreeNode)node.getChildAt(j);
          if (commitNode.isChecked()) {
            seenCheckedNode = true;
          }
          else {
            if (seenCheckedNode) {
              return true;
            }
          }
        }
      }
      if (seenCheckedNode && r.remoteCommits > 0) {
        return true;
      }
    }
    return false;
  }

  /**
   * This is called when rebase is pressed: executes rebase in background.
   */
  private void rebase() {
    final List<VcsException> exceptions = new ArrayList<VcsException>();
    final RebaseInfo rebaseInfo = collectRebaseInfo();

    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        executeRebase(exceptions, rebaseInfo);
      }
    }, GitBundle.getString("push.active.rebasing"), true, myProject);
    if (!exceptions.isEmpty()) {
      GitUIUtil.showOperationErrors(myProject, exceptions, "git rebase");
    }
    refreshTree(false, rebaseInfo.uncheckedCommits);
    GitUtil.refreshFiles(myProject, rebaseInfo.roots);
  }

  private void executeRebase(final List<VcsException> exceptions, final RebaseInfo rebaseInfo) {
    GitPushRebaseProcess process = new GitPushRebaseProcess(GitVcs.getInstance(myProject), myProject, exceptions, rebaseInfo.policy, rebaseInfo.reorderedCommits, rebaseInfo.rootsWithMerges);
    process.doUpdate(ProgressManager.getInstance().getProgressIndicator(), rebaseInfo.roots);
  }

  private static class RebaseInfo {
    final Set<VirtualFile> rootsWithMerges;
    private final Map<VirtualFile, Set<String>> uncheckedCommits;
    private final Set<VirtualFile> roots;
    private final GitVcsSettings.UpdateChangesPolicy policy;
    final Map<VirtualFile,List<String>> reorderedCommits;

    public RebaseInfo(Map<VirtualFile, List<String>> reorderedCommits,
                      Set<VirtualFile> rootsWithMerges,
                      Map<VirtualFile, Set<String>> uncheckedCommits, Set<VirtualFile> roots,
                      GitVcsSettings.UpdateChangesPolicy policy) {

      this.reorderedCommits = reorderedCommits;
      this.rootsWithMerges = rootsWithMerges;
      this.uncheckedCommits = uncheckedCommits;
      this.roots = roots;
      this.policy = policy;
    }
  }

  private RebaseInfo collectRebaseInfo() {
    final Set<VirtualFile> roots = new HashSet<VirtualFile>();
    final Set<VirtualFile> rootsWithMerges = new HashSet<VirtualFile>();
    final Map<VirtualFile, List<String>> reorderedCommits = new HashMap<VirtualFile, List<String>>();
    final Map<VirtualFile, Set<String>> uncheckedCommits = new HashMap<VirtualFile, Set<String>>();
    for (int i = 0; i < myTreeRoot.getChildCount(); i++) {
      CheckedTreeNode node = (CheckedTreeNode)myTreeRoot.getChildAt(i);
      Root r = (Root)node.getUserObject();
      Set<String> unchecked = new HashSet<String>();
      uncheckedCommits.put(r.root, unchecked);
      if (r.commits.size() == 0) {
        if (r.remoteCommits > 0) {
          roots.add(r.root);
        }
        continue;
      }
      boolean seenCheckedNode = false;
      boolean reorderNeeded = false;
      boolean seenMerges = false;
      for (int j = 0; j < node.getChildCount(); j++) {
        if (node.getChildAt(j) instanceof CheckedTreeNode) {
          CheckedTreeNode commitNode = (CheckedTreeNode)node.getChildAt(j);
          Commit commit = (Commit)commitNode.getUserObject();
          seenMerges |= commit.isMerge;
          if (commitNode.isChecked()) {
            seenCheckedNode = true;
          }
          else {
            unchecked.add(commit.commitId());
            if (seenCheckedNode) {
              reorderNeeded = true;
            }
          }
        }
      }
      if (seenMerges) {
        rootsWithMerges.add(r.root);
      }
      if (r.remoteCommits > 0 || reorderNeeded) {
        roots.add(r.root);
      }
      if (reorderNeeded) {
        List<String> reordered = new ArrayList<String>();
        for (int j = 0; j < node.getChildCount(); j++) {
          if (node.getChildAt(j) instanceof CheckedTreeNode) {
            CheckedTreeNode commitNode = (CheckedTreeNode)node.getChildAt(j);
            if (!commitNode.isChecked()) {
              Commit commit = (Commit)commitNode.getUserObject();
              reordered.add(commit.revision.asString());
            }
          }
        }
        for (int j = 0; j < node.getChildCount(); j++) {
          if (node.getChildAt(j) instanceof CheckedTreeNode) {
            CheckedTreeNode commitNode = (CheckedTreeNode)node.getChildAt(j);
            if (commitNode.isChecked()) {
              Commit commit = (Commit)commitNode.getUserObject();
              reordered.add(commit.revision.asString());
            }
          }
        }
        Collections.reverse(reordered);
        reorderedCommits.put(r.root, reordered);
      }
    }
    final GitVcsSettings.UpdateChangesPolicy p = UpdatePolicyUtils.getUpdatePolicy(myStashRadioButton, myShelveRadioButton, null);
    assert p == GitVcsSettings.UpdateChangesPolicy.STASH || p == GitVcsSettings.UpdateChangesPolicy.SHELVE;

    return new RebaseInfo(reorderedCommits, rootsWithMerges, uncheckedCommits, roots, p);
  }

  /**
   * Refresh tree
   *
   * @param fetchData if true, the current state is fetched from remote
   * @param unchecked the map from vcs root to commit identifiers that should be unchecked
   * @param initial   set this true if refreshTree is called for the first time - during the dialog construction.
   *                  the difference is that commit checkboxes should be checked only during the initial refresh - others should respect
   *                  user who could uncheck some commits.
   */
  private void refreshTree(final boolean fetchData, final Map<VirtualFile, Set<String>> unchecked, final boolean updateUncheckedCommits) {
    myCommitTree.setPaintBusy(true);
    loadRootsInBackground(fetchData, new PushActiveBranchRunnable(){
      @Override
      void run(List<Root> roots) {
        updateTree(roots, updateUncheckedCommits ? unchecked : null);
        updateUI();
        myCommitTree.setPaintBusy(false);
      }
    });
  }

  private void refreshTree(boolean fetchData, Map<VirtualFile, Set<String>> unchecked) {
    refreshTree(fetchData, unchecked, true);
  }

  /**
   * Update the tree according to the list of loaded roots
   *
   *
   * @param roots            the list of roots to add to the tree
   * @param uncheckedCommits the map from vcs root to commit identifiers that should be uncheckedCommits
   */
  private void updateTree(List<Root> roots, Map<VirtualFile, Set<String>> uncheckedCommits) {
    myTreeRoot.removeAllChildren();
    if (roots == null) {
      roots = Collections.emptyList();
    }
    for (Root r : roots) {
      CheckedTreeNode rootNode = new CheckedTreeNode(r);
      Status status = new Status();
      status.root = r;
      rootNode.add(new DefaultMutableTreeNode(status, false));
      Set<String> unchecked =
        uncheckedCommits != null && uncheckedCommits.containsKey(r.root) ? uncheckedCommits.get(r.root) : Collections.<String>emptySet();
      for (Commit c : r.commits) {
        CheckedTreeNode child = new CheckedTreeNode(c);
        rootNode.add(child);
        child.setChecked(r.remote != null && !unchecked.contains(c.commitId()));
      }
      myTreeRoot.add(rootNode);
    }
  }

  // Execute from AWT thread.
  private void updateUI() {
    ((DefaultTreeModel)myCommitTree.getModel()).reload(myTreeRoot);
    TreeUtil.expandAll(myCommitTree);
    updateButtons();
  }

  /**
   * Update buttons on the form
   */
  private void updateButtons() {
    String error = null;
    boolean wasCheckedNode = false;
    boolean reorderMerges = false;
    for (int i = 0; i < myTreeRoot.getChildCount(); i++) {
      CheckedTreeNode node = (CheckedTreeNode)myTreeRoot.getChildAt(i);
      boolean seenCheckedNode = false;
      boolean reorderNeeded = false;
      boolean seenMerges = false;
      boolean seenUnchecked = false;
      for (int j = 0; j < node.getChildCount(); j++) {
        if (node.getChildAt(j) instanceof CheckedTreeNode) {
          CheckedTreeNode commitNode = (CheckedTreeNode)node.getChildAt(j);
          Commit commit = (Commit)commitNode.getUserObject();
          seenMerges |= commit.isMerge;
          if (commitNode.isChecked()) {
            seenCheckedNode = true;
          }
          else {
            seenUnchecked = true;
            if (seenCheckedNode) {
              reorderNeeded = true;
            }
          }
        }
      }
      if (!seenCheckedNode) {
        continue;
      }
      Root r = (Root)node.getUserObject();
      if (seenMerges && seenUnchecked) {
        error = GitBundle.getString("push.active.error.merges.unchecked");
      }
      if (seenMerges && reorderNeeded) {
        reorderMerges = true;
        error = GitBundle.getString("push.active.error.reorder.merges");
      }
      if (reorderNeeded) {
        if (error == null) {
          error = GitBundle.getString("push.active.error.reorder.needed");
        }
      }
      if (r.branch == null) {
        if (error == null) {
          error = GitBundle.getString("push.active.error.no.branch");
        }
        break;
      }
      wasCheckedNode |= r.remoteBranch != null;
      if (r.remoteCommits != 0 && r.commits.size() != 0) {
        if (error == null) {
          error = GitBundle.getString("push.active.error.behind");
        }
        break;
      }
    }
    boolean rebaseNeeded = isRebaseNeeded();
    myPushButton.setEnabled(wasCheckedNode && error == null && !rebaseNeeded);
    setErrorText(error);
    myRebaseButton.setEnabled(rebaseNeeded && !reorderMerges);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getHelpId() {
    return "reference.VersionControl.Git.PushActiveBranches";
  }

  /**
   * Load VCS roots
   *
   * @param project    the project
   * @param roots      the VCS root list
   * @param exceptions the list of of exceptions to use
   * @param fetchData  if true, the data for remote is fetched.
   * @return the loaded information about vcs roots
   */
  private static List<Root> loadRoots(final Project project,
                              final List<VirtualFile> roots,
                              final Collection<VcsException> exceptions,
                              final boolean fetchData) {
    final ArrayList<Root> rc = new ArrayList<Root>();
    for (VirtualFile root : roots) {
      try {
        Root r = new Root();
        rc.add(r);
        r.root = root;
        GitBranch b = GitBranch.current(project, root);
        if (b != null) {
          r.branch = b.getFullName();
          r.remote = b.getTrackedRemoteName(project, root);
          r.remoteBranch = b.getTrackedBranchName(project, root);
          if (r.remote != null) {
            if (fetchData && !r.remote.equals(".")) {
              GitLineHandler fetch = new GitLineHandler(project, root, GitCommand.FETCH);
              fetch.addParameters(r.remote, "-v");
              Collection<VcsException> exs = GitHandlerUtil.doSynchronouslyWithExceptions(fetch);
              exceptions.addAll(exs);
            }
            GitBranch tracked = b.tracked(project, root);
            assert tracked != null : "Tracked branch cannot be null here";
            GitSimpleHandler unmerged = new GitSimpleHandler(project, root, GitCommand.LOG);
            unmerged.addParameters("--pretty=format:%H", r.branch + ".." + tracked.getFullName());
            unmerged.setNoSSH(true);
            unmerged.setStdoutSuppressed(true);
            StringScanner su = new StringScanner(unmerged.run());
            while (su.hasMoreData()) {
              if (su.line().trim().length() != 0) {
                r.remoteCommits++;
              }
            }
            GitSimpleHandler toPush = new GitSimpleHandler(project, root, GitCommand.LOG);
            toPush.addParameters("--pretty=format:%H%x20%ct%x20%at%x20%s%n%P", tracked.getFullName() + ".." + r.branch);
            toPush.setNoSSH(true);
            toPush.setStdoutSuppressed(true);
            StringScanner sp = new StringScanner(toPush.run());
            while (sp.hasMoreData()) {
              if (sp.isEol()) {
                sp.line();
                continue;
              }
              Commit c = new Commit();
              c.root = r;
              String hash = sp.spaceToken();
              String time = sp.spaceToken();
              c.revision = new GitRevisionNumber(hash, new Date(Long.parseLong(time) * 1000L));
              c.authorTime = sp.spaceToken();
              c.message = sp.line();
              c.isMerge = sp.line().indexOf(' ') != -1;
              r.commits.add(c);
            }
          }
        }
      }
      catch (VcsException e) {
        exceptions.add(e);
      }
    }
    return rc;
  }

  /**
   * Loads roots (fetches) in background. When finished, executes the given task in the AWT thread.
   * @param postUiTask
   */
  private void loadRootsInBackground(final boolean fetchData, @Nullable final PushActiveBranchRunnable postUiTask) {
    final Task.Backgroundable fetchTask = new Task.Backgroundable(myProject, GitBundle.getString("push.active.fetching")) {
      public void run(@NotNull ProgressIndicator indicator) {
        final Collection<VcsException> exceptions = new HashSet<VcsException>(1);
        final List<Root> roots = loadRoots(myProject, myVcsRoots, exceptions, fetchData);
        if (!exceptions.isEmpty()) {
          setErrorText(GitBundle.getString("push.active.fetch.failed"));
          return;
        }

        if (postUiTask != null) {
          UIUtil.invokeAndWaitIfNeeded(new Runnable() {
            @Override
            public void run() {
              postUiTask.run(roots);
            }
          });
        }
      }
    };
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override public void run() {
        final ModalityState modalityState = ModalityState.stateForComponent(getContentPane());
        myVcs.getTaskQueue().run(fetchTask, modalityState, null);
      }
    });
  }

  /**
   * Create UI components for the dialog
   */
  private void createUIComponents() {
    myTreeRoot = new CheckedTreeNode("ROOT");
    myCommitTree = new CheckboxTree(new CheckboxTree.CheckboxTreeCellRenderer() {
      @Override
      public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        // Fix GTK background
        if (UIUtil.isUnderGTKLookAndFeel()) {
          final Color background = selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground();
          UIUtil.changeBackGround(this, background);
        }
        ColoredTreeCellRenderer r = getTextRenderer();
        if (!(value instanceof DefaultMutableTreeNode)) {
          // unknown node type
          renderUnknown(r, value);
          return;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
        if (!(node.getUserObject() instanceof Node)) {
          // unknown node type
          renderUnknown(r, node.getUserObject());
          return;
        }
        ((Node)node.getUserObject()).render(r);
      }

      /**
       * Render unknown node
       *
       * @param r     a renderer to use
       * @param value the unknown value
       */
      private void renderUnknown(ColoredTreeCellRenderer r, Object value) {
        r.append("UNSUPPORTED NODE TYPE: " + (value == null ? "null" : value.getClass().getName()), SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
    }, myTreeRoot) {
      @Override
      protected void onNodeStateChanged(CheckedTreeNode node) {
        updateButtons();
        super.onNodeStateChanged(node);
      }
    };
  }


  /**
   * The base class for nodes in the tree
   */
  static abstract class Node {
    /**
     * Render the node text
     *
     * @param renderer the renderer to use
     */
    protected abstract void render(ColoredTreeCellRenderer renderer);
  }

  /**
   * The commit descriptor
   */
  static class Status extends Node {
    /**
     * The root
     */
    Root root;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void render(ColoredTreeCellRenderer renderer) {
      renderer.append(GitBundle.getString("push.active.status.status"));
      if (root.branch == null) {
        renderer.append(GitBundle.message("push.active.status.no.branch"), SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
      else if (root.remote == null) {
        renderer.append(GitBundle.message("push.active.status.no.tracked"), SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
      }
      else if (root.remoteCommits != 0 && root.commits.size() == 0) {
        renderer.append(GitBundle.message("push.active.status.no.commits.behind", root.remoteCommits),
                        SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
      }
      else if (root.commits.size() == 0) {
        renderer.append(GitBundle.message("push.active.status.no.commits"), SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
      }
      else if (root.remoteCommits != 0) {
        renderer.append(GitBundle.message("push.active.status.behind", root.remoteCommits), SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
      else {
        renderer.append(GitBundle.message("push.active.status.push", root.commits.size()));
      }
    }
  }

  /**
   * The commit descriptor
   */
  static class Commit extends Node {
    /**
     * The root
     */
    Root root;
    /**
     * The revision
     */
    GitRevisionNumber revision;
    /**
     * The message
     */
    String message;
    /**
     * The author time
     */
    String authorTime;
    /**
     * If true, the commit is a merge
     */
    boolean isMerge;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void render(ColoredTreeCellRenderer renderer) {
      renderer.append(revision.asString().substring(0, HASH_PREFIX_SIZE), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      renderer.append(": ");
      renderer.append(message);
      if (isMerge) {
        renderer.append(" " + GitBundle.getString("push.active.commit.node.merge"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
    }

    /**
     * @return the identifier that is supposed to be stable with respect to rebase
     */
    String commitId() {
      return authorTime + ":" + message;
    }
  }

  /**
   * The root node
   */
  static class Root extends Node {
    /**
     * if true, the update is required
     */
    int remoteCommits;
    /**
     * the path to vcs root
     */
    VirtualFile root;
    /**
     * the current branch
     */
    String branch;
    /**
     * the remote name
     */
    String remote;
    /**
     * the remote branch name
     */
    String remoteBranch;
    /**
     * The commit that will be actually pushed
     */
    String commitToPush;
    /**
     * the commit
     */
    List<Commit> commits = new ArrayList<Commit>();

    /**
     * {@inheritDoc}
     */
    @Override
    protected void render(ColoredTreeCellRenderer renderer) {
      SimpleTextAttributes rootAttributes;
      SimpleTextAttributes branchAttributes;
      if (remote != null && commits.size() != 0 && remoteCommits != 0 || branch == null) {
        rootAttributes = SimpleTextAttributes.ERROR_ATTRIBUTES.derive(SimpleTextAttributes.STYLE_BOLD, null, null, null);
        branchAttributes = SimpleTextAttributes.ERROR_ATTRIBUTES;
      }
      else if (remote == null || commits.size() == 0) {
        rootAttributes = SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES;
        branchAttributes = SimpleTextAttributes.GRAYED_ATTRIBUTES;
      }
      else {
        branchAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
        rootAttributes = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
      }
      renderer.append(root.getPresentableUrl(), rootAttributes);
      if (branch != null) {
        renderer.append(" [" + branch, branchAttributes);
        if (remote != null) {
          renderer.append(" -> " + remote + "#" + remoteBranch, branchAttributes);
        }
        renderer.append("]", branchAttributes);
      }
    }
  }
}
