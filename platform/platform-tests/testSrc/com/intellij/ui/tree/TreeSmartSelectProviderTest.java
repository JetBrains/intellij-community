// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreeSelectionModel;
import java.util.function.Consumer;

public class TreeSmartSelectProviderTest {
  private final TreeSmartSelectProvider provider = new TreeSmartSelectProvider();

  private void testIncrease(JTree tree, String... expected) {
    test(tree, true, false, expected);
  }

  private void testDecrease(JTree tree, String... expected) {
    test(tree, false, true, expected);
  }

  private void testIncreaseDecrease(JTree tree, String... expected) {
    test(tree, true, true, expected);
  }

  private void test(JTree tree, boolean increase, boolean decrease, String... expected) {
    int length = expected.length;
    if (length < 2) throw new IllegalArgumentException();
    if (!increase && !decrease) throw new IllegalArgumentException();
    assertTree(tree, expected[0]);
    if (increase) {
      for (int i = 1; i < expected.length; i++) {
        provider.increaseSelection(tree);
        assertTree(tree, expected[i]);
      }
    }
    if (decrease) {
      for (int i = 1; i < expected.length; i++) {
        provider.decreaseSelection(tree);
        assertTree(tree, expected[increase ? expected.length - 1 - i : i]);
      }
    }
  }

  @Test
  public void testIcreaseToVisibleRoot() {
    test(true, tree -> {
      select(tree, 5);
      testIncrease(tree,
                   "-Root\n" +
                   " -Color\n" +
                   "  Red\n" +
                   "  Green\n" +
                   "  Blue\n" +
                   " +[Digit]\n" +
                   " -Letter\n" +
                   "  -Greek\n" +
                   "   Alpha\n" +
                   "   Beta\n" +
                   "   Gamma\n" +
                   "   Delta\n" +
                   "   Epsilon\n",
                   // 1 // 1 // 1 // select siblings of node
                   "-Root\n" +
                   " -[Color]\n" +
                   "  [Red]\n" +
                   "  [Green]\n" +
                   "  [Blue]\n" +
                   " +[Digit]\n" +
                   " -[Letter]\n" +
                   "  -[Greek]\n" +
                   "   [Alpha]\n" +
                   "   [Beta]\n" +
                   "   [Gamma]\n" +
                   "   [Delta]\n" +
                   "   [Epsilon]\n",
                   // 2 // 2 // 2 // select visible root
                   "-[Root]\n" +
                   " -[Color]\n" +
                   "  [Red]\n" +
                   "  [Green]\n" +
                   "  [Blue]\n" +
                   " +[Digit]\n" +
                   " -[Letter]\n" +
                   "  -[Greek]\n" +
                   "   [Alpha]\n" +
                   "   [Beta]\n" +
                   "   [Gamma]\n" +
                   "   [Delta]\n" +
                   "   [Epsilon]\n",
                   // 3 // 3 // 3 // do nothing
                   "-[Root]\n" +
                   " -[Color]\n" +
                   "  [Red]\n" +
                   "  [Green]\n" +
                   "  [Blue]\n" +
                   " +[Digit]\n" +
                   " -[Letter]\n" +
                   "  -[Greek]\n" +
                   "   [Alpha]\n" +
                   "   [Beta]\n" +
                   "   [Gamma]\n" +
                   "   [Delta]\n" +
                   "   [Epsilon]\n");
    });
  }

  @Test
  public void testIcreaseToHiddenRoot() {
    test(false, tree -> {
      select(tree, 5);
      testIncrease(tree,
                   "-Root\n" +
                   " -Color\n" +
                   "  Red\n" +
                   "  Green\n" +
                   "  Blue\n" +
                   " +[Digit]\n" +
                   " -Letter\n" +
                   "  -Greek\n" +
                   "   Alpha\n" +
                   "   Beta\n" +
                   "   Gamma\n" +
                   "   Delta\n" +
                   "   Epsilon\n",
                   // 1 // 1 // 1 // select siblings of node
                   "-Root\n" +
                   " -[Color]\n" +
                   "  [Red]\n" +
                   "  [Green]\n" +
                   "  [Blue]\n" +
                   " +[Digit]\n" +
                   " -[Letter]\n" +
                   "  -[Greek]\n" +
                   "   [Alpha]\n" +
                   "   [Beta]\n" +
                   "   [Gamma]\n" +
                   "   [Delta]\n" +
                   "   [Epsilon]\n",
                   // 2 // 2 // 2 // do nothing
                   "-Root\n" +
                   " -[Color]\n" +
                   "  [Red]\n" +
                   "  [Green]\n" +
                   "  [Blue]\n" +
                   " +[Digit]\n" +
                   " -[Letter]\n" +
                   "  -[Greek]\n" +
                   "   [Alpha]\n" +
                   "   [Beta]\n" +
                   "   [Gamma]\n" +
                   "   [Delta]\n" +
                   "   [Epsilon]\n");
    });
  }

