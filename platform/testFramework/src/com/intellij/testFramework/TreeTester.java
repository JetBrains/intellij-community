/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.openapi.util.text.StringUtil;
import org.junit.Assert;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.util.function.Function;

/**
 * Helper class for testing structure of Swing's trees. It's an improved version of {@link PlatformTestUtil#assertTreeEqual} which allows
 * using custom presentation for tree nodes. Later we can enhance this class to support other kinds of trees.
 *
 * @author nik
 */
public class TreeTester {
  private final TreeNode myNode;
  private Function<TreeNode, String> myPresenter = Object::toString;

  public static TreeTester forTree(JTree tree) {
    return forNode((TreeNode)tree.getModel().getRoot());
  }

  public static TreeTester forNode(TreeNode node) {
    return new TreeTester(node);
  }

  private TreeTester(TreeNode node) {
    myNode = node;
  }

  public TreeTester withPresenter(Function<TreeNode, String> presenter) {
    myPresenter = presenter;
    return this;
  }

  public void assertStructureEquals(String expected) {
    StringBuilder buffer = new StringBuilder();
    printSubTree(myNode, 0, buffer);
    Assert.assertEquals(expected, buffer.toString());
  }

  private void printSubTree(TreeNode node, int level, StringBuilder result) {
    result.append(StringUtil.repeat(" ", level)).append(myPresenter.apply(node)).append("\n");
    for (int i = 0; i < node.getChildCount(); i++) {
      printSubTree(node.getChildAt(i), level+1, result);
    }
  }
}
