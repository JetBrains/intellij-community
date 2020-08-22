// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.util.function.Function;

/**
 * Helper class for testing structure of Swing's trees. It's an improved version of {@link PlatformTestUtil#assertTreeEqual} which allows
 * using custom presentation for tree nodes. Later we can enhance this class to support other kinds of trees.
 */
public final class TreeNodeTester {
  private final TreeNode myNode;
  private Function<? super TreeNode, String> myPresenter = Object::toString;

  /**
   * @deprecated use {@link com.intellij.ui.tree.TreeTestUtil#TreeTestUtil(JTree)}
   */
  @Deprecated
  public static TreeNodeTester forTree(JTree tree) {
    return forNode((TreeNode)tree.getModel().getRoot());
  }

  public static TreeNodeTester forNode(TreeNode node) {
    return new TreeNodeTester(node);
  }

  private TreeNodeTester(TreeNode node) {
    myNode = node;
  }

  public TreeNodeTester withPresenter(Function<? super TreeNode, String> presenter) {
    myPresenter = presenter;
    return this;
  }

  @NotNull
  public String constructTextRepresentation() {
    StringBuilder buffer = new StringBuilder();
    printSubTree(myNode, 0, buffer);
    return buffer.toString();
  }

  public void assertStructureEquals(String expected) {
    Assert.assertEquals(expected, constructTextRepresentation());
  }

  private void printSubTree(TreeNode node, int level, StringBuilder result) {
    result.append(StringUtil.repeat(" ", level)).append(myPresenter.apply(node)).append("\n");
    for (int i = 0; i < node.getChildCount(); i++) {
      printSubTree(node.getChildAt(i), level+1, result);
    }
  }
}
