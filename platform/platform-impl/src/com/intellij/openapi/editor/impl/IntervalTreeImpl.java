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

import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.util.Trinity;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * User: cdr
 */
public abstract class IntervalTreeImpl<T extends MutableInterval> extends RedBlackTree<T> implements IntervalTree<T> {
  protected final ReadWriteLock l = new ReentrantReadWriteLock();
  private final EqualStartIntervalComparator<T> comparator;

  public class MyNode extends DeltaNode<T> {
    public MyNode(T key) {
      super(key);
    }
    @Override
    public MyNode getLeft() {
      return (MyNode)super.getLeft();
    }

    @Override
    public MyNode getRight() {
      return (MyNode)super.getRight();
    }

    @Override
    public MyNode getParent() {
      return (MyNode)super.getParent();
    }

    @Override
    protected int computeDeltaUpToRoot() {
      if (normalized) return 0;
      return super.computeDeltaUpToRoot();
    }
  }

  private void pushDeltaFromRoot(MyNode node) {
    if (node != null) {
      pushDeltaFromRoot(node.getParent());
      pushDelta(node);
    }
  }

  protected static class DeltaNode<T extends MutableInterval> extends RedBlackTree.Node<T> {
    protected int maxEnd; // max of all intervalEnd()s among all children.
    protected int delta;  // delta of startOffset. getStartOffset() = myStartOffset + Sum of deltas up to root
    public DeltaNode(T key) {
      // maxEnd == 0 so to not disrupt existing maxes
      super(key);
    }

    protected int computeDeltaUpToRoot() {
      int delta = 0;
      DeltaNode<T> node = this;
      while (node != null) {
        if (key.isValid()) {
          delta += node.delta;
        }
        node = (DeltaNode<T>)node.getParent();
      }
      return delta;
    }
  }


  @Override
  protected MyNode createNewNode(T key) {
    return new MyNode(key);
  }

