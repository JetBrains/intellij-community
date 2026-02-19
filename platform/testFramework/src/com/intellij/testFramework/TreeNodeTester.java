// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import javax.swing.tree.TreeNode;
import java.util.function.Function;

/**
 * Helper class for testing the structure of Swing trees.
 * It's an improved version of {@link PlatformTestUtil#assertTreeEqual} which allows using custom presentation for tree nodes.
 * Later we can enhance this class to support other kinds of trees.
 */
public final class TreeNodeTester {
  private final TreeNode myNode;
  private Function<? super TreeNode, String> myPresenter = Object::toString;

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

  public @NotNull String constructTextRepresentation() {
    StringBuilder buffer = new StringBuilder();
    printSubTree(myNode, 0, buffer);
    return buffer.toString();
  }

  public void assertStructureEquals(String expected) {
    Assert.assertEquals(expected, constructTextRepresentation());
  }

  private void printSubTree(TreeNode node, int level, StringBuilder result) {
    result.append(" ".repeat(level)).append(myPresenter.apply(node)).append("\n");
    for (int i = 0; i < node.getChildCount(); i++) {
      printSubTree(node.getChildAt(i), level+1, result);
    }
  }
}
