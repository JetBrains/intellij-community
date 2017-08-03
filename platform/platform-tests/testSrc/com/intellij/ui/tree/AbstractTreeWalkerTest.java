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
package com.intellij.ui.tree;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import java.util.ArrayList;
import java.util.Collection;

import static com.intellij.util.ReflectionUtil.getField;
import static org.junit.Assert.assertEquals;

public class AbstractTreeWalkerTest {
  private final static boolean PRINT = false;

  @Test
  public void testInterrupt() {
    Node root = createRoot();
    test(root, 1, path -> TreeVisitor.Action.INTERRUPT, root);
    test(null, 0, path -> TreeVisitor.Action.INTERRUPT);
  }

  @Test
  public void testContinue() {
    test(createRoot(), 21, path -> TreeVisitor.Action.CONTINUE);
    test(null, 0, path -> TreeVisitor.Action.CONTINUE);
  }

  @Test
  public void testSkipSiblings() {
    test(createRoot(), 1, path -> TreeVisitor.Action.SKIP_SIBLINGS);
    test(null, 0, path -> TreeVisitor.Action.SKIP_SIBLINGS);
  }

  @Test
  public void testSkipChildren() {
    test(createRoot(), 1, path -> TreeVisitor.Action.SKIP_CHILDREN);
    test(null, 0, path -> TreeVisitor.Action.SKIP_CHILDREN);
  }

  @Test
  public void testDeepVisit() {
    testDeepVisit(1);
    testDeepVisit(10);
    testDeepVisit(100);
    testDeepVisit(1000);
    if (PRINT) return;
    testDeepVisit(10000);
    testDeepVisit(100000);
  }

  private static void testDeepVisit(int count) {
    Node node = new Node(count);
    for (int i = 1; i < count; i++) node = new Node(count - i, node);
    test(node, count, path -> TreeVisitor.Action.CONTINUE);
  }

  @Test
  public void testDoubleStart() {
    /*
    Walker walker = new Walker(path -> TreeVisitor.Action.CONTINUE, null, 0);
    assertResult(walker);
    */
  }

  @Test
  public void testFinder() {
    TreeNode root = createRoot();
    TreeNode color = root.getChildAt(0);
    TreeNode digit = root.getChildAt(1);
    TreeNode greek = root.getChildAt(2);
    test(root, 1, createFinder(root), root);
    test(root, 2, createFinder(color), root, color);
    test(root, 3, createFinder(color.getChildAt(0)), root, color, color.getChildAt(0));
    test(root, 4, createFinder(color.getChildAt(1)), root, color, color.getChildAt(1));
    test(root, 5, createFinder(color.getChildAt(2)), root, color, color.getChildAt(2));
    test(root, 3, createFinder(digit), root, digit);
    test(root, 4, createFinder(digit.getChildAt(0)), root, digit, digit.getChildAt(0));
    test(root, 5, createFinder(digit.getChildAt(1)), root, digit, digit.getChildAt(1));
    test(root, 6, createFinder(digit.getChildAt(2)), root, digit, digit.getChildAt(2));
    test(root, 7, createFinder(digit.getChildAt(3)), root, digit, digit.getChildAt(3));
    test(root, 8, createFinder(digit.getChildAt(4)), root, digit, digit.getChildAt(4));
    test(root, 9, createFinder(digit.getChildAt(5)), root, digit, digit.getChildAt(5));
    test(root, 10, createFinder(digit.getChildAt(6)), root, digit, digit.getChildAt(6));
    test(root, 11, createFinder(digit.getChildAt(7)), root, digit, digit.getChildAt(7));
    test(root, 12, createFinder(digit.getChildAt(8)), root, digit, digit.getChildAt(8));
    test(root, 4, createFinder(greek), root, greek);
    test(root, 5, createFinder(greek.getChildAt(0)), root, greek, greek.getChildAt(0));
    test(root, 6, createFinder(greek.getChildAt(1)), root, greek, greek.getChildAt(1));
    test(root, 7, createFinder(greek.getChildAt(2)), root, greek, greek.getChildAt(2));
    test(root, 8, createFinder(greek.getChildAt(3)), root, greek, greek.getChildAt(3));
    test(root, 9, createFinder(greek.getChildAt(4)), root, greek, greek.getChildAt(4));
  }