  @Test
  public void testIcreaseDecreaseFromLeafNode() {
    test(tree -> {
      select(tree, 10);
      testIncreaseDecrease(tree,
                           "-Root\n" +
                           " -Color\n" +
                           "  Red\n" +
                           "  Green\n" +
                           "  Blue\n" +
                           " +Digit\n" +
                           " -Letter\n" +
                           "  -Greek\n" +
                           "   Alpha\n" +
                           "   Beta\n" +
                           "   [Gamma]\n" +
                           "   Delta\n" +
                           "   Epsilon\n",
                           // 1 // 1 // 1 // select siblings of node
                           "-Root\n" +
                           " -Color\n" +
                           "  Red\n" +
                           "  Green\n" +
                           "  Blue\n" +
                           " +Digit\n" +
                           " -Letter\n" +
                           "  -Greek\n" +
                           "   [Alpha]\n" +
                           "   [Beta]\n" +
                           "   [Gamma]\n" +
                           "   [Delta]\n" +
                           "   [Epsilon]\n",
                           // 2 // 2 // 2 // select parent of node
                           "-Root\n" +
                           " -Color\n" +
                           "  Red\n" +
                           "  Green\n" +
                           "  Blue\n" +
                           " +Digit\n" +
                           " -Letter\n" +
                           "  -[Greek]\n" +
                           "   [Alpha]\n" +
                           "   [Beta]\n" +
                           "   [Gamma]\n" +
                           "   [Delta]\n" +
                           "   [Epsilon]\n",
                           // 3 // 3 // 3 // select grand parent of node
                           "-Root\n" +
                           " -Color\n" +
                           "  Red\n" +
                           "  Green\n" +
                           "  Blue\n" +
                           " +Digit\n" +
                           " -[Letter]\n" +
                           "  -[Greek]\n" +
                           "   [Alpha]\n" +
                           "   [Beta]\n" +
                           "   [Gamma]\n" +
                           "   [Delta]\n" +
                           "   [Epsilon]\n",
                           // 4 // 4 // 4 // select siblings of grand parent
                           "-Root\n" +
                           " -[Color]\n" +
                           "  [Red]\n" +
                           "  [Green]\n" +
                           "  [Blue]\n" +
                           " +[Digit]\n" +
                           " -[Letter]\n" +
                           "  -[Greek]\n" +
                           "   [Alpha]\n" +
                           "   [Beta]\n" +
                           "   [Gamma]\n" +
                           "   [Delta]\n" +
                           "   [Epsilon]\n");
    });
  }

  @Test
  public void testIcreaseDecreaseFromCollapsedNode() {
    test(tree -> {
      select(tree, 5);
      testIncreaseDecrease(tree,
                           "-Root\n" +
                           " -Color\n" +
                           "  Red\n" +
                           "  Green\n" +
                           "  Blue\n" +
                           " +[Digit]\n" +
                           " -Letter\n" +
                           "  -Greek\n" +
                           "   Alpha\n" +
                           "   Beta\n" +
                           "   Gamma\n" +
                           "   Delta\n" +
                           "   Epsilon\n",
                           // 1 // 1 // 1 // select siblings of node
                           "-Root\n" +
                           " -[Color]\n" +
                           "  [Red]\n" +
                           "  [Green]\n" +
                           "  [Blue]\n" +
                           " +[Digit]\n" +
                           " -[Letter]\n" +
                           "  -[Greek]\n" +
                           "   [Alpha]\n" +
                           "   [Beta]\n" +
                           "   [Gamma]\n" +
                           "   [Delta]\n" +
                           "   [Epsilon]\n");
    });
  }

