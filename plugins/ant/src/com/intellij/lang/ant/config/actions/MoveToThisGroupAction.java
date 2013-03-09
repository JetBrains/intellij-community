/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.lang.ant.config.actions;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildFileGroup;
import com.intellij.lang.ant.config.AntBuildFileGroupManager;
import com.intellij.lang.ant.config.explorer.AntBuildFileNodeDescriptor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.treeStructure.Tree;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * @author VISTALL
 * @since 12:43/09.03.13
 */
public class MoveToThisGroupAction extends AnAction {
  private final Tree myTree;
  private final AntBuildFileGroup myGroup;

  public MoveToThisGroupAction(Tree tree, AntBuildFileGroup group) {
    super(AntBundle.message(group == null ? "move.out.from.group" : "move.to.this.group"));
    myTree = tree;
    myGroup = group;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final AntBuildFile buildFile = findBuildFile(myTree);

    AntBuildFileGroupManager.getInstance(e.getProject()).moveToGroup(buildFile, myGroup);
    final AbstractTreeBuilder builder = AbstractTreeBuilder.getBuilderFor(myTree);
    if(builder != null) {
      builder.queueUpdate();
    }
  }

  public static AntBuildFile findBuildFile(Tree tree) {
    final Object lastSelectedPathComponent = tree.getLastSelectedPathComponent();
    if(lastSelectedPathComponent == null || !(lastSelectedPathComponent instanceof DefaultMutableTreeNode)) {
      return null;
    }

    final Object userObject = ((DefaultMutableTreeNode)lastSelectedPathComponent).getUserObject();
    if(!(userObject instanceof AntBuildFileNodeDescriptor)) {
      return null;
    }

    return ((AntBuildFileNodeDescriptor)userObject).getBuildFile();
  }
}
