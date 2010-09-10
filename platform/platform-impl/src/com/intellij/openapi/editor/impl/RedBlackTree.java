/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import org.jetbrains.annotations.NotNull;

import java.util.Comparator;


/**
 * User: cdr
 */
public class RedBlackTree<K> {
  public static final boolean VERIFY = false;
  private static final int INDENT_STEP = 4;
  protected int size;
  protected int modCount;
  public Node<K> root;

  private final Comparator<K> comparator;

  public RedBlackTree(Comparator<K> comparator) {
    this.comparator = comparator;
    root = null;
    verifyProperties();
  }

  protected Node<K> lookupNode(K key, Node<K> root) {
    while (root != null) {
      int compResult = comparator.compare(key, root.key);
      if (compResult < 0) {
        root = root.getLeft();
      }
      else if (compResult > 0) {
        root = root.getRight();
      }
      else {
        return root;
      }
    }
    return root;
  }

  protected void rotateLeft(Node<K> n) {
    Node<K> r = n.getRight();
    replaceNode(n, r);
    n.setRight(r.getLeft());
    if (r.getLeft() != null) {
      r.getLeft().setParent(n);
    }
    r.setLeft(n);
    n.setParent(r);
  }

  protected void rotateRight(Node<K> n) {
    Node<K> l = n.getLeft();
    replaceNode(n, l);
    n.setLeft(l.getRight());
    if (l.getRight() != null) {
      l.getRight().setParent(n);
    }
    l.setRight(n);
    n.setParent(l);
  }

  protected void replaceNode(@NotNull Node<K> oldn, Node<K> newn) {
    Node<K> parent = oldn.getParent();
    if (parent == null) {
      root = newn;
    }
    else {
      if (oldn == parent.getLeft()) {
        parent.setLeft(newn);
      }
      else {
        parent.setRight(newn);
      }
    }
    if (newn != null) {
      newn.setParent(parent);
    }
    //oldn.parent = null;
    //oldn.left = null;
    //oldn.right = null;
  }

  protected Node<K> createNewNode(K key) {
    return new Node<K>(key);
  }

  public Node<K> insert(Node<K> newNode) {
    modCount++;
    newNode.color = Color.RED;
    K key = newNode.key;

    if (root == null) {
      root = newNode;
    }
    else {
      Node<K> n = root;
      loop:
      while (true) {
        int compResult = comparator.compare(key, n.key);
        if (compResult < 0) {
          if (n.getLeft() == null) {
            n.setLeft(newNode);
            break loop;
          }
          n = n.getLeft();
        }
        else if (compResult > 0) {
          if (n.getRight() == null) {
            n.setRight(newNode);
            break loop;
          }
          n = n.getRight();
        }
        else {
          assert false : "already inserted";
          return root;
        }
      }
      newNode.setParent(n);
    }
    size++;
    insertCase1(newNode);
    verifyProperties();
    return newNode;
  }

  protected void insertCase1(Node<K> n) {
    if (n.getParent() == null) {
      n.color = Color.BLACK;
    }
    else {
      insertCase2(n);
    }
  }

  private void insertCase2(Node<K> n) {
    if (nodeColor(n.getParent()) != Color.BLACK) {
      insertCase3(n);
    }
    // Tree is still valid
  }

  void insertCase3(Node<K> n) {
    if (nodeColor(n.uncle()) == Color.RED) {
      n.getParent().color = Color.BLACK;
      n.uncle().color = Color.BLACK;
      n.grandparent().color = Color.RED;
      insertCase1(n.grandparent());
    }
    else {
      insertCase4(n);
    }
  }

  void insertCase4(Node<K> n) {
    if (n == n.getParent().getRight() && n.getParent() == n.grandparent().getLeft()) {
      rotateLeft(n.getParent());
      n = n.getLeft();
    }
    else if (n == n.getParent().getLeft() && n.getParent() == n.grandparent().getRight()) {
      rotateRight(n.getParent());
      n = n.getRight();
    }
    insertCase5(n);
  }

  void insertCase5(Node<K> n) {
    n.getParent().color = Color.BLACK;
    n.grandparent().color = Color.RED;
    if (n == n.getParent().getLeft() && n.getParent() == n.grandparent().getLeft()) {
      rotateRight(n.grandparent());
    }
    else {
      assert n == n.getParent().getRight();
      assert n.getParent() == n.grandparent().getRight();
      rotateLeft(n.grandparent());
    }
  }

  private static <K> void assertParentChild(Node<K> node1) {
    assert node1 == null || node1.getParent() == null || node1.getParent().getLeft() == node1 || node1.getParent().getRight() == node1;
  }

  //returns parent of deleted node
  public Node<K> delete(K key) {
    modCount++;
    Node<K> n = lookupNode(key, root);
    deleteNode(n);
    return n;
  }

