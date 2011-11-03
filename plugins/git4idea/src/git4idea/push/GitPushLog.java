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
package git4idea.push;

import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.tree.TreeUtil;
import git4idea.GitBranch;
import git4idea.history.browser.GitCommit;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * The list of commits from multiple repositories and branches, with diff panel at the right.
 *
 * @author Kirill Likhodedov
 */
class GitPushLog extends JPanel implements TypeSafeDataProvider {

  private final Project myProject;
  private ChangesBrowser myChangesBrowser;
  private final Tree myTree;
  private final DefaultTreeModel myTreeModel;
  private final DefaultMutableTreeNode myRootNode;
  Map<GitRepository, Boolean> mySelectedRepositories = new HashMap<GitRepository, Boolean>();

  GitPushLog(@NotNull Project project) {
    myProject = project;

    for (GitRepository repository : GitRepositoryManager.getInstance(project).getRepositories()) {
      mySelectedRepositories.put(repository, true);
    }

    myRootNode = new DefaultMutableTreeNode();
    myTreeModel = new DefaultTreeModel(myRootNode);
    myTree = new Tree(myTreeModel);
    myTree.setRootVisible(false);
    myTree.setCellRenderer(new MyTreeCellRenderer());
    TreeUtil.expandAll(myTree);

    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) myTree.getLastSelectedPathComponent();
        if (node != null) {
          Object nodeInfo = node.getUserObject();
          if (nodeInfo instanceof GitCommit) {
            myChangesBrowser.setChangesToDisplay(((GitCommit)nodeInfo).getChanges());
            return;
          }
        }
        myChangesBrowser.setChangesToDisplay(Collections.<Change>emptyList());
      }
    });
    
    myChangesBrowser = new ChangesBrowser(myProject, null, Collections.<Change>emptyList(), null, false, true, null, ChangesBrowser.MyUseCase.LOCAL_CHANGES, null);
    myChangesBrowser.getDiffAction().registerCustomShortcutSet(CommonShortcuts.getDiff(), myTree);

    Splitter splitter = new Splitter(false, 0.7f);
    splitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myTree));
    splitter.setSecondComponent(myChangesBrowser);
    
    setLayout(new BorderLayout());
    add(splitter);
  }

  private void insertToRootNode(DefaultMutableTreeNode rootNode, List<DefaultMutableTreeNode> nodesForRepositories) {
    for (DefaultMutableTreeNode node : nodesForRepositories) {
      rootNode.add(node);
    }
  }

  private List<DefaultMutableTreeNode> createNodesForRepositories(@NotNull GitCommitsByRepoAndBranch commits) {
    List<DefaultMutableTreeNode> nodes = new ArrayList<DefaultMutableTreeNode>();
    for (Map.Entry<GitRepository, GitCommitsByBranch> entry : commits.asMap().entrySet()) {
      GitRepository repository = entry.getKey();
      GitCommitsByBranch commitsByBranch = entry.getValue();

      DefaultMutableTreeNode repoNode = createRepoNode(repository, commitsByBranch);
      nodes.add(repoNode);
    }
    return nodes;
  }

  private DefaultMutableTreeNode createRepoNode(GitRepository repository, GitCommitsByBranch commitsByBranch) {
    DefaultMutableTreeNode repoNode = new DefaultMutableTreeNode(repository);
    for (Map.Entry<GitBranch, List<GitCommit>> entry : commitsByBranch.asMap().entrySet()) {
      DefaultMutableTreeNode branchNode = createBranchNode(entry.getKey(), entry.getValue());
      repoNode.add(branchNode);
    }
    return repoNode;
  }

  private DefaultMutableTreeNode createBranchNode(GitBranch branch, List<GitCommit> commits) {
    DefaultMutableTreeNode branchNode = new DefaultMutableTreeNode(branch.getName());
    for (GitCommit commit : commits) {
      branchNode.add(new DefaultMutableTreeNode(commit));
    }
    return branchNode;
  }

  // Make changes available for diff action
  @Override
  public void calcData(DataKey key, DataSink sink) {
    if (VcsDataKeys.CHANGES.equals(key)) {
      DefaultMutableTreeNode[] selectedNodes = myTree.getSelectedNodes(DefaultMutableTreeNode.class, null);
      if (selectedNodes.length == 0) {
        return;
      }
      Object object = selectedNodes[0].getUserObject();
      if (object instanceof GitCommit) {
        sink.put(key, ArrayUtil.toObjectArray(((GitCommit)object).getChanges(), Change.class));
      }
    }
  }

  @NotNull
  public JComponent getPreferredFocusComponent() {
    return myTree;
  }

  void setCommits(GitCommitsByRepoAndBranch commits) {
    insertToRootNode(myRootNode, createNodesForRepositories(commits));
    myTreeModel.reload(myRootNode);
    TreeUtil.expandAll(myTree);
    repaint();
  }

  /**
   * @return repositories selected (via checkboxes) to be pushed.
   */
  Collection<GitRepository> getSelectedRepositories() {
    Collection<GitRepository> repositories = new ArrayList<GitRepository>(mySelectedRepositories.size());
    for (Map.Entry<GitRepository, Boolean> entry : mySelectedRepositories.entrySet()) {
      if (entry.getValue()) {
        repositories.add(entry.getKey());
      }
    }
    return repositories;
  }

  private class MyTreeCellRenderer implements TreeCellRenderer {

    public final DefaultTreeCellRenderer DEFAULT_RENDERER = new DefaultTreeCellRenderer();

    JBLabel myBranchRenderer = createBranchRender();
    JBLabel myCommitRender = createCommitRender();


    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      if (value instanceof DefaultMutableTreeNode) {
        Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
        if (userObject instanceof GitCommit) {
          myCommitRender.setText(makeTextForCommit((GitCommit) userObject));
          myCommitRender.setForeground(selected ? DEFAULT_RENDERER.getTextSelectionColor() : DEFAULT_RENDERER.getTextNonSelectionColor());
          return myCommitRender;
        } else if (userObject instanceof GitRepository) {
          return createRepositoryRender((GitRepository)userObject, selected);
        }
      }
      return DEFAULT_RENDERER.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
    }

    private String makeTextForCommit(@NotNull GitCommit commit) {
      return String.format("<html><code><b>%s</b> %15s</code>  %s</html>",
                           commit.getShortHash(),
                           DateFormatUtil.formatPrettyDateTime(commit.getAuthorTime()),
                           commit.getSubject())
        .replace(" ", "&nbsp;");
    }


    private JBLabel createCommitRender() {
      return new JBLabel();
    }

    private Component createRepositoryRender(final GitRepository repository, boolean selected) {
      final JCheckBox checkBox = new JCheckBox(repository.getRoot().getPresentableUrl(), null, true);
      checkBox.setForeground(selected ? DEFAULT_RENDERER.getTextSelectionColor() : DEFAULT_RENDERER.getTextNonSelectionColor());
      checkBox.setBackground(selected ? DEFAULT_RENDERER.getBackgroundSelectionColor() : DEFAULT_RENDERER.getBackgroundNonSelectionColor());
      checkBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          mySelectedRepositories.put(repository, checkBox.isSelected());
        }
      });
      return checkBox;
    }

    private JBLabel createBranchRender() {
      JBLabel label = new JBLabel();
      label.setOpaque(true);
      label.setBackground(Color.CYAN);
      return label;
    }
  }

}