  @Override
  protected Node<T> lookupNode(T key, Node<T> root) {
    int delta = 0;
    int delta1 = ((RangeMarkerImpl)key).myNode == null ? 0 : ((RangeMarkerImpl)key).myNode.computeDeltaUpToRoot();
    while (root != null) {
      delta += ((MyNode)root).delta;
      int compResult = compare(key, delta1, root.key, delta);
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

  private int compare(T i1, int delta1, T i2, int delta2) {
    int start1 = i1.intervalStart() + delta1;
    int start2 = i2.intervalStart() + delta2;
    if (start1 != start2) return start1 - start2;
    int equalStartCompare = comparator.compare(i1, i2);
    return equalStartCompare;
  }

  protected interface EqualStartIntervalComparator<T extends MutableInterval> {
    int compare(T i1, T i2);
  }

  public IntervalTreeImpl(EqualStartIntervalComparator<T> comparator) {
    super(null);
    this.comparator = comparator;
  }

  protected MyNode getRoot() {
    return (MyNode)root;
  }

  public boolean process(@NotNull Processor<? super T> processor) {
    try {
      normalize();
      l.readLock().lock();
      checkMax(true);
      return process(getRoot(), processor, modCount);
    }
    finally {
      l.readLock().unlock();
    }
  }

  private boolean process(Node<T> root, Processor<? super T> processor, int modCountBefore) {
    if (root == null) return true;

    if (!process(root.getLeft(), processor, modCountBefore)) return false;
    if (!processor.process(root.key)) return false;
    assert modCount == modCountBefore;

    return process(root.getRight(), processor, modCountBefore);
  }

  public boolean processOverlappingWith(int start, int end, @NotNull Processor<? super T> processor) {
    try {
      normalize();
      l.readLock().lock();
      checkMax(true);
      return processOverlappingWith(getRoot(), start, end, processor, modCount, 0);
    }
    finally {
      l.readLock().unlock();
    }
  }

  private boolean processOverlappingWith(MyNode root, int start, int end, Processor<? super T> processor, int modCountBefore, int deltaUpToRootExclusive) {
    if (root == null) {
      return true;
    }
    assert root.key.isValid();

    int delta = deltaUpToRootExclusive + root.delta;
    if (start > maxEndOf(root, deltaUpToRootExclusive)) {
      return true; // right of the rightmost interval in the subtree
    }

    if (!processOverlappingWith(root.getLeft(), start, end, processor, modCountBefore, delta)) return false;
    int myStartOffset = root.key.intervalStart() + delta;
    int myEndOffset = root.key.intervalEnd() + delta;
    boolean overlaps = Math.max(myStartOffset, start) <= Math.min(myEndOffset, end);
    if (overlaps && !processor.process(root.key)) return false;
    assert modCount == modCountBefore;

    if (end < myStartOffset) {
      return true; // left of the root, cant be in the right subtree
    }

    return processOverlappingWith(root.getRight(), start, end, processor, modCountBefore, delta);
  }

  public boolean processOverlappingWith(int offset, @NotNull Processor<? super T> processor) {
    try {
      normalize();
      l.readLock().lock();
      checkMax(true);
      return processOverlapping(getRoot(), offset, processor, modCount, 0);
    }
    finally {
      l.readLock().unlock();
    }
  }

  private boolean processOverlapping(MyNode root, int offset, Processor<? super T> processor, int modCountBefore, int deltaUpToRootExclusive) {
    if (root == null) {
      return true;
    }
    assert root.key.isValid();
    int delta = deltaUpToRootExclusive + root.delta;
    if (offset > maxEndOf(root, deltaUpToRootExclusive)) {
      return true; // right of the rightmost interval in the subtree
    }

    if (!processOverlapping(root.getLeft(), offset, processor, modCountBefore, delta)) return false;
    int myStartOffset = root.key.intervalStart() + delta;
    int myEndOffset = root.key.intervalEnd() + delta;
    boolean overlaps = myStartOffset <= offset && offset < myEndOffset;

    if (overlaps && !processor.process(root.key)) return false;
    assert modCount == modCountBefore;

    if (offset < myStartOffset) {
      return true; // left of the root, cant be in the right subtree
    }

    return processOverlapping(root.getRight(), offset, processor, modCountBefore, delta);
  }

  private void insert(MyNode newNode) {
    T key = newNode.key;
    if (root == null) {
      root = newNode;
    }
    else {
      MyNode n = (MyNode)root;
      int delta = 0;
      loop:
      while (true) {
        assert n.key.isValid();
        delta += n.delta;
        int compResult = compare(key, 0, n.key, delta);
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
          T i1 = key;
          T i2 = n.key;
          int delta1 = 0;
          int delta2 = delta;
          assert false : "already inserted: " + i1 + ":" + delta1 + "; "+ ((RangeMarkerEx)i1).getId() +" <-> " + i2 + ":" + delta2 + "; "+ ((RangeMarkerEx)i2).getId()+ " iden=" + (i1 == i2);
          return;
        }
      }
      newNode.delta = -delta;
      newNode.setParent(n);
    }
    size++;
    correctMaxUp(newNode);
    insertCase1(newNode);
    verifyProperties();
  }

  public MyNode add(@NotNull T interval) {
    try {
      l.writeLock().lock();
      checkMax(true);

      modCount++;
      MyNode newNode = createNewNode(interval);
      insert(newNode);

      checkMax(false); // myNode still not assigned
      return newNode;
    }
    finally {
      l.writeLock().unlock();
    }
  }

  public void checkMax(boolean assertInvalid) {
    if (!VERIFY) return;
    checkMax(getRoot(), 0, assertInvalid);
  }

  // returns real (minStart, maxStart, maxEnd)
  protected Trinity<Integer,Integer,Integer> checkMax(MyNode root, int deltaUpToRootExclusive, boolean assertInvalid) {
    if (root == null) return Trinity.create(Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE);
    int delta = deltaUpToRootExclusive + (root.key.isValid() ? root.delta : 0);
    Trinity<Integer, Integer, Integer> l = checkMax(root.getLeft(), delta, assertInvalid);
    int minLeftStart = l.first;
    int maxLeftStart = l.second;
    int maxLeftEnd = l.third;
    Trinity<Integer, Integer, Integer> r = checkMax(root.getRight(), delta, assertInvalid);
    int maxRightEnd = r.third;
    int minRightStart = r.first;
    int maxRightStart = r.second;
    if (!root.key.isValid()) {
      if (assertInvalid) assert false : root.key;
      return Trinity.create(Math.min(minLeftStart, minRightStart), Math.max(maxLeftStart, maxRightStart), Math.max(maxRightEnd, maxLeftEnd));
    }
    MyNode parent = root.getParent();
    if (parent != null && assertInvalid) {
      int c = compare(root.key, delta, parent.key, delta - root.delta);
      assert c != 0;
      assert c < 0 && parent.getLeft() == root || c > 0 && parent.getRight() == root;
    }
    assert delta + root.maxEnd == Math.max(maxLeftEnd, Math.max(maxRightEnd, delta + root.key.intervalEnd()));
    int myStartOffset = delta + root.key.intervalStart();
    assert maxLeftStart <= myStartOffset;
    assert minRightStart >= myStartOffset;
    assert myStartOffset >= 0;
    assert minLeftStart == Integer.MAX_VALUE || minLeftStart <= myStartOffset;
    assert maxRightStart == Integer.MIN_VALUE || maxRightStart >= myStartOffset;
    int minStart = Math.min(minLeftStart, myStartOffset);
    int maxStart = Math.max(myStartOffset, Math.max(maxLeftStart, maxRightStart));
    assert minStart <= maxStart;
    return Trinity.create(minStart, maxStart, root.maxEnd + delta);
  }

  @Override
  protected Node<T> maximumNode(Node<T> n) {
    MyNode root = (MyNode)n;
    pushDelta(root.getParent());
    pushDelta(root);
    while (root.getRight() != null) {
      root = root.getRight();
      pushDelta(root);
    }
    return root;
  }

  public boolean remove(@NotNull T interval) {
    try {
      l.writeLock().lock();
      checkMax(true);

      MyNode node = (MyNode)delete(interval);
      if (node == null) return false;
      MyNode parent = node.getParent();
      correctMaxUp(parent);
      checkMax(true);
      return true;
    }
    finally {
      l.writeLock().unlock();
    }
  }

  @Override
  protected void deleteNode(Node<T> n) {
    pushDeltaFromRoot((MyNode)n);
    super.deleteNode(n);
  }

  protected void pushDelta(MyNode root) {
    if (root == null || !root.key.isValid()) return;
    int delta = root.delta;
    if (delta != 0) {
      root.key.setIntervalStart(root.key.intervalStart() + delta);
      root.key.setIntervalEnd(root.key.intervalEnd() + delta);
      root.maxEnd += delta;
      root.delta = 0;
      incDelta(root.getLeft(), delta);
      incDelta(root.getRight(), delta);
    }
  }
  private void incDelta(MyNode root, int delta) {
    if (root == null) return;
    if (root.key.isValid()) {
      root.delta += delta;
    }
    else {
      incDelta(root.getLeft(), delta);
      incDelta(root.getRight(), delta);
    }
  }

  @Override
  protected MyNode swapWithMaxPred(Node<T> root, Node<T> maxPred) {
    checkMax(false);
    MyNode a = (MyNode)root;
    MyNode d = (MyNode)maxPred;
    Color acolor = a.color;
    Color dcolor = d.color;
    assert !a.key.isValid() || a.delta == 0 : a.delta;
    for (MyNode n = a.getLeft(); n != null; n = n.getRight()) {
      assert !n.key.isValid() || n.delta == 0 : n.delta;
    }
    swapNodes(a, d);

    // set range of the key to be deleted so it wont disrupt maxes
    a.key.setValid(false);
    //a.key.setIntervalStart(d.key.intervalStart());
    //a.key.setIntervalEnd(d.key.intervalEnd());

    //correctMaxUp(a);
    a.color = dcolor;
    d.color = acolor;
    correctMaxUp(a);

    checkMax(false);
    return a;
  }

  private void swapNodes(MyNode n1, MyNode n2) {
    MyNode l1 = n1.getLeft();
    MyNode r1 = n1.getRight();
    MyNode p1 = n1.getParent();
    MyNode l2 = n2.getLeft();
    MyNode r2 = n2.getRight();
    MyNode p2 = n2.getParent();

    if (p1 != null) {
      if (p1.getLeft() == n1) p1.setLeft(n2); else p1.setRight(n2);
    }
    else {
      root = n2;
    }
    if (p2 != null) {
      if (p2.getLeft() == n2) p2.setLeft(p2 == n1 ? l2 : n1); else p2.setRight(p2 == n1 ? r2 : n1);
    }
    else {
      root = n1;
    }
    n1.setParent(p2 == n1 ? n2 : p2);
    n2.setParent(p1);

    n1.setLeft(l2);
    n2.setLeft(l1 == n2 ? n1 : l1);
    if (l1 != null) l1.setParent(n2 == l1 ? p1 : n2);
    if (r1 != null) r1.setParent(n2);
    n1.setRight(r2);
    n2.setRight(r1);
    if (l2 != null) l2.setParent(n1);
    if (r2 != null) r2.setParent(n1);
  }

  protected int maxEndOf(MyNode node, int deltaUpToRootExclusive) {
    if (node == null) {
      return 0;
    }
    if (node.key.isValid()) {
      return node.maxEnd + node.delta + deltaUpToRootExclusive;
    }
    return Math.max(maxEndOf(node.getLeft(), deltaUpToRootExclusive), maxEndOf(node.getRight(), deltaUpToRootExclusive));
  }

  // max of n.left's maxend, n.right's maxend and its own interval endOffset
  protected void correctMax(@NotNull MyNode node, int deltaUpToRoot) {
    if (!node.key.isValid()) return;
    int realMax = Math.max(Math.max(maxEndOf(node.getLeft(), deltaUpToRoot), maxEndOf(node.getRight(), deltaUpToRoot)),
                           deltaUpToRoot + node.key.intervalEnd());
    node.maxEnd = realMax - deltaUpToRoot;
  }

  private void correctMaxUp(MyNode node) {
    int delta = node == null ? 0 : node.computeDeltaUpToRoot();
    while (node != null) {
      if (node.key.isValid()) {
        int d = node.delta;
        correctMax(node, delta);
        delta -= d;
      }
      node = node.getParent();
    }
    assert delta == 0 : delta;
  }

  @Override
  protected void rotateRight(Node<T> n) {
    checkMax(false);
    MyNode node1 = (MyNode)n;
    MyNode node2 = node1.getLeft();
    MyNode node3 = node1.getRight();

    MyNode parent = node1.getParent();
    int deltaUp = parent == null ? 0 : parent.computeDeltaUpToRoot();
    pushDelta(node1);
    pushDelta(node2);
    pushDelta(node3);

    super.rotateRight(node1);

    if (node3 != null) {
      correctMax(node3, deltaUp);
    }
    correctMax(node1, deltaUp);
    correctMax(node2, deltaUp);
    checkMax(false);
  }

  @Override
  protected void rotateLeft(Node<T> n) {
    checkMax(false);
    MyNode node1 = (MyNode)n;
    MyNode node2 = node1.getLeft();
    MyNode node3 = node1.getRight();

    MyNode parent = node1.getParent();
    int deltaUp = parent == null ? 0 : parent.computeDeltaUpToRoot();
    pushDelta(node1);
    pushDelta(node2);
    pushDelta(node3);
    checkMax(false);
    super.rotateLeft(node1);

    if (node2 != null) {
      correctMax(node2, deltaUp);
    }
    correctMax(node1, deltaUp);
    correctMax(node3, deltaUp);
    checkMax(false);
  }

  @Override
  protected void replaceNode(@NotNull Node<T> node, Node<T> child) {
    super.replaceNode(node, child);
    if (child != null && node.key.isValid()) {
      ((MyNode)child).delta += ((MyNode)node).delta;
      //todo correct max up to root??
    }
  }

  public Iterator<T> iterator() {
    MyNode firstNode = getRoot();
    while (firstNode != null && firstNode.getLeft() != null) {
      firstNode = firstNode.getLeft();
    }
    return createIteratorFrom(firstNode);
  }

  private MyNode findMinOverlappingWith(MyNode root, Interval interval, int modCountBefore, int deltaUpToRootExclusive) {
    if (root == null) {
      return null;
    }
    assert root.key.isValid();

    int delta = deltaUpToRootExclusive + root.delta;
    if (interval.intervalStart() > maxEndOf(root, deltaUpToRootExclusive)) {
      return null; // right of the rightmost interval in the subtree
    }

    MyNode inLeft = findMinOverlappingWith(root.getLeft(), interval, modCountBefore, delta);
    if (inLeft != null) return inLeft;
    int myStartOffset = root.key.intervalStart() + delta;
    int myEndOffset = root.key.intervalEnd() + delta;
    boolean overlaps = Math.max(myStartOffset, interval.intervalStart()) <= Math.min(myEndOffset, interval.intervalEnd());
    if (overlaps) return root;
    assert modCount == modCountBefore;

    if (interval.intervalEnd() < myStartOffset) {
      return null; // left of the root, cant be in the right subtree
    }

    return findMinOverlappingWith(root.getRight(), interval, modCountBefore, delta);
  }

  @NotNull
  Iterator<T> iteratorFrom(@NotNull Interval interval) {
    MyNode firstOverlap = findMinOverlappingWith(getRoot(), interval, modCount, 0);
    if (firstOverlap == null) {
      return ContainerUtil.emptyIterator();
    }
    return createIteratorFrom(firstOverlap);
  }

  private Iterator<T> createIteratorFrom(final MyNode firstNode) {
    checkMax(true);
    normalize();

    final int modCountBefore = modCount;
    return new Iterator<T>() {
      Node<T> element = firstNode;

      public boolean hasNext() {
        return element != null;
      }

      public T next() {
        assert modCount == modCountBefore;
        if (element == null) throw new NoSuchElementException();
        Node<T> prev = element;
        element = getNext(element);
        return prev.key;
      }

      private Node<T> getNext(Node<T> element) {
        Node<T> n = element.getRight();
        if (n != null) {
          while (n.getLeft()!= null) {
            n = n.getLeft();
          }
          return n;
        }
        Node<T> parent = element.getParent();
        while (parent != null) {
          if (parent.getLeft() == element) return parent;
          element = parent;
          parent = parent.getParent();
        }
        return parent;
      }

      public void remove() {
        throw new IncorrectOperationException();
      }
    };
  }

  protected volatile boolean normalized = true;

  public void normalize() {
    if (normalized) return;
    try {
      l.writeLock().lock();
      if (normalized) return;
      normalize(getRoot());
      normalized = true;
    }
    finally {
      l.writeLock().unlock();
    }
  }

  private void normalize(MyNode root) {
    if (root == null) return;
    pushDelta(root);
    normalize(root.getLeft());
    normalize(root.getRight());
  }

  private void printSorted() { printSorted(getRoot());}
  private void printSorted(MyNode root) {
    if (root == null) return;
    printSorted(root.getLeft());
    System.out.println(root.key);
    printSorted(root.getRight());
  }

}