  @Test
  public void testColorFinder() {
    Node color = createColorNode();
    Node digit = createDigitNode();
    Node greek = createGreekNode();
    Node root = new Node("root", color, digit, greek);
    TreePath parent = new TreePath(root);
    test(parent, color, 1, createFinder(root));
    test(parent, color, 1, createFinder(color), root, color);
    test(parent, color, 2, createFinder(color.getChildAt(0)), root, color, color.getChildAt(0));
    test(parent, color, 3, createFinder(color.getChildAt(1)), root, color, color.getChildAt(1));
    test(parent, color, 4, createFinder(color.getChildAt(2)), root, color, color.getChildAt(2));
    test(parent, color, 1, createFinder(digit));
    test(parent, color, 1, createFinder(greek));
  }

  @Test
  public void testDigitFinder() {
    TreeNode root = createRoot();
    TreeNode color = root.getChildAt(0);
    TreeNode digit = root.getChildAt(1);
    TreeNode greek = root.getChildAt(2);
    TreePath parent = new TreePath(root);
    test(parent, digit, 1, createFinder(root));
    test(parent, digit, 1, createFinder(color));
    test(parent, digit, 1, createFinder(digit), root, digit);
    test(parent, digit, 2, createFinder(digit.getChildAt(0)), root, digit, digit.getChildAt(0));
    test(parent, digit, 3, createFinder(digit.getChildAt(1)), root, digit, digit.getChildAt(1));
    test(parent, digit, 4, createFinder(digit.getChildAt(2)), root, digit, digit.getChildAt(2));
    test(parent, digit, 5, createFinder(digit.getChildAt(3)), root, digit, digit.getChildAt(3));
    test(parent, digit, 6, createFinder(digit.getChildAt(4)), root, digit, digit.getChildAt(4));
    test(parent, digit, 7, createFinder(digit.getChildAt(5)), root, digit, digit.getChildAt(5));
    test(parent, digit, 8, createFinder(digit.getChildAt(6)), root, digit, digit.getChildAt(6));
    test(parent, digit, 9, createFinder(digit.getChildAt(7)), root, digit, digit.getChildAt(7));
    test(parent, digit, 10, createFinder(digit.getChildAt(8)), root, digit, digit.getChildAt(8));
    test(parent, digit, 1, createFinder(greek));
  }

  @Test
  public void testGreekFinder() {
    TreeNode root = createRoot();
    TreeNode color = root.getChildAt(0);
    TreeNode digit = root.getChildAt(1);
    TreeNode greek = root.getChildAt(2);
    TreePath parent = new TreePath(root);
    test(parent, greek, 1, createFinder(root));
    test(parent, greek, 1, createFinder(color));
    test(parent, greek, 1, createFinder(digit));
    test(parent, greek, 1, createFinder(greek), root, greek);
    test(parent, greek, 2, createFinder(greek.getChildAt(0)), root, greek, greek.getChildAt(0));
    test(parent, greek, 3, createFinder(greek.getChildAt(1)), root, greek, greek.getChildAt(1));
    test(parent, greek, 4, createFinder(greek.getChildAt(2)), root, greek, greek.getChildAt(2));
    test(parent, greek, 5, createFinder(greek.getChildAt(3)), root, greek, greek.getChildAt(3));
    test(parent, greek, 6, createFinder(greek.getChildAt(4)), root, greek, greek.getChildAt(4));
  }

  private static TreeVisitor createFinder(TreeNode node) {
    return new TreeVisitor.Finder() {
      @Override
      protected boolean found(@NotNull TreePath path) {
        return node.equals(path.getLastPathComponent());
      }

      @Override
      protected boolean contains(@NotNull TreePath path) {
        Object component = path.getLastPathComponent();
        return component instanceof Node && node instanceof Node && ((Node)node).isNodeAncestor((Node)component);
      }
    };
  }

