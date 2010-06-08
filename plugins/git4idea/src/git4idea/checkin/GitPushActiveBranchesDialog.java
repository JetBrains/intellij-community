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

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
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

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

/**
 * The dialog that allows pushing active branches.
 */
public class GitPushActiveBranchesDialog extends DialogWrapper {
  /**
   * Amount of digits to show in commit prefix
   */
  private final static int HASH_PREFIX_SIZE = 8;
  /**
   * The view commit button
   */
  private JButton myViewButton;
  /**
   * The root panel
   */
  private JPanel myPanel;
  /**
   * Fetch changes from remote repository
   */
  private JButton myFetchButton;
  /**
   * Rebase commits to new roots
   */
  private JButton myRebaseButton;
  /**
   * The commit tree (sorted by vcs roots)
   */
  private CheckboxTree myCommitTree;
  /**
   * Save files policy option
   */
  private JRadioButton myStashRadioButton;
  /**
   * Save files policy option
   */
  private JRadioButton myShelveRadioButton;
  /**
   * The root node
   */
  private CheckedTreeNode myTreeRoot;
  /**
   * The context project
   */
  private final Project myProject;
  /**
   * The vcs roots for the project
   */
  private final List<VirtualFile> myVcsRoots;

  /**
   * The constructor
   *
   * @param project  the project
   * @param vcsRoots the vcs roots
   * @param roots    the loaded information about roots
   */
  private GitPushActiveBranchesDialog(final Project project, List<VirtualFile> vcsRoots, List<Root> roots) {
    super(project, true);
    myProject = project;
    myVcsRoots = vcsRoots;
    updateTree(roots, null);
    TreeUtil.expandAll(myCommitTree);
    final GitVcsSettings settings = GitVcsSettings.getInstance(project);
    UpdatePolicyUtils.updatePolicyItem(settings.getPushActiveBranchesRebaseSavePolicy(), myStashRadioButton, myShelveRadioButton, null);
    ChangeListener listener = new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        settings.setPushActiveBranchesRebaseSavePolicy(UpdatePolicyUtils.getUpdatePolicy(myStashRadioButton, myShelveRadioButton, null));
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
        doFetch();
      }
    });
    myRebaseButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        doRebase();
      }
    });
    setTitle(GitBundle.getString("push.active.title"));
    setOKButtonText(GitBundle.getString("push.active.button"));
    init();
  }

  /**
   * Perform fetch operation
   */
  private void doFetch() {
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
   *
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
   * Preform rebase operation
   */
  private void doRebase() {
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
      if (r.remoteCommits > 0 && seenCheckedNode || reorderNeeded) {
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
    final List<VcsException> exceptions = new ArrayList<VcsException>();
    final GitVcsSettings.UpdateChangesPolicy p = UpdatePolicyUtils.getUpdatePolicy(myStashRadioButton, myShelveRadioButton, null);
    assert p == GitVcsSettings.UpdateChangesPolicy.STASH || p == GitVcsSettings.UpdateChangesPolicy.SHELVE;
    final ProgressManager progressManager = ProgressManager.getInstance();
    final GitVcs vcs = GitVcs.getInstance(myProject);
    progressManager.runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        GitPushRebaseProcess process = new GitPushRebaseProcess(vcs, myProject, exceptions, p, reorderedCommits, rootsWithMerges);
        process.doUpdate(progressManager.getProgressIndicator(), roots);
      }
    }, GitBundle.getString("push.active.rebasing"), false, myProject);
    refreshTree(false, uncheckedCommits);
    if (!exceptions.isEmpty()) {
      GitUIUtil.showOperationErrors(myProject, exceptions, "git rebase");
    }
    GitUtil.refreshFiles(myProject, roots);
  }

  /**
   * Refresh tree
   *
   * @param fetchData if true, the current state is fetched from remote
   * @param unchecked the map from vcs root to commit identifiers that should be unchecked
   */
  private void refreshTree(final boolean fetchData, Map<VirtualFile, Set<String>> unchecked) {
    ArrayList<VcsException> exceptions = new ArrayList<VcsException>();
    List<Root> roots = loadRoots(myProject, myVcsRoots, exceptions, fetchData);
    if (!exceptions.isEmpty()) {
      //noinspection ThrowableResultOfMethodCallIgnored
      GitUIUtil.showOperationErrors(myProject, exceptions, "Refreshing root information");
      return;
    }
    updateTree(roots, unchecked);
  }

  /**
   * Update the tree according to the list of loaded roots
   *
   * @param roots            the list of roots to add to the tree
   * @param uncheckedCommits the map from vcs root to commit identifiers that should be uncheckedCommits
   */
  private void updateTree(List<Root> roots, Map<VirtualFile, Set<String>> uncheckedCommits) {
    myTreeRoot.removeAllChildren();
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
    setOKActionEnabled(wasCheckedNode && error == null && !rebaseNeeded);
    setErrorText(error);
    myRebaseButton.setEnabled(rebaseNeeded && !reorderMerges);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
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
  static List<Root> loadRoots(final Project project,
                              final List<VirtualFile> roots,
                              final Collection<VcsException> exceptions,
                              final boolean fetchData) {
    final ProgressManager manager = ProgressManager.getInstance();
    final ArrayList<Root> rc = new ArrayList<Root>();
    manager.runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
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
      }
    }, GitBundle.getString("push.active.fetching"), false, project);
    return rc;
  }

  /**
   * Show dialog for the project
   *
   * @param project the project to show dialog for
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
   *
   * @param project    the context project
   * @param vcsRoots   the vcs roots in the project
   * @param exceptions the collected exceptions
   */
  public static void showDialog(final Project project, List<VirtualFile> vcsRoots, final Collection<VcsException> exceptions) {
    final List<Root> roots = loadRoots(project, vcsRoots, exceptions, true);
    if (!exceptions.isEmpty()) {
      Messages
        .showErrorDialog(project, GitBundle.getString("push.active.fetch.failed"), GitBundle.getString("push.active.fetch.failed.title"));
      return;
    }
    GitPushActiveBranchesDialog d = new GitPushActiveBranchesDialog(project, vcsRoots, roots);
    d.show();
    if (d.isOK()) {
      final ArrayList<Root> rootsToPush = new ArrayList<Root>();
      for (int i = 0; i < d.myTreeRoot.getChildCount(); i++) {
        CheckedTreeNode node = (CheckedTreeNode)d.myTreeRoot.getChildAt(i);
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
      final ProgressManager manager = ProgressManager.getInstance();
      final ArrayList<VcsException> errors = new ArrayList<VcsException>();
      manager.runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          for (Root r : rootsToPush) {
            GitLineHandler h = new GitLineHandler(project, r.root, GitCommand.PUSH);
            String src = r.commitToPush != null ? r.commitToPush : r.branch;
            h.addParameters("-v", r.remote, src + ":" + r.remoteBranch);
            GitPushUtils.trackPushRejectedAsError(h, "Rejected push (" + r.root.getPresentableUrl() + "): ");
            errors.addAll(GitHandlerUtil.doSynchronouslyWithExceptions(h));
          }
        }
      }, GitBundle.getString("push.active.pushing"), false, project);
      if (!errors.isEmpty()) {
        GitUIUtil.showOperationErrors(project, errors, GitBundle.getString("push.active.pushing"));
      }
    }
  }

  /**
   * Create UI components for the dialog
   */
  private void createUIComponents() {
    myTreeRoot = new CheckedTreeNode("ROOT");
    myCommitTree = new CheckboxTree(new CheckboxTree.CheckboxTreeCellRenderer() {
      @Override
      public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
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
        renderer.append(GitBundle.getString("push.active.commit.node.merge"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
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