  @Test
  public void testIcreaseDecreaseFromExpandedNode() {
    test(tree -> {
      select(tree, 7);
      testIncreaseDecrease(tree,
                           "-Root\n" +
                           " -Color\n" +
                           "  Red\n" +
                           "  Green\n" +
                           "  Blue\n" +
                           " +Digit\n" +
                           " -Letter\n" +
                           "  -[Greek]\n" +
                           "   Alpha\n" +
                           "   Beta\n" +
                           "   Gamma\n" +
                           "   Delta\n" +
                           "   Epsilon\n",
                           // 1 // 1 // 1 // select children of node
                           "-Root\n" +
                           " -Color\n" +
                           "  Red\n" +
                           "  Green\n" +
                           "  Blue\n" +
                           " +Digit\n" +
                           " -Letter\n" +
                           "  -[Greek]\n" +
                           "   [Alpha]\n" +
                           "   [Beta]\n" +
                           "   [Gamma]\n" +
                           "   [Delta]\n" +
                           "   [Epsilon]\n",
                           // 2 // 2 // 2 // select parent of node
                           "-Root\n" +
                           " -Color\n" +
                           "  Red\n" +
                           "  Green\n" +
                           "  Blue\n" +
                           " +Digit\n" +
                           " -[Letter]\n" +
                           "  -[Greek]\n" +
                           "   [Alpha]\n" +
                           "   [Beta]\n" +
                           "   [Gamma]\n" +
                           "   [Delta]\n" +
                           "   [Epsilon]\n",
                           // 3 // 3 // 3 // select siblings of parent
                           "-Root\n" +
                           " -[Color]\n" +
                           "  [Red]\n" +
                           "  [Green]\n" +
                           "  [Blue]\n" +
                           " +[Digit]\n" +
                           " -[Letter]\n" +
                           "  -[Greek]\n" +
                           "   [Alpha]\n" +
                           "   [Beta]\n" +
                           "   [Gamma]\n" +
                           "   [Delta]\n" +
                           "   [Epsilon]\n");
    });
  }

  @Test
  public void testIcreaseDecreaseFromExpandedParentNode() {
    test(tree -> {
      select(tree, 6);
      testIncreaseDecrease(tree,
                           "-Root\n" +
                           " -Color\n" +
                           "  Red\n" +
                           "  Green\n" +
                           "  Blue\n" +
                           " +Digit\n" +
                           " -[Letter]\n" +
                           "  -Greek\n" +
                           "   Alpha\n" +
                           "   Beta\n" +
                           "   Gamma\n" +
                           "   Delta\n" +
                           "   Epsilon\n",
                           // 1 // 1 // 1 // select descendants of node
                           "-Root\n" +
                           " -Color\n" +
                           "  Red\n" +
                           "  Green\n" +
                           "  Blue\n" +
                           " +Digit\n" +
                           " -[Letter]\n" +
                           "  -[Greek]\n" +
                           "   [Alpha]\n" +
                           "   [Beta]\n" +
                           "   [Gamma]\n" +
                           "   [Delta]\n" +
                           "   [Epsilon]\n",
                           // 2 // 2 // 2 // select siblings of node
                           "-Root\n" +
                           " -[Color]\n" +
                           "  [Red]\n" +
                           "  [Green]\n" +
                           "  [Blue]\n" +
                           " +[Digit]\n" +
                           " -[Letter]\n" +
                           "  -[Greek]\n" +
                           "   [Alpha]\n" +
                           "   [Beta]\n" +
                           "   [Gamma]\n" +
                           "   [Delta]\n" +
                           "   [Epsilon]\n");
    });
  }

