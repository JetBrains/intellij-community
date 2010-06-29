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

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 21-Jul-2006
 * Time: 11:31:06
 */
package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.*;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Icons;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class TargetChooserDialog extends DialogWrapper {
  private final Project myProject;
  private AntBuildTarget mySelectedTarget;
  private Tree myTree;

  public TargetChooserDialog(final Project project, final AntBuildTarget selectedTarget) {
    super(project, false);
    myProject = project;
    mySelectedTarget = selectedTarget;
    setTitle(AntBundle.message("ant.target.choser.title"));
    init();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    myTree = initTree();
    panel.add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);
    myTree.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (KeyEvent.VK_ENTER == e.getKeyCode()) {
          doOKAction();
        }
      }
    });
    myTree.addMouseListener(new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        if (UIUtil.isActionClick(e) && e.getClickCount() == 2 && mySelectedTarget != null) {
          doOKAction();
        }
      }
    });
    return panel;
  }

  private Tree initTree() {
    @NonNls final DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
    final Tree tree = new Tree(root);
    tree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        final TreePath selectionPath = tree.getSelectionPath();
        if (selectionPath != null) {
          final DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
          final Object userObject = node.getUserObject();
          if (userObject instanceof AntTargetNodeDescriptor) {
            final AntTargetNodeDescriptor antBuildTarget = (AntTargetNodeDescriptor)userObject;
            mySelectedTarget = antBuildTarget.getAntTarget();
          }
          else {
            mySelectedTarget = null;
          }
        }
      }
    });
    tree.setCellRenderer(new MyTreeCellRenderer());
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.setLineStyleAngled();
    TreeUtil.installActions(tree);
    new TreeSpeedSearch(tree, new Convertor<TreePath, String>() {
      public String convert(final TreePath path) {
        final Object userObject = ((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();
        if (userObject instanceof AntTargetNodeDescriptor) {
          final AntBuildTarget target = ((AntTargetNodeDescriptor)userObject).getAntTarget();
          return target.getDisplayName();
        }
        return null;
      }
    });

    DefaultMutableTreeNode selectedNode = null;
    final AntConfiguration antConfiguration = AntConfigurationImpl.getInstance(myProject);
    final AntBuildFile[] antBuildFiles = antConfiguration.getBuildFiles();
    for (AntBuildFile buildFile : antBuildFiles) {
      final DefaultMutableTreeNode buildFileNode = new DefaultMutableTreeNode(buildFile);
      DefaultMutableTreeNode selection = processFileTargets(antConfiguration.getMetaTargets(buildFile), buildFile, buildFileNode);
      if (selection != null){
        selectedNode = selection;
      }
      selection = processFileTargets(antConfiguration.getModel(buildFile).getTargets(), buildFile, buildFileNode);
      if (selection != null){
        selectedNode = selection;
      }
      root.add(buildFileNode);
    }
    TreeUtil.expandAll(tree);
    TreeUtil.selectInTree(selectedNode, true, tree);
    return tree;
  }

  public JComponent getPreferredFocusedComponent() {
    return myTree;
  }

  private DefaultMutableTreeNode processFileTargets(final AntBuildTarget[] targets, final AntBuildFile buildFile, final DefaultMutableTreeNode buildFileNode) {
    DefaultMutableTreeNode result = null;
    for (AntBuildTarget target : targets) {
      if (target.getName() == null) continue;
      final AntTargetNodeDescriptor descriptor = new AntTargetNodeDescriptor(target, buildFile);
      final DefaultMutableTreeNode node = new DefaultMutableTreeNode(descriptor);
      if (isSelected(descriptor)){
        result = node;
      }
      buildFileNode.add(node);
    }
    return result;
  }

  private boolean isSelected(final AntTargetNodeDescriptor descriptor) {
    return mySelectedTarget != null && Comparing.strEqual(mySelectedTarget.getName(), descriptor.getAntTarget().getName()) &&
           mySelectedTarget.getModel().getBuildFile() == descriptor.getBuildFile();
  }

  @Nullable
  public AntBuildTarget getSelectedTarget() {
    return mySelectedTarget;
  }

  private static class AntTargetNodeDescriptor {
    private final AntBuildTarget myAntTarget;
    private final AntBuildFile myBuildFile;


    public AntTargetNodeDescriptor(final AntBuildTarget antTarget, final AntBuildFile buildFile) {
      myAntTarget = antTarget;
      myBuildFile = buildFile;
    }

    public AntBuildTarget getAntTarget() {
      return myAntTarget;
    }

    public AntBuildFile getBuildFile() {
      return myBuildFile;
    }
  }

  private static class MyTreeCellRenderer extends ColoredTreeCellRenderer {
    public void customizeCellRenderer(JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (value instanceof DefaultMutableTreeNode) {
        final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)value;
        final Object userObject = treeNode.getUserObject();
        if (userObject instanceof AntBuildFile) {
          append(((AntBuildFile)userObject).getPresentableName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
        else if (userObject instanceof AntTargetNodeDescriptor) {
          final AntTargetNodeDescriptor descriptor = (AntTargetNodeDescriptor)userObject;
          final AntBuildTarget antTarget = descriptor.getAntTarget();
          final String antTargetName = antTarget.getName();
          append(antTargetName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
          boolean isMeta = antTarget instanceof MetaTarget;
          setIcon(isMeta ? Icons.ANT_META_TARGET_ICON : Icons.ANT_TARGET_ICON);
        }
      }
    }
  }
}