  @Test
  public void testPathFinder() {
    TreeNode root = createRoot();
    TreeNode color = root.getChildAt(0);
    TreeNode digit = root.getChildAt(1);
    TreeNode greek = root.getChildAt(2);
    test(root, 1, createPathFinder("root"), root);
    test(root, 1, createPathFinder("toor")); // not found
    test(root, 2, createPathFinder("root", "color"), root, color);
    test(root, 4, createPathFinder("root", "roloc")); // not found
    test(root, 3, createPathFinder("root", "color", "red"), root, color, color.getChildAt(0));
    test(root, 7, createPathFinder("root", "color", "der")); // not found
    test(root, 4, createPathFinder("root", "color", "green"), root, color, color.getChildAt(1));
    test(root, 7, createPathFinder("root", "color", "neerg")); // not found
    test(root, 5, createPathFinder("root", "color", "blue"), root, color, color.getChildAt(2));
    test(root, 7, createPathFinder("root", "color", "eulb")); // not found
    test(root, 3, createPathFinder("root", "digit"), root, digit);
    test(root, 4, createPathFinder("root", "tigid")); // not found
    test(root, 4, createPathFinder("root", "digit", "one"), root, digit, digit.getChildAt(0));
    test(root, 13, createPathFinder("root", "digit", "eno")); // not found
    test(root, 5, createPathFinder("root", "digit", "two"), root, digit, digit.getChildAt(1));
    test(root, 13, createPathFinder("root", "digit", "owt")); // not found
    test(root, 6, createPathFinder("root", "digit", "three"), root, digit, digit.getChildAt(2));
    test(root, 13, createPathFinder("root", "digit", "eerht")); // not found
    test(root, 7, createPathFinder("root", "digit", "four"), root, digit, digit.getChildAt(3));
    test(root, 13, createPathFinder("root", "digit", "ruof")); // not found
    test(root, 8, createPathFinder("root", "digit", "five"), root, digit, digit.getChildAt(4));
    test(root, 13, createPathFinder("root", "digit", "evif")); // not found
    test(root, 9, createPathFinder("root", "digit", "six"), root, digit, digit.getChildAt(5));
    test(root, 13, createPathFinder("root", "digit", "xis")); // not found
    test(root, 10, createPathFinder("root", "digit", "seven"), root, digit, digit.getChildAt(6));
    test(root, 13, createPathFinder("root", "digit", "neves")); // not found
    test(root, 11, createPathFinder("root", "digit", "eight"), root, digit, digit.getChildAt(7));
    test(root, 13, createPathFinder("root", "digit", "thgie")); // not found
    test(root, 12, createPathFinder("root", "digit", "nine"), root, digit, digit.getChildAt(8));
    test(root, 13, createPathFinder("root", "digit", "enin")); // not found
    test(root, 4, createPathFinder("root", "greek"), root, greek);
    test(root, 4, createPathFinder("root", "keerg")); // not found
    test(root, 5, createPathFinder("root", "greek", "alpha"), root, greek, greek.getChildAt(0));
    test(root, 9, createPathFinder("root", "greek", "ahpla")); // not found
    test(root, 6, createPathFinder("root", "greek", "beta"), root, greek, greek.getChildAt(1));
    test(root, 9, createPathFinder("root", "greek", "ateb")); // not found
    test(root, 7, createPathFinder("root", "greek", "gamma"), root, greek, greek.getChildAt(2));
    test(root, 9, createPathFinder("root", "greek", "ammag")); // not found
    test(root, 8, createPathFinder("root", "greek", "delta"), root, greek, greek.getChildAt(3));
    test(root, 9, createPathFinder("root", "greek", "atled")); // not found
    test(root, 9, createPathFinder("root", "greek", "epsilon"), root, greek, greek.getChildAt(4));
    test(root, 9, createPathFinder("root", "greek", "nolispe")); // not found
  }

