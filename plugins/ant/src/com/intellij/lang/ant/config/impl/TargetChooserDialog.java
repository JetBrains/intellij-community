/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
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
import com.intellij.openapi.util.Pair;
import com.intellij.ui.*;
import com.intellij.util.Icons;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;

public class TargetChooserDialog extends DialogWrapper {
  private Pair<AntBuildFile, String> mySelectedTarget;
  private AntConfiguration myAntConfiguration;

  protected TargetChooserDialog(final Project project,
                                final Pair<AntBuildFile, String> selectedTarger,
                                final AntConfiguration antConfiguration) {
    super(project, false);
    mySelectedTarget = selectedTarger;
    myAntConfiguration = antConfiguration;
    setTitle(AntBundle.message("ant.target.choser.title"));
    init();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(ScrollPaneFactory.createScrollPane(initTree()), BorderLayout.CENTER);
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
            mySelectedTarget = new Pair<AntBuildFile, String>(antBuildTarget.getBuildFile(), antBuildTarget.getAntTarget().getName());
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
    TreeToolTipHandler.install(tree);
    TreeUtil.installActions(tree);
    new TreeSpeedSearch(tree);

    DefaultMutableTreeNode selectedNode = null;
    final AntBuildFile[] antBuildFiles = myAntConfiguration.getBuildFiles();
    for (AntBuildFile buildFile : antBuildFiles) {
      final DefaultMutableTreeNode buildFileNode = new DefaultMutableTreeNode(buildFile);
      DefaultMutableTreeNode selection = processFileTargets(myAntConfiguration.getMetaTargets(buildFile), buildFile, buildFileNode);
      if (selection != null){
        selectedNode = selection;
      }
      selection = processFileTargets(myAntConfiguration.getModel(buildFile).getTargets(), buildFile, buildFileNode);
      if (selection != null){
        selectedNode = selection;
      }
      root.add(buildFileNode);
    }
    TreeUtil.expandAll(tree);
    TreeUtil.selectInTree(selectedNode, true, tree);
    return tree;
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
    return mySelectedTarget != null && Comparing.strEqual(mySelectedTarget.getSecond(), descriptor.getAntTarget().getName()) &&
           mySelectedTarget.getFirst() == descriptor.getBuildFile();
  }

  public Pair<AntBuildFile, String> getSelectedTarget() {
    return mySelectedTarget;
  }

  private static class AntTargetNodeDescriptor {
    private AntBuildTarget myAntTarget;
    private AntBuildFile myBuildFile;


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