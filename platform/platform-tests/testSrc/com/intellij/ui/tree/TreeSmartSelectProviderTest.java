// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

import com.intellij.testFramework.TestApplicationManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreeSelectionModel;
import java.util.function.Consumer;

import static com.intellij.ui.tree.TreeTestUtil.node;
import static com.intellij.util.ui.tree.TreeUtil.promiseExpandAll;
import static com.intellij.util.ui.tree.TreeUtilTest.waitForTestOnEDT;
import static java.awt.EventQueue.isDispatchThread;

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

  private void testDecreaseDoNotClearSelection(int row, String expected) {
    test(tree -> {
      select(tree, row);
      testDecrease(tree, expected, expected);
    });
    test(TreeSelectionModel.SINGLE_TREE_SELECTION, tree -> {
      select(tree, row);
      testDecrease(tree, expected, expected);
    });
  }

  @Before
  public void setUp() {
    TestApplicationManager.getInstance();
  }

  @Test
  public void testDecreaseFromLeafNode() {
    testDecreaseDoNotClearSelection(10, """
      -Root
       -Color
        Red
        Green
        Blue
       +Digit
       -Letter
        -Greek
         Alpha
         Beta
         [Gamma]
         Delta
         Epsilon
      """);
  }

  @Test
  public void testDecreaseFromCollapsedNode() {
    testDecreaseDoNotClearSelection(5, """
      -Root
       -Color
        Red
        Green
        Blue
       +[Digit]
       -Letter
        -Greek
         Alpha
         Beta
         Gamma
         Delta
         Epsilon
      """);
  }

  @Test
  public void testDecreaseFromExpandedNode() {
    testDecreaseDoNotClearSelection(7, """
      -Root
       -Color
        Red
        Green
        Blue
       +Digit
       -Letter
        -[Greek]
         Alpha
         Beta
         Gamma
         Delta
         Epsilon
      """);
  }

  @Test
  public void testDecreaseFromExpandedParentNode() {
    testDecreaseDoNotClearSelection(6, """
      -Root
       -Color
        Red
        Green
        Blue
       +Digit
       -[Letter]
        -Greek
         Alpha
         Beta
         Gamma
         Delta
         Epsilon
      """);
  }

  @Test
  public void testIncreaseToVisibleRoot() {
    test(true, tree -> {
      select(tree, 5);
      testIncrease(tree,
                   """
                     -Root
                      -Color
                       Red
                       Green
                       Blue
                      +[Digit]
                      -Letter
                       -Greek
                        Alpha
                        Beta
                        Gamma
                        Delta
                        Epsilon
                     """,
                   // 1 // 1 // 1 // select siblings of node
                   """
                     -Root
                      -[Color]
                       [Red]
                       [Green]
                       [Blue]
                      +[Digit]
                      -[Letter]
                       -[Greek]
                        [Alpha]
                        [Beta]
                        [Gamma]
                        [Delta]
                        [Epsilon]
                     """,
                   // 2 // 2 // 2 // select visible root
                   """
                     -[Root]
                      -[Color]
                       [Red]
                       [Green]
                       [Blue]
                      +[Digit]
                      -[Letter]
                       -[Greek]
                        [Alpha]
                        [Beta]
                        [Gamma]
                        [Delta]
                        [Epsilon]
                     """,
                   // 3 // 3 // 3 // do nothing
                   """
                     -[Root]
                      -[Color]
                       [Red]
                       [Green]
                       [Blue]
                      +[Digit]
                      -[Letter]
                       -[Greek]
                        [Alpha]
                        [Beta]
                        [Gamma]
                        [Delta]
                        [Epsilon]
                     """);
    });
  }

  @Test
  public void testIncreaseToHiddenRoot() {
    test(false, tree -> {
      select(tree, 5);
      testIncrease(tree,
                   """
                     -Root
                      -Color
                       Red
                       Green
                       Blue
                      +[Digit]
                      -Letter
                       -Greek
                        Alpha
                        Beta
                        Gamma
                        Delta
                        Epsilon
                     """,
                   // 1 // 1 // 1 // select siblings of node
                   """
                     -Root
                      -[Color]
                       [Red]
                       [Green]
                       [Blue]
                      +[Digit]
                      -[Letter]
                       -[Greek]
                        [Alpha]
                        [Beta]
                        [Gamma]
                        [Delta]
                        [Epsilon]
                     """,
                   // 2 // 2 // 2 // do nothing
                   """
                     -Root
                      -[Color]
                       [Red]
                       [Green]
                       [Blue]
                      +[Digit]
                      -[Letter]
                       -[Greek]
                        [Alpha]
                        [Beta]
                        [Gamma]
                        [Delta]
                        [Epsilon]
                     """);
    });
  }

  @Test
  public void testIncreaseDecreaseFromLeafNode() {
    test(tree -> {
      select(tree, 10);
      testIncreaseDecrease(tree,
                           """
                             -Root
                              -Color
                               Red
                               Green
                               Blue
                              +Digit
                              -Letter
                               -Greek
                                Alpha
                                Beta
                                [Gamma]
                                Delta
                                Epsilon
                             """,
                           // 1 // 1 // 1 // select siblings of node
                           """
                             -Root
                              -Color
                               Red
                               Green
                               Blue
                              +Digit
                              -Letter
                               -Greek
                                [Alpha]
                                [Beta]
                                [Gamma]
                                [Delta]
                                [Epsilon]
                             """,
                           // 2 // 2 // 2 // select parent of node
                           """
                             -Root
                              -Color
                               Red
                               Green
                               Blue
                              +Digit
                              -Letter
                               -[Greek]
                                [Alpha]
                                [Beta]
                                [Gamma]
                                [Delta]
                                [Epsilon]
                             """,
                           // 3 // 3 // 3 // select grand parent of node
                           """
                             -Root
                              -Color
                               Red
                               Green
                               Blue
                              +Digit
                              -[Letter]
                               -[Greek]
                                [Alpha]
                                [Beta]
                                [Gamma]
                                [Delta]
                                [Epsilon]
                             """,
                           // 4 // 4 // 4 // select siblings of grand parent
                           """
                             -Root
                              -[Color]
                               [Red]
                               [Green]
                               [Blue]
                              +[Digit]
                              -[Letter]
                               -[Greek]
                                [Alpha]
                                [Beta]
                                [Gamma]
                                [Delta]
                                [Epsilon]
                             """);
    });
  }

  @Test
  public void testIncreaseDecreaseFromCollapsedNode() {
    test(tree -> {
      select(tree, 5);
      testIncreaseDecrease(tree,
                           """
                             -Root
                              -Color
                               Red
                               Green
                               Blue
                              +[Digit]
                              -Letter
                               -Greek
                                Alpha
                                Beta
                                Gamma
                                Delta
                                Epsilon
                             """,
                           // 1 // 1 // 1 // select siblings of node
                           """
                             -Root
                              -[Color]
                               [Red]
                               [Green]
                               [Blue]
                              +[Digit]
                              -[Letter]
                               -[Greek]
                                [Alpha]
                                [Beta]
                                [Gamma]
                                [Delta]
                                [Epsilon]
                             """);
    });
  }

  @Test
  public void testIncreaseDecreaseFromExpandedNode() {
    test(tree -> {
      select(tree, 7);
      testIncreaseDecrease(tree,
                           """
                             -Root
                              -Color
                               Red
                               Green
                               Blue
                              +Digit
                              -Letter
                               -[Greek]
                                Alpha
                                Beta
                                Gamma
                                Delta
                                Epsilon
                             """,
                           // 1 // 1 // 1 // select children of node
                           """
                             -Root
                              -Color
                               Red
                               Green
                               Blue
                              +Digit
                              -Letter
                               -[Greek]
                                [Alpha]
                                [Beta]
                                [Gamma]
                                [Delta]
                                [Epsilon]
                             """,
                           // 2 // 2 // 2 // select parent of node
                           """
                             -Root
                              -Color
                               Red
                               Green
                               Blue
                              +Digit
                              -[Letter]
                               -[Greek]
                                [Alpha]
                                [Beta]
                                [Gamma]
                                [Delta]
                                [Epsilon]
                             """,
                           // 3 // 3 // 3 // select siblings of parent
                           """
                             -Root
                              -[Color]
                               [Red]
                               [Green]
                               [Blue]
                              +[Digit]
                              -[Letter]
                               -[Greek]
                                [Alpha]
                                [Beta]
                                [Gamma]
                                [Delta]
                                [Epsilon]
                             """);
    });
  }

  @Test
  public void testIncreaseDecreaseFromExpandedParentNode() {
    test(tree -> {
      select(tree, 6);
      testIncreaseDecrease(tree,
                           """
                             -Root
                              -Color
                               Red
                               Green
                               Blue
                              +Digit
                              -[Letter]
                               -Greek
                                Alpha
                                Beta
                                Gamma
                                Delta
                                Epsilon
                             """,
                           // 1 // 1 // 1 // select descendants of node
                           """
                             -Root
                              -Color
                               Red
                               Green
                               Blue
                              +Digit
                              -[Letter]
                               -[Greek]
                                [Alpha]
                                [Beta]
                                [Gamma]
                                [Delta]
                                [Epsilon]
                             """,
                           // 2 // 2 // 2 // select siblings of node
                           """
                             -Root
                              -[Color]
                               [Red]
                               [Green]
                               [Blue]
                              +[Digit]
                              -[Letter]
                               -[Greek]
                                [Alpha]
                                [Beta]
                                [Gamma]
                                [Delta]
                                [Epsilon]
                             """);
    });
  }

  @Test
  public void testIncreaseDecreaseWithoutCapture() {
    test(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION, tree -> {
      select(tree, 3, 10);
      testIncreaseDecrease(tree,
                           """
                             -Root
                              -Color
                               Red
                               [Green]
                               Blue
                              +Digit
                              -Letter
                               -Greek
                                Alpha
                                Beta
                                [Gamma]
                                Delta
                                Epsilon
                             """,
                           // 1 // 1 // 1 // select siblings of second node
                           """
                             -Root
                              -Color
                               Red
                               [Green]
                               Blue
                              +Digit
                              -Letter
                               -Greek
                                [Alpha]
                                [Beta]
                                [Gamma]
                                [Delta]
                                [Epsilon]
                             """,
                           // 2 // 2 // 2 // select parent of second node
                           """
                             -Root
                              -Color
                               Red
                               [Green]
                               Blue
                              +Digit
                              -Letter
                               -[Greek]
                                [Alpha]
                                [Beta]
                                [Gamma]
                                [Delta]
                                [Epsilon]
                             """,
                           // 3 // 3 // 3 // grand parent of node
                           """
                             -Root
                              -Color
                               Red
                               [Green]
                               Blue
                              +Digit
                              -[Letter]
                               -[Greek]
                                [Alpha]
                                [Beta]
                                [Gamma]
                                [Delta]
                                [Epsilon]
                             """);
    });
  }

  @Test
  public void testIncreaseDecreaseWithCapture() {
    test(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION, tree -> {
      select(tree, 10, 3);
      testIncrease(tree,
                   """
                     -Root
                      -Color
                       Red
                       [Green]
                       Blue
                      +Digit
                      -Letter
                       -Greek
                        Alpha
                        Beta
                        [Gamma]
                        Delta
                        Epsilon
                     """,
                   // 1 // 1 // 1 // select siblings of second node
                   """
                     -Root
                      -Color
                       [Red]
                       [Green]
                       [Blue]
                      +Digit
                      -Letter
                       -Greek
                        Alpha
                        Beta
                        [Gamma]
                        Delta
                        Epsilon
                     """,
                   // 2 // 2 // 2 // select parent of second node
                   """
                     -Root
                      -[Color]
                       [Red]
                       [Green]
                       [Blue]
                      +Digit
                      -Letter
                       -Greek
                        Alpha
                        Beta
                        [Gamma]
                        Delta
                        Epsilon
                     """,
                   // 3 // 3 // 3 // select siblings of parent
                   """
                     -Root
                      -[Color]
                       [Red]
                       [Green]
                       [Blue]
                      +[Digit]
                      -[Letter]
                       -[Greek]
                        [Alpha]
                        [Beta]
                        [Gamma]
                        [Delta]
                        [Epsilon]
                     """);
      testDecrease(tree,
                   """
                     -Root
                      -[Color]
                       [Red]
                       [Green]
                       [Blue]
                      +[Digit]
                      -[Letter]
                       -[Greek]
                        [Alpha]
                        [Beta]
                        [Gamma]
                        [Delta]
                        [Epsilon]
                     """,
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

  @Test
  public void testSingleTreeSelection() {
    test(TreeSelectionModel.SINGLE_TREE_SELECTION, tree -> {
      select(tree, 10);
      testIncreaseDecrease(tree,
                           """
                             -Root
                              -Color
                               Red
                               Green
                               Blue
                              +Digit
                              -Letter
                               -Greek
                                Alpha
                                Beta
                                [Gamma]
                                Delta
                                Epsilon
                             """,
                           // 1 // 1 // 1 // do nothing
                           """
                             -Root
                              -Color
                               Red
                               Green
                               Blue
                              +Digit
                              -Letter
                               -Greek
                                Alpha
                                Beta
                                [Gamma]
                                Delta
                                Epsilon
                             """,
                           // 2 // 2 // 2 // do nothing
                           """
                             -Root
                              -Color
                               Red
                               Green
                               Blue
                              +Digit
                              -Letter
                               -Greek
                                Alpha
                                Beta
                                [Gamma]
                                Delta
                                Epsilon
                             """);
    });
  }

  @Test // copied from TreeSmartSelectTest.testSelectionDoesntJumpTooQuickly
  public void testSelectionDoesntJumpTooQuickly() {
    @SuppressWarnings("UndesirableClassUsage")
    JTree tree = new JTree(new DefaultTreeModel(
      node("/",
           node("ktor",
                node("ktor-core"),
                node("ktor-features",
                     node("jetty-http-client"),
                     node("ktor-locations",
                          node("src",
                               node("asdsd.asdas.asdas",
                                    node("a"),
                                    node("b"),
                                    node("c"))),
                          node("tests",
                               node("fooo")),
                          node("zar.txt"),
                          node("zoo.txt")))))));
    TreeTestUtil.assertTreeUI(tree);
    expandAll(tree);
    waitForTestOnEDT(() -> {
      tree.setSelectionRow(10);
      Assert.assertEquals(15, tree.getRowCount());
      assertTree(tree, """
        -/
         -ktor
          ktor-core
          -ktor-features
           jetty-http-client
           -ktor-locations
            -src
             -asdsd.asdas.asdas
              a
              b
              [c]
            -tests
             fooo
            zar.txt
            zoo.txt
        """);

      TreeSmartSelectProvider provider = new TreeSmartSelectProvider();
      provider.increaseSelection(tree);
      provider.increaseSelection(tree);
      provider.increaseSelection(tree);
      provider.increaseSelection(tree);
      provider.increaseSelection(tree);
      assertTree(tree, """
        -/
         -ktor
          ktor-core
          -ktor-features
           jetty-http-client
           -[ktor-locations]
            -[src]
             -[asdsd.asdas.asdas]
              [a]
              [b]
              [c]
            -[tests]
             [fooo]
            [zar.txt]
            [zoo.txt]
        """);
    });
  }

  private static TreeNode root() {
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
    String actual = new TreeTestUtil(tree).withSelection().toString();
    Assert.assertEquals(expected, !tree.isRootVisible() ? "-Root\n" + actual : actual);
  }

  private static void test(Consumer<? super JTree> consumer) {
    test(true, consumer);
    test(false, consumer);
  }

  private static void test(int selectionMode, Consumer<? super JTree> consumer) {
    test(selectionMode, true, consumer);
    test(selectionMode, false, consumer);
  }

  private static void test(boolean rootVisible, Consumer<? super JTree> consumer) {
    test(TreeSelectionModel.CONTIGUOUS_TREE_SELECTION, rootVisible, consumer);
    test(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION, rootVisible, consumer);
  }

  private static void test(int selectionMode, boolean rootVisible, Consumer<? super JTree> consumer) {
    @SuppressWarnings("UndesirableClassUsage")
    JTree tree = new JTree(new DefaultTreeModel(root()));
    TreeTestUtil.assertTreeUI(tree);
    tree.getSelectionModel().setSelectionMode(selectionMode);
    tree.setRootVisible(rootVisible);
    expandAll(tree);
    waitForTestOnEDT(() -> {
      tree.collapseRow(normalize(tree, 5));
      tree.clearSelection();
      assertTree(tree, """
        -Root
         -Color
          Red
          Green
          Blue
         +Digit
         -Letter
          -Greek
           Alpha
           Beta
           Gamma
           Delta
           Epsilon
        """);
      consumer.accept(tree);
    });
  }

  private static void expandAll(JTree tree) {
    Assert.assertFalse("unexpected thread", isDispatchThread());
    try {
      // the following method expands nodes on EDT,
      // so in this case we should pause the main thread
      promiseExpandAll(tree).blockingGet(10000);
    }
    catch (Exception exception) {
      throw new AssertionError(exception);
    }
  }
}