  @Test
  public void testColorPathFinder() {
    TreeNode root = createRoot();
    TreeNode color = root.getChildAt(0);
    TreePath parent = new TreePath(root);
    test(parent, color, 1, createPathFinder("root"));
    test(parent, color, 1, createPathFinder("toor")); // not found
    test(parent, color, 1, createPathFinder("root", "color"), root, color);
    test(parent, color, 1, createPathFinder("root", "roloc")); // not found
    test(parent, color, 2, createPathFinder("root", "color", "red"), root, color, color.getChildAt(0));
    test(parent, color, 4, createPathFinder("root", "color", "der")); // not found
    test(parent, color, 3, createPathFinder("root", "color", "green"), root, color, color.getChildAt(1));
    test(parent, color, 4, createPathFinder("root", "color", "neerg")); // not found
    test(parent, color, 4, createPathFinder("root", "color", "blue"), root, color, color.getChildAt(2));
    test(parent, color, 4, createPathFinder("root", "color", "eulb")); // not found
    test(parent, color, 1, createPathFinder("root", "digit"));
    test(parent, color, 1, createPathFinder("root", "tigid")); // not found
    test(parent, color, 1, createPathFinder("root", "greek"));
    test(parent, color, 1, createPathFinder("root", "keerg")); // not found
  }

  @Test
  public void testDigitPathFinder() {
    TreeNode root = createRoot();
    TreeNode digit = root.getChildAt(1);
    TreePath parent = new TreePath(root);
    test(parent, digit, 1, createPathFinder("root"));
    test(parent, digit, 1, createPathFinder("toor")); // not found
    test(parent, digit, 1, createPathFinder("root", "color"));
    test(parent, digit, 1, createPathFinder("root", "roloc")); // not found
    test(parent, digit, 1, createPathFinder("root", "digit"), root, digit);
    test(parent, digit, 1, createPathFinder("root", "tigid")); // not found
    test(parent, digit, 2, createPathFinder("root", "digit", "one"), root, digit, digit.getChildAt(0));
    test(parent, digit, 10, createPathFinder("root", "digit", "eno")); // not found
    test(parent, digit, 3, createPathFinder("root", "digit", "two"), root, digit, digit.getChildAt(1));
    test(parent, digit, 10, createPathFinder("root", "digit", "owt")); // not found
    test(parent, digit, 4, createPathFinder("root", "digit", "three"), root, digit, digit.getChildAt(2));
    test(parent, digit, 10, createPathFinder("root", "digit", "eerht")); // not found
    test(parent, digit, 5, createPathFinder("root", "digit", "four"), root, digit, digit.getChildAt(3));
    test(parent, digit, 10, createPathFinder("root", "digit", "ruof")); // not found
    test(parent, digit, 6, createPathFinder("root", "digit", "five"), root, digit, digit.getChildAt(4));
    test(parent, digit, 10, createPathFinder("root", "digit", "evif")); // not found
    test(parent, digit, 7, createPathFinder("root", "digit", "six"), root, digit, digit.getChildAt(5));
    test(parent, digit, 10, createPathFinder("root", "digit", "xis")); // not found
    test(parent, digit, 8, createPathFinder("root", "digit", "seven"), root, digit, digit.getChildAt(6));
    test(parent, digit, 10, createPathFinder("root", "digit", "neves")); // not found
    test(parent, digit, 9, createPathFinder("root", "digit", "eight"), root, digit, digit.getChildAt(7));
    test(parent, digit, 10, createPathFinder("root", "digit", "thgie")); // not found
    test(parent, digit, 10, createPathFinder("root", "digit", "nine"), root, digit, digit.getChildAt(8));
    test(parent, digit, 10, createPathFinder("root", "digit", "enin")); // not found
    test(parent, digit, 1, createPathFinder("root", "greek"));
    test(parent, digit, 1, createPathFinder("root", "keerg")); // not found
  }

  @Test
  public void testGreekPathFinder() {
    TreeNode root = createRoot();
    TreeNode greek = root.getChildAt(2);
    TreePath parent = new TreePath(root);
    test(parent, greek, 1, createPathFinder("root"));
    test(parent, greek, 1, createPathFinder("toor")); // not found
    test(parent, greek, 1, createPathFinder("root", "color"));
    test(parent, greek, 1, createPathFinder("root", "roloc")); // not found
    test(parent, greek, 1, createPathFinder("root", "digit"));
    test(parent, greek, 1, createPathFinder("root", "tigid")); // not found
    test(parent, greek, 1, createPathFinder("root", "greek"), root, greek);
    test(parent, greek, 1, createPathFinder("root", "keerg")); // not found
    test(parent, greek, 2, createPathFinder("root", "greek", "alpha"), root, greek, greek.getChildAt(0));
    test(parent, greek, 6, createPathFinder("root", "greek", "ahpla")); // not found
    test(parent, greek, 3, createPathFinder("root", "greek", "beta"), root, greek, greek.getChildAt(1));
    test(parent, greek, 6, createPathFinder("root", "greek", "ateb")); // not found
    test(parent, greek, 4, createPathFinder("root", "greek", "gamma"), root, greek, greek.getChildAt(2));
    test(parent, greek, 6, createPathFinder("root", "greek", "ammag")); // not found
    test(parent, greek, 5, createPathFinder("root", "greek", "delta"), root, greek, greek.getChildAt(3));
    test(parent, greek, 6, createPathFinder("root", "greek", "atled")); // not found
    test(parent, greek, 6, createPathFinder("root", "greek", "epsilon"), root, greek, greek.getChildAt(4));
    test(parent, greek, 6, createPathFinder("root", "greek", "nolispe")); // not found
  }