  protected void deleteNode(Node<K> n) {
    if (n == null) return;  // Key not found, do nothing
    if (n.getLeft() != null && n.getRight() != null) {
      // Copy key/value from predecessor and then delete it instead
      Node<K> pred = maximumNode(n.getLeft());

      //Color c = pred.color;
      //swap(n, pred);
      //assert pred.color == c;
      //pred.color = n.color;
      //n.color = c;

      n = swapWithMaxPred(n, pred);
    }

    assert n.getLeft() == null || n.getRight() == null;
    Node<K> child = n.getRight() == null ? n.getLeft() : n.getRight();
    if (nodeColor(n) == Color.BLACK) {
      n.color = nodeColor(child);
      deleteCase1(n);
    }
    replaceNode(n, child);

    if (nodeColor(root) == Color.RED) {
      root.color = Color.BLACK;
    }
    size--;
    verifyProperties();
  }

  protected Node<K> swapWithMaxPred(Node<K> nowAscendant, Node<K> nowDescendant) {
    nowAscendant.key = nowDescendant.key;
    //nowAscendant.value = nowDescendant.value;
    return nowDescendant;
  }

  protected Node<K> maximumNode(Node<K> n) {
    assert n != null;
    while (n.getRight() != null) {
      n = n.getRight();
    }
    return n;
  }

  private void deleteCase1(Node<K> n) {
    if (n.getParent() != null) {
      deleteCase2(n);
    }
  }

  private void deleteCase2(Node<K> n) {
    if (nodeColor(n.sibling()) == Color.RED) {
      n.getParent().color = Color.RED;
      n.sibling().color = Color.BLACK;
      if (n == n.getParent().getLeft()) {
        rotateLeft(n.getParent());
      }
      else {
        rotateRight(n.getParent());
      }
    }
    deleteCase3(n);
  }

  private void deleteCase3(Node<K> n) {
    if (nodeColor(n.getParent()) == Color.BLACK &&
        nodeColor(n.sibling()) == Color.BLACK &&
        nodeColor(n.sibling().getLeft()) == Color.BLACK &&
        nodeColor(n.sibling().getRight()) == Color.BLACK) {
      n.sibling().color = Color.RED;
      deleteCase1(n.getParent());
    }
    else {
      deleteCase4(n);
    }
  }

  private void deleteCase4(Node<K> n) {
    if (nodeColor(n.getParent()) == Color.RED &&
        nodeColor(n.sibling()) == Color.BLACK &&
        nodeColor(n.sibling().getLeft()) == Color.BLACK &&
        nodeColor(n.sibling().getRight()) == Color.BLACK) {
      n.sibling().color = Color.RED;
      n.getParent().color = Color.BLACK;
    }
    else {
      deleteCase5(n);
    }
  }

  private void deleteCase5(Node<K> n) {
    if (n == n.getParent().getLeft() &&
        nodeColor(n.sibling()) == Color.BLACK &&
        nodeColor(n.sibling().getLeft()) == Color.RED &&
        nodeColor(n.sibling().getRight()) == Color.BLACK) {
      n.sibling().color = Color.RED;
      n.sibling().getLeft().color = Color.BLACK;
      rotateRight(n.sibling());
    }
    else if (n == n.getParent().getRight() &&
             nodeColor(n.sibling()) == Color.BLACK &&
             nodeColor(n.sibling().getRight()) == Color.RED &&
             nodeColor(n.sibling().getLeft()) == Color.BLACK) {
      n.sibling().color = Color.RED;
      n.sibling().getRight().color = Color.BLACK;
      rotateLeft(n.sibling());
    }
    deleteCase6(n);
  }

  private void deleteCase6(Node<K> n) {
    n.sibling().color = nodeColor(n.getParent());
    n.getParent().color = Color.BLACK;
    if (n == n.getParent().getLeft()) {
      assert nodeColor(n.sibling().getRight()) == Color.RED;
      n.sibling().getRight().color = Color.BLACK;
      rotateLeft(n.getParent());
    }
    else {
      assert nodeColor(n.sibling().getLeft()) == Color.RED;
      n.sibling().getLeft().color = Color.BLACK;
      rotateRight(n.getParent());
    }
  }

  public void print() {
    printHelper(root, 0);
  }

  private static void printHelper(Node<?> n, int indent) {
    if (n == null) {
      System.err.print("<empty tree>");
      return;
    }
    if (n.getRight() != null) {
      printHelper(n.getRight(), indent + INDENT_STEP);
    }
    for (int i = 0; i < indent; i++) {
      System.err.print(" ");
    }
    if (n.color == Color.BLACK) {
      System.err.println(n.key);
    }
    else {
      System.err.println("<" + n.key + ">");
    }
    if (n.getLeft() != null) {
      printHelper(n.getLeft(), indent + INDENT_STEP);
    }
  }