  @Test
  public void testIcreaseDecreaseWithoutCapture() {
    test(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION, tree -> {
      select(tree, 3, 10);
      testIncreaseDecrease(tree,
                           "-Root\n" +
                           " -Color\n" +
                           "  Red\n" +
                           "  [Green]\n" +
                           "  Blue\n" +
                           " +Digit\n" +
                           " -Letter\n" +
                           "  -Greek\n" +
                           "   Alpha\n" +
                           "   Beta\n" +
                           "   [Gamma]\n" +
                           "   Delta\n" +
                           "   Epsilon\n",
                           // 1 // 1 // 1 // select siblings of second node
                           "-Root\n" +
                           " -Color\n" +
                           "  Red\n" +
                           "  [Green]\n" +
                           "  Blue\n" +
                           " +Digit\n" +
                           " -Letter\n" +
                           "  -Greek\n" +
                           "   [Alpha]\n" +
                           "   [Beta]\n" +
                           "   [Gamma]\n" +
                           "   [Delta]\n" +
                           "   [Epsilon]\n",
                           // 2 // 2 // 2 // select parent of second node
                           "-Root\n" +
                           " -Color\n" +
                           "  Red\n" +
                           "  [Green]\n" +
                           "  Blue\n" +
                           " +Digit\n" +
                           " -Letter\n" +
                           "  -[Greek]\n" +
                           "   [Alpha]\n" +
                           "   [Beta]\n" +
                           "   [Gamma]\n" +
                           "   [Delta]\n" +
                           "   [Epsilon]\n",
                           // 3 // 3 // 3 // grand parent of node
                           "-Root\n" +
                           " -Color\n" +
                           "  Red\n" +
                           "  [Green]\n" +
                           "  Blue\n" +
                           " +Digit\n" +
                           " -[Letter]\n" +
                           "  -[Greek]\n" +
                           "   [Alpha]\n" +
                           "   [Beta]\n" +
                           "   [Gamma]\n" +
                           "   [Delta]\n" +
                           "   [Epsilon]\n");
    });
  }

  @Test
  public void testIcreaseDecreaseWithCapture() {
    test(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION, tree -> {
      select(tree, 10, 3);
      testIncrease(tree,
                   "-Root\n" +
                   " -Color\n" +
                   "  Red\n" +
                   "  [Green]\n" +
                   "  Blue\n" +
                   " +Digit\n" +
                   " -Letter\n" +
                   "  -Greek\n" +
                   "   Alpha\n" +
                   "   Beta\n" +
                   "   [Gamma]\n" +
                   "   Delta\n" +
                   "   Epsilon\n",
                   // 1 // 1 // 1 // select siblings of second node
                   "-Root\n" +
                   " -Color\n" +
                   "  [Red]\n" +
                   "  [Green]\n" +
                   "  [Blue]\n" +
                   " +Digit\n" +
                   " -Letter\n" +
                   "  -Greek\n" +
                   "   Alpha\n" +
                   "   Beta\n" +
                   "   [Gamma]\n" +
                   "   Delta\n" +
                   "   Epsilon\n",
                   // 2 // 2 // 2 // select parent of second node
                   "-Root\n" +
                   " -[Color]\n" +
                   "  [Red]\n" +
                   "  [Green]\n" +
                   "  [Blue]\n" +
                   " +Digit\n" +
                   " -Letter\n" +
                   "  -Greek\n" +
                   "   Alpha\n" +
                   "   Beta\n" +
                   "   [Gamma]\n" +
                   "   Delta\n" +
                   "   Epsilon\n",
                   // 3 // 3 // 3 // select siblings of parent
                   "-Root\n" +
                   " -[Color]\n" +
                   "  [Red]\n" +
                   "  [Green]\n" +
                   "  [Blue]\n" +
                   " +[Digit]\n" +
                   " -[Letter]\n" +
                   "  -[Greek]\n" +
                   "   [Alpha]\n" +
                   "   [Beta]\n" +
                   "   [Gamma]\n" +
                   "   [Delta]\n" +
                   "   [Epsilon]\n");
      testDecrease(tree,
                   "-Root\n" +
                   " -[Color]\n" +
                   "  [Red]\n" +
                   "  [Green]\n" +
                   "  [Blue]\n" +
                   " +[Digit]\n" +
                   " -[Letter]\n" +
                   "  -[Greek]\n" +
                   "   [Alpha]\n" +
                   "   [Beta]\n" +
                   "   [Gamma]\n" +
                   "   [Delta]\n" +
                   "   [Epsilon]\n",
                   // 1 // 1 // 1 // unselect siblings of parent
                   "-Root\n" +
                   " -[Color]\n" +
                   "  [Red]\n" +
                   "  [Green]\n" +
                   "  [Blue]\n" +
                   " +Digit\n" +
                   " -Letter\n" +
                   "  -Greek\n" +
                   "   Alpha\n" +
                   "   Beta\n" +
                   "   Gamma\n" + // captured
                   "   Delta\n" +
                   "   Epsilon\n",
                   // 2 // 2 // 2 // unselect parent of second node
                   "-Root\n" +
                   " -Color\n" +
                   "  [Red]\n" +
                   "  [Green]\n" +
                   "  [Blue]\n" +
                   " +Digit\n" +
                   " -Letter\n" +
                   "  -Greek\n" +
                   "   Alpha\n" +
                   "   Beta\n" +
                   "   Gamma\n" + // captured
                   "   Delta\n" +
                   "   Epsilon\n",
                   // 3 // 3 // 3 // select siblings of second node
                   "-Root\n" +
                   " -Color\n" +
                   "  Red\n" +
                   "  [Green]\n" +
                   "  Blue\n" +
                   " +Digit\n" +
                   " -Letter\n" +
                   "  -Greek\n" +
                   "   Alpha\n" +
                   "   Beta\n" +
                   "   Gamma\n" + // captured
                   "   Delta\n" +
                   "   Epsilon\n");
    });
  }