  private static TreeVisitor createPathFinder(String... names) {
    return new TreeVisitor.PathFinder(new TreePath(names), Object::toString);
  }

  private static void test(TreeNode node, int count, @NotNull TreeVisitor visitor, Object... expected) {
    test(null, node, count, visitor, expected);
  }

  private static void test(TreePath parent, TreeNode node, int count, TreeVisitor visitor, Object... expected) {
    test(parent, node, count, false, new Walker(visitor), expected);
    test(parent, node, count, false, new Walker(visitor) {
      @Override
      protected Collection<TreeNode> getChildren(@NotNull TreeNode node) {
        setChildren(super.getChildren(node));
        return null;
      }
    }, expected);
  }

  private static void test(TreePath parent, TreeNode node, int count, boolean error, Walker walker, Object... expected) {
    walker.start(parent, node);
    switch (walker.promise().getState()) {
      case PENDING:
        throw new IllegalStateException("not processed");
      case FULFILLED:
        if (!error) break;
        throw new IllegalStateException("not rejected");
      case REJECTED:
        if (error) break;
        throw new IllegalStateException("not fulfilled");
    }
    TreeVisitor wrapper = getField(AbstractTreeWalker.class, walker, TreeVisitor.class, "visitor");
    assertEquals(Integer.valueOf(count), getField(Wrapper.class, wrapper, int.class, "count"));
    assertResult(walker, expected);
  }

  private static void assertResult(Walker walker, Object... expected) {
    assertResult(walker, expected.length == 0 ? null : new TreePath(expected));
  }

  private static void assertResult(Walker walker, TreePath expected) {
    assertEquals("unexpected result", expected, walker.promise().blockingGet(1));
  }


  @NotNull
  private static Node createRoot() {
    return new Node("root", createColorNode(), createDigitNode(), createGreekNode());
  }

  @NotNull
  private static Node createColorNode() {
    return new Node("color", "red", "green", "blue");
  }

  @NotNull
  private static Node createDigitNode() {
    return new Node("digit", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine");
  }

  @NotNull
  private static Node createGreekNode() {
    return new Node("greek", "alpha", "beta", "gamma", "delta", "epsilon");
  }


  private static class Node extends DefaultMutableTreeNode {
    private Node(Object content, Object... children) {
      super(content);
      for (Object child : children) {
        add(child instanceof MutableTreeNode
            ? (MutableTreeNode)child
            : new Node(child));
      }
    }
  }


  private static class Walker extends AbstractTreeWalker<TreeNode> {
    private Walker(TreeVisitor visitor) {
      super(new Wrapper(visitor));
    }

    @Override
    protected Collection<TreeNode> getChildren(@NotNull TreeNode node) {
      int count = node.getChildCount();
      ArrayList<TreeNode> list = new ArrayList<>(count);
      for (int i = 0; i < count; i++) list.add(node.getChildAt(i));
      return list;
    }
  }


  private static class Wrapper implements TreeVisitor {
    @SuppressWarnings("unused")
    private int count; // reflection
    private final TreeVisitor visitor;

    private Wrapper(TreeVisitor visitor) {
      this.visitor = visitor;
      if (PRINT) System.out.println("==============================");
    }

    @NotNull
    @Override
    public Action accept(@NotNull TreePath path) {
      count++;
      if (PRINT) System.out.println(path);
      return visitor.accept(path);
    }
  }
}
