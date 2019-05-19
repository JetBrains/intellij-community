/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testGuiFramework.fixtures;

import com.intellij.openapi.util.Ref;
import com.intellij.testGuiFramework.framework.GuiTestUtil;
import com.intellij.testGuiFramework.matcher.ClassNameMatcher;
import com.intellij.ui.treeStructure.CachingSimpleNode;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import com.intellij.util.ui.tree.TreeUtil;
import org.fest.swing.cell.JTreeCellReader;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JTreeFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

/**
 * Fixture for the left tree panel in Settings Dialog
 */
public class SettingsTreeFixture extends ComponentFixture<SettingsTreeFixture, SimpleTree> {

  public SettingsTreeFixture(@NotNull Robot robot, @NotNull SimpleTree target) {
    super(SettingsTreeFixture.class, robot, target);
  }

  //done
  @NotNull
  public static SettingsTreeFixture find(@NotNull Robot robot) {
    SimpleTree simpleTree = GuiTestUtil.INSTANCE.waitUntilFound(robot, ClassNameMatcher.forClass("com.intellij.openapi.options.newEditor.SettingsTreeView$MyTree", SimpleTree.class));
    return new SettingsTreeFixture(robot, simpleTree);
  }

  public JTreeFixture getTreeFixture() {
    JTreeFixture fixture = new JTreeFixture(robot(), target());
    fixture.replaceCellReader(new SettingsTreeCellReader());
    return fixture;
  }

  public void select(String path) {
    getTreeFixture().selectPath(path);
  }

  @Nullable
  public TreePath findPathByItemName(@NotNull String itemName){

    Ref<TreeNode> searchableNodeRef = Ref.create();
    TreeNode searchableNode;
    TreeUtil.traverse((TreeNode)target().getModel().getRoot(), node -> {
      String valueFromNode = getValueFromNode(target(), node);
      if (valueFromNode != null && valueFromNode.equals(itemName)) {
        assert (node instanceof TreeNode);
        searchableNodeRef.set((TreeNode)node);
        return false;
      }
      return true;
    });
    if (searchableNodeRef.isNull()) return null;
    searchableNode = searchableNodeRef.get();
    assert searchableNode != null;
    return TreeUtil.getPathFromRoot(searchableNode);
  }

  @Nullable
  public static String getValueFromNode(@NotNull JTree jTree, Object treeNode){
    if (!(treeNode instanceof DefaultMutableTreeNode)) return null;
    Object userObject = ((DefaultMutableTreeNode)treeNode).getUserObject();
    if (!(userObject instanceof FilteringTreeStructure.FilteringNode)) return null;
    FilteringTreeStructure.FilteringNode myNode = (FilteringTreeStructure.FilteringNode)userObject;
    Object delegate = myNode.getDelegate();
    if (!(delegate instanceof CachingSimpleNode)) return null;
    CachingSimpleNode simpleNode = (CachingSimpleNode) delegate;
    return simpleNode.toString();
  }

  private static class SettingsTreeCellReader implements JTreeCellReader {
    @Nullable
    @Override
    public String valueAt(@NotNull JTree tree, Object treeNode) {
      return getValueFromNode(tree, treeNode);
    }
  }

}
