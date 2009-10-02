package git4idea.checkin;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.tree.TreeUtil;
import git4idea.GitBranch;
import git4idea.GitRevisionNumber;
import git4idea.actions.GitShowAllSubmittedFilesAction;
import git4idea.commands.*;
import git4idea.i18n.GitBundle;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

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
   * The commit tree control
   */
  private JTree myCommitTree;
  /**
   * The root information structure
   */
  private List<Root> myRoots;

  /**
   * The constructor
   *
   * @param project the project
   * @param roots   the loaded roots
   */
  private GitPushActiveBranchesDialog(final Project project, List<Root> roots) {
    super(project, true);
    myRoots = roots;
    myCommitTree.setModel(new DefaultTreeModel(createTree()));
    TreeUtil.expandAll(myCommitTree);
    for (Root r : roots) {
      if (r.branch == null) {
        setErrorText(GitBundle.getString("push.active.error.no.branch"));
        setOKActionEnabled(false);
        break;
      }
      if (r.remoteCommits != 0) {
        setErrorText(GitBundle.getString("push.active.error.behind"));
        setOKActionEnabled(false);
        break;
      }
    }
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
    setTitle(GitBundle.getString("push.active.title"));
    setOKButtonText(GitBundle.getString("push.active.button"));
    init();
  }

  /**
   * @return the created tree
   */
  private TreeNode createTree() {
    DefaultMutableTreeNode treeRoot = new DefaultMutableTreeNode("ROOT", true);
    for (Root r : myRoots) {
      DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(r, true);
      Status status = new Status();
      status.root = r;
      rootNode.add(new DefaultMutableTreeNode(status, false));
      for (Commit c : r.commits) {
        rootNode.add(new DefaultMutableTreeNode(c, false));
      }
      treeRoot.add(rootNode);
    }
    return treeRoot;
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
   * Load VCS roots
   *
   * @param project the project
   * @param roots   the VCS root list
   * @return the loaded information about vcs roots
   */
  static List<Root> loadRoots(final Project project, final List<VirtualFile> roots, final Collection<VcsException> exceptions) {
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
                if(!r.remote.equals(".")) {
                  GitLineHandler fetch = new GitLineHandler(project, root, GitHandler.FETCH);
                  fetch.addParameters(r.remote, "-v");
                  Collection<VcsException> exs = GitHandlerUtil.doSynchronouslyWithExceptions(fetch);
                  exceptions.addAll(exs);
                }
                GitBranch tracked = b.tracked(project, root);
                assert tracked != null : "Tracked branch cannot be null here";
                GitSimpleHandler unmerged = new GitSimpleHandler(project, root, GitHandler.LOG);
                unmerged.addParameters("--pretty=format:%H", r.branch + ".." + tracked.getFullName());
                unmerged.setNoSSH(true);
                unmerged.setStdoutSuppressed(true);
                StringScanner su = new StringScanner(unmerged.run());
                while (su.hasMoreData()) {
                  if (su.line().trim().length() != 0) {
                    r.remoteCommits++;
                  }
                }
                GitSimpleHandler toPush = new GitSimpleHandler(project, root, GitHandler.LOG);
                toPush.addParameters("--pretty=format:%H%x20%ct%x20%s", tracked.getFullName() + ".." + r.branch);
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
                  c.message = sp.line();
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
   * Show the dialog
   *
   * @param project    the context project
   * @param vcsRoots   the vcs roots in the project
   * @param exceptions the collected exceptions
   */
  public static void showDialog(final Project project, List<VirtualFile> vcsRoots, final Collection<VcsException> exceptions) {
    final List<Root> roots = loadRoots(project, vcsRoots, exceptions);
    if (!exceptions.isEmpty()) {
      Messages
        .showErrorDialog(project, GitBundle.getString("push.active.fetch.failed"), GitBundle.getString("push.active.fetch.failed.title"));
      return;
    }
    GitPushActiveBranchesDialog d = new GitPushActiveBranchesDialog(project, roots);
    d.show();
    if (d.isOK()) {
      final ProgressManager manager = ProgressManager.getInstance();
      manager.runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          for (Root r : roots) {
            if (r.remote != null && r.commits.size() != 0) {
              GitLineHandler h = new GitLineHandler(project, r.root, GitHandler.PUSH);
              h.addParameters("-v", r.remote, r.branch+":"+r.remoteBranch);
              GitHandlerUtil.doSynchronouslyWithExceptions(h);
            }
          }
        }
      }, GitBundle.getString("push.active.pushing"), false, project);
    }
  }


  /**
   * The commit descriptor
   */
  static class Status {
    /**
     * The root
     */
    Root root;

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
      if (root.branch == null) {
        return GitBundle.message("push.active.status.no.branch");
      }
      if (root.remote == null) {
        return GitBundle.message("push.active.status.no.tracked");
      }
      if (root.commits.size() == 0) {
        return GitBundle.message("push.active.status.no.commits");
      }
      if (root.remoteCommits != 0) {
        return GitBundle.message("push.active.status.behind", root.remoteCommits);
      }
      return GitBundle.message("push.active.status.push", root.commits.size());
    }
  }

  /**
   * The commit descriptor
   */
  static class Commit {
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
     * {@inheritDoc}
     */
    @Override
    public String toString() {
      return GitBundle.message("push.active.commit.node", revision.asString().substring(0, HASH_PREFIX_SIZE), message);
    }
  }

  /**
   * The root node
   */
  static class Root {
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
     * the commit
     */
    List<Commit> commits = new ArrayList<Commit>();

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
      if (branch == null) {
        return GitBundle.message("push.active.root.node.no.branch", root.getPresentableUrl());
      }
      if (remote == null) {
        return GitBundle.message("push.active.root.node.no.tracked", root.getPresentableUrl(), branch);
      }
      if (commits.size() == 0) {
        return GitBundle.message("push.active.root.node.no.commits", root.getPresentableUrl(), branch, remote, remoteBranch);
      }
      if (remoteCommits != 0) {
        return GitBundle.message("push.active.root.node.behind", root.getPresentableUrl(), branch, remote, remoteBranch);
      }
      return GitBundle.message("push.active.root.node.push", root.getPresentableUrl(), branch, remote, remoteBranch);
    }
  }
}
