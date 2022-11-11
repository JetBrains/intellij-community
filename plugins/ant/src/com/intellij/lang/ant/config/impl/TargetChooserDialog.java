// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang.ant.config.impl;

import com.intellij.icons.AllIcons;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.*;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import icons.AntIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
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

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    myTree = initTree();
    myTree.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (KeyEvent.VK_ENTER == e.getKeyCode()) {
          doOKAction();
        }
      }
    });

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        if (mySelectedTarget != null) {
          doOKAction();
          return true;
        }
        return false;
      }
    }.installOn(myTree);

    return JBUI.Panels.simplePanel(ScrollPaneFactory.createScrollPane(myTree));
  }

  private Tree initTree() {
    @NonNls final DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
    final Tree tree = new Tree(root);
    tree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
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
    TreeUtil.installActions(tree);
    new TreeSpeedSearch(tree, false, path -> {
      final Object userObject = ((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();
      if (userObject instanceof AntTargetNodeDescriptor) {
        final AntBuildTarget target = ((AntTargetNodeDescriptor)userObject).getAntTarget();
        return target.getDisplayName();
      }
      return null;
    });

    DefaultMutableTreeNode selectedNode = null;
    final AntConfiguration antConfiguration = AntConfigurationImpl.getInstance(myProject);
    for (AntBuildFile buildFile : antConfiguration.getBuildFileList()) {
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

  @Override
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


    AntTargetNodeDescriptor(final AntBuildTarget antTarget, final AntBuildFile buildFile) {
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
    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
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
          setIcon(isMeta ? AntIcons.MetaTarget : AllIcons.Nodes.Target);
        }
      }
    }
  }
}