  private static DefaultMutableTreeNode node(@NotNull Object object, Object... children) {
    if (object instanceof DefaultMutableTreeNode && ArrayUtil.isEmpty(children)) return (DefaultMutableTreeNode)object;
    if (object instanceof TreeNode) throw new IllegalArgumentException("do not use a tree node as a node content");
    DefaultMutableTreeNode node = new DefaultMutableTreeNode(object);
    for (Object child : children) node.add(node(child));
    return node;
  }

  private static DefaultMutableTreeNode root() {
    return node("Root",
                node("Color",
                     node("Red"),
                     node("Green"),
                     node("Blue")),
                node("Digit",
                     node("One"),
                     node("Two"),
                     node("Three"),
                     node("Four"),
                     node("Five"),
                     node("Six"),
                     node("Seven"),
                     node("Eight"),
                     node("Nine")),
                node("Letter",
                     node("Greek",
                          node("Alpha"),
                          node("Beta"),
                          node("Gamma"),
                          node("Delta"),
                          node("Epsilon"))));
  }

  private static int normalize(JTree tree, int row) {
    return tree.isRootVisible() ? row : row - 1;
  }

  private static void select(JTree tree, int... rows) {
    for (int row : rows) tree.addSelectionRow(normalize(tree, row));
  }

  private static void assertTree(JTree tree, String expected) {
    PlatformTestUtil.assertTreeEqual(tree, expected, true);
  }

  private static void test(Consumer<JTree> consumer) {
    test(true, consumer);
    test(false, consumer);
  }

  private static void test(int selectionMode, Consumer<JTree> consumer) {
    test(selectionMode, true, consumer);
    test(selectionMode, false, consumer);
  }

  private static void test(boolean rootVisible, Consumer<JTree> consumer) {
    //TODO: test(TreeSelectionModel.CONTIGUOUS_TREE_SELECTION, rootVisible, consumer);
    test(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION, rootVisible, consumer);
  }

  private static void test(int selectionMode, boolean rootVisible, Consumer<JTree> consumer) {
    @SuppressWarnings("UndesirableClassUsage")
    JTree tree = new JTree(new DefaultTreeModel(root()));
    tree.getSelectionModel().setSelectionMode(selectionMode);
    tree.setRootVisible(rootVisible);
    TreeUtil.promiseExpandAll(tree);
    tree.collapseRow(normalize(tree, 5));
    tree.clearSelection();
    assertTree(tree, "-Root\n" +
                     " -Color\n" +
                     "  Red\n" +
                     "  Green\n" +
                     "  Blue\n" +
                     " +Digit\n" +
                     " -Letter\n" +
                     "  -Greek\n" +
                     "   Alpha\n" +
                     "   Beta\n" +
                     "   Gamma\n" +
                     "   Delta\n" +
                     "   Epsilon\n");
    consumer.accept(tree);
  }
}
