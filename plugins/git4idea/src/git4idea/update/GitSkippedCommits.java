/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.PanelWithActionsAndCloseButton;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ContentsUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitFileRevision;
import git4idea.actions.GitShowAllSubmittedFilesAction;
import git4idea.rebase.GitRebaseUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * The panel that displays list of skipped commits during update
 */
public class GitSkippedCommits extends PanelWithActionsAndCloseButton {
  /**
   * The current project
   */
  private final Project myProject;
  /**
   * Tree control
   */
  private final Tree myTree;
  /**
   * Get center component
   */
  private JBScrollPane myCenterComponent;

  /**
   * The constructor
   *
   * @param contentManager content manager
   * @param project        the context project
   * @param skippedCommits the map with skipped commits
   */
  public GitSkippedCommits(@NotNull ContentManager contentManager,
                           Project project, SortedMap<VirtualFile, List<GitRebaseUtils.CommitInfo>> skippedCommits) {
    super(contentManager, null);
    myProject = project;
    DefaultMutableTreeNode treeRoot = new DefaultMutableTreeNode("ROOT", true);
    for (Map.Entry<VirtualFile, List<GitRebaseUtils.CommitInfo>> e : skippedCommits.entrySet()) {
      DefaultMutableTreeNode vcsRoot = new DefaultMutableTreeNode(new VcsRoot(e.getKey()));
      int missed = 0;
      for (GitRebaseUtils.CommitInfo c : e.getValue()) {
        if (c != null) {
          vcsRoot.add(new DefaultMutableTreeNode(new Commit(e.getKey(), c)));
        }
        else {
          missed++;
        }
      }
      treeRoot.add(vcsRoot);
      if (missed > 0) {
        vcsRoot.add(new DefaultMutableTreeNode("The " + missed + " commit(s) were not parsed due to unsupported rebase directory format"));
      }
    }
    myTree = new Tree(treeRoot);
    myTree.setCellRenderer(createTreeCellRenderer());
    myTree.setRootVisible(false);
    myCenterComponent = new JBScrollPane(myTree);
    init();
    TreeUtil.expandAll(myTree);
  }

  /**
   * @return new cell renderer
   */
  private DefaultTreeCellRenderer createTreeCellRenderer() {
    return new DefaultTreeCellRenderer() {
      @Override
      public Component getTreeCellRendererComponent(JTree tree,
                                                    Object value,
                                                    boolean sel,
                                                    boolean expanded,
                                                    boolean leaf,
                                                    int row,
                                                    boolean hasFocus) {
        Component r = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row,
                                                         hasFocus);
        Object o = value instanceof DefaultMutableTreeNode ? ((DefaultMutableTreeNode)value).getUserObject() : null;
        if (o instanceof VcsRoot) {
          r.setFont(tree.getFont().deriveFont(Font.BOLD));
          r.setForeground(sel ? textSelectionColor : textNonSelectionColor);
        }
        else if (o instanceof String) {
          r.setForeground(sel ? textSelectionColor : Color.RED);
          r.setFont(tree.getFont());
        }
        else {
          r.setForeground(sel ? textSelectionColor : textNonSelectionColor);
          r.setFont(tree.getFont());
        }
        return r;
      }
    };
  }

  @Override
  protected JComponent createCenterPanel() {
    return myCenterComponent;
  }

  @Override
  protected void addActionsTo(DefaultActionGroup group) {
    super.addActionsTo(group);
    GitShowAllSubmittedFilesAction showCommit = new GitShowAllSubmittedFilesAction();
    showCommit.registerCustomShortcutSet(new CustomShortcutSet(
      CommonShortcuts.DOUBLE_CLICK_1.getShortcuts()[0]), myTree);
    group.addAction(showCommit);

  }

  @Override
  public Object getData(String dataId) {
    if (PlatformDataKeys.PROJECT.is(dataId)) {
      return myProject;
    }
    TreePath selectionPath = myTree.getSelectionPath();
    DefaultMutableTreeNode node = selectionPath == null ? null : (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
    Object o = node == null ? null : node.getUserObject();
    if (o instanceof Commit) {
      Commit c = (Commit)o;
      if (VcsDataKeys.VCS_VIRTUAL_FILE.is(dataId)) {
        return c.root;
      }
      if (VcsDataKeys.VCS_FILE_REVISION.is(dataId)) {
        return new GitFileRevision(myProject, VcsUtil.getFilePath(c.root.getPath()), c.commitInfo.revision);
      }
    }
    return super.getData(dataId);
  }

  /**
   * Show skipped commits
   *
   * @param project        the context project
   * @param skippedCommits the skipped commits
   */
  public static void showSkipped(final Project project, final SortedMap<VirtualFile, List<GitRebaseUtils.CommitInfo>> skippedCommits) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        ContentManager contentManager = ProjectLevelVcsManagerEx.getInstanceEx(project).getContentManager();
        if (contentManager == null) {
          return;
        }
        GitSkippedCommits skipped = new GitSkippedCommits(contentManager, project, skippedCommits);
        Content content = ContentFactory.SERVICE.getInstance().createContent(skipped, "Skipped Commits", true);
        ContentsUtil.addContent(contentManager, content, true);
        ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS).activate(null);
      }
    });
  }

  /**
   * Wrapper for vcs root
   */
  private static class VcsRoot {
    final VirtualFile root;

    public VcsRoot(VirtualFile root) {
      this.root = root;
    }

    @Override
    public String toString() {
      return root.getPath();
    }
  }

  /**
   * Wrapper for commit
   */
  private static class Commit {
    final VirtualFile root;
    final GitRebaseUtils.CommitInfo commitInfo;

    public Commit(VirtualFile root, GitRebaseUtils.CommitInfo commitInfo) {
      this.root = root;
      this.commitInfo = commitInfo;
    }

    @Override
    public String toString() {
      return commitInfo.revision.asString().substring(0, 8) + ": " + commitInfo.subject;
    }
  }
}