  /*
  private static void test (int... keys) {
    testDel(keys,keys);
  }
  private static void testDel (int[] adds, int[] dels) {
    RedBlackTree<Integer> t = new RedBlackTree<Integer>();
    for (int i = 0; i < adds.length; i++) {
      int key = adds[i];
      Node<Integer> node = t.createNewNode(key);
      node.value = i;
      t.insert(node);
    }
    for (int key : dels) {
      t.delete(key);
    }
  }

  public static void main(String[] args) {
    test(0, 2, 1, 2);
    test(4, 2, 0, 9, 6, 1, 3, 7);
    testDel(new int[] {7, 2, 5, 0, 4}, new int[]{2,7});




    //t.insert(0, 0);
    //t.insert(2, 1); //assert t.lookup(1).equals(1) : t.lookup(1);
    //t.insert(1, 0); //assert t.lookup(0).equals(0) : t.lookup(0);
    //t.insert(2, 1); //assert t.lookup(1).equals(1) : t.lookup(1);

    Random gen = new Random();

    while (true) {
      RedBlackTree<Integer> t = new RedBlackTree<Integer>();
      List<Integer> adds = new ArrayList<Integer>();
      List<Integer> dels = new ArrayList<Integer>();
      assert t.size() == 0 : t.size();
      try {
        for (int i = 0; i < 500; i++) {
          int x = gen.nextInt(500);
          if (adds.contains(x)) continue;
          adds.add(x);
          Node<Integer> node = t.createNewNode(x);
          node.value = x;
          t.insert(node);
          Integer lookup = t.lookupNode(x).value;
          assert lookup.equals(i) : lookup;
        }
        List<Integer> candidates = new ArrayList<Integer>(adds);
        for (int i = 0; i < candidates.size(); i++) {
          int x = gen.nextInt(candidates.size());
          Integer r = candidates.get(x);
          candidates.remove((Object)r);
          dels.add(r);
          t.delete(r);
        }
      }
      catch (AssertionError e) {
        System.err.println("adds = " + adds);
        System.err.println("dels = " + dels);
        throw e;
      }
    }
  }
  */

  public static class Node<K> {
    K key;
    private Node<K> left;
    private Node<K> right;
    private Node<K> parent;
    protected Color color;

    public Node(K key) {
      this.key = key;
      color = Color.RED;
      setParent(null);
    }

    public Node<K> grandparent() {
      assert getParent() != null; // Not the root node
      assert getParent().getParent() != null; // Not child of root
      return getParent().getParent();
    }

    public Node<K> sibling() {
      Node<K> parent = getParent();
      assert parent != null; // Root node has no sibling
      return this == parent.getLeft() ? parent.getRight() : parent.getLeft();
    }

    public Node<K> uncle() {
      assert getParent() != null; // Root node has no uncle
      assert getParent().getParent() != null; // Children of root have no uncle
      return getParent().sibling();
    }

    public Node<K> getLeft() {
      return left;
    }

    public void setLeft(Node<K> left) {
      this.left = left;
    }

    public Node<K> getRight() {
      return right;
    }

    public void setRight(Node<K> right) {
      this.right = right;
    }

    public Node<K> getParent() {
      return parent;
    }

    public void setParent(Node<K> parent) {
      this.parent = parent;
    }
  }

  protected static enum Color {
    RED, BLACK
  }

  public int size() {
    return size;
  }

  public void verifyProperties() {
    //if (true) return;
    if (VERIFY) {
      verifyProperty1(root);
      verifyProperty2(root);
      // Property 3 is implicit
      verifyProperty4(root);
      verifyProperty5(root);
    }
  }

  private static void verifyProperty1(Node<?> n) {
    assert nodeColor(n) == Color.RED || nodeColor(n) == Color.BLACK;
    if (n == null) return;
    assert n.getParent() != n;
    assert n.getLeft() != n;
    assert n.getRight() != n;
    assertParentChild(n);

    verifyProperty1(n.getLeft());
    verifyProperty1(n.getRight());
  }

  private static void verifyProperty2(Node<?> root) {
    assert nodeColor(root) == Color.BLACK;
  }

  private static Color nodeColor(Node<?> n) {
    return n == null ? Color.BLACK : n.color;
  }

  private static void verifyProperty4(Node<?> n) {
    if (nodeColor(n) == Color.RED) {
      assert nodeColor(n.getLeft()) == Color.BLACK;
      assert nodeColor(n.getRight()) == Color.BLACK;
      assert nodeColor(n.getParent()) == Color.BLACK;
    }
    if (n == null) return;
    verifyProperty4(n.getLeft());
    verifyProperty4(n.getRight());
  }

  private static void verifyProperty5(Node<?> root) {
    verifyProperty5Helper(root, 0, -1);
  }

  private static int verifyProperty5Helper(Node<?> n, int blackCount, int pathBlackCount) {
    if (nodeColor(n) == Color.BLACK) {
      blackCount++;
    }
    if (n == null) {
      if (pathBlackCount == -1) {
        pathBlackCount = blackCount;
      }
      else {
        assert blackCount == pathBlackCount;
      }
      return pathBlackCount;
    }
    pathBlackCount = verifyProperty5Helper(n.getLeft(), blackCount, pathBlackCount);
    pathBlackCount = verifyProperty5Helper(n.getRight(), blackCount, pathBlackCount);

    return pathBlackCount;
  }

  public void clear() {
    modCount++;
    root = null;
    size = 0;
  }
}
