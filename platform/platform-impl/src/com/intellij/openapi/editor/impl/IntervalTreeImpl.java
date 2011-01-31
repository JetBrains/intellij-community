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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Trinity;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * User: cdr
 */
public abstract class IntervalTreeImpl<T extends MutableInterval> extends RedBlackTree<T> implements IntervalTree<T> {
  protected final ReadWriteLock l = new ReentrantReadWriteLock();

  protected abstract EqualStartIntervalComparator<IntervalNode> getComparator();
  private final ReferenceQueue<T> myReferenceQueue = new ReferenceQueue<T>();
  private int deadReferenceCount;

  public abstract static class IntervalNode extends RedBlackTree.Node implements MutableInterval {
    protected int maxEnd; // max of all intervalEnd()s among all children.
    protected int delta;  // delta of startOffset. getStartOffset() = myStartOffset + Sum of deltas up to root
    protected abstract int computeDeltaUpToRoot();
    @Override
    public IntervalNode getLeft() {
      return (IntervalNode)super.getLeft();
    }
    @Override
    public IntervalNode getRight() {
      return (IntervalNode)super.getRight();
    }

    @Override
    public IntervalNode getParent() {
      return (IntervalNode)super.getParent();
    }

  }
  protected class MyNode extends IntervalNode {

    private volatile int myStart;
    private volatile int myEnd;
    private volatile boolean isValid = true;
    private final Reference<T> interval;
    public MyNode(@NotNull T key, int start, int end) {
      // maxEnd == 0 so to not disrupt existing maxes
      interval = new WeakReference<T>(key,myReferenceQueue);
      myStart = start;
      myEnd = end;
    }
    @Override
    public T getKey() {
      return interval.get();
    }

    @Override
    protected int computeDeltaUpToRoot() {
      if (normalized) return 0;
      int delta = 0;
      IntervalNode node = this;
      while (node != null) {
        if (node.isValid()) {
          delta += node.delta;
        }
        node = node.getParent();
      }
      return delta;
    }

    @Override
    public int setIntervalStart(int start) {
      return myStart = start;
    }

    @Override
    public int setIntervalEnd(int end) {
      return myEnd = end;
    }

    @Override
    public boolean isValid() {
      return isValid;
    }

    @Override
    public boolean setValid(boolean value) {
      return isValid = value;
    }

    @Override
    public int intervalStart() {
      return myStart;
    }

    @Override
    public int intervalEnd() {
      return myEnd;
    }

    public IntervalTreeImpl getTree() {
      return IntervalTreeImpl.this;
    }

  }
  private void pushDeltaFromRoot(IntervalNode node) {
    if (normalized) return;
    if (node != null) {
      pushDeltaFromRoot(node.getParent());
      pushDelta(node);
    }
  }

  @Override
  protected MyNode createNewNode(T key, int start, int end, Object data) {
    return new MyNode(key, start, end);
  }

  @Override
  protected IntervalNode lookupNode(@NotNull T key, Node<T> interval) {
    return ((RangeMarkerImpl)key).myNode;
    //int delta = 0;
    //int delta1 = ((RangeMarkerImpl)key).myNode == null ? 0 : ((RangeMarkerImpl)key).myNode.computeDeltaUpToRoot();
    //List<IntervalNode> invalid = new ArrayList<IntervalNode>();
    //IntervalNode root = (IntervalNode)interval;
    //while (root != null) {
    //  delta += root.delta;
    //  int compResult = compareNodes(((RangeMarkerImpl)key).myNode, delta1, root, delta, invalid);
    //  if (compResult < 0) {
    //    root = root.getLeft();
    //  }
    //  else if (compResult > 0) {
    //    root = root.getRight();
    //  }
    //  else {
    //    return root;
    //  }
    //}
    //deleteNodes(invalid);
    //return root;
  }

  private int compareNodes(@NotNull IntervalNode i1, int delta1, @NotNull IntervalNode i2, int delta2, List<IntervalNode> invalid) {
    T i2Key = (T)i2.getKey();
    if (i2Key == null) {
      invalid.add(i2); //gced
    }
    int start1 = i1.intervalStart() + delta1;
    int start2 = i2.intervalStart() + delta2;
    if (start1 != start2) return start1 - start2;
    //if (i2Key == null) return 1; // by default insert to the right to the gced node
    int equalStartCompare = getComparator().compare(i1, i2);
    return equalStartCompare;
  }

  protected interface EqualStartIntervalComparator<T extends MutableInterval> {

    int compare(T i1, T i2);
  }
  protected IntervalTreeImpl.IntervalNode getRoot() {
    return (IntervalNode)root;
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

  private boolean process(IntervalNode root, Processor<? super T> processor, int modCountBefore) {
    if (root == null) return true;

    if (!process(root.getLeft(), processor, modCountBefore)) return false;
    T key = (T)root.getKey();
    if (key != null) {
      if (!processor.process(key)) return false;
      assert modCount == modCountBefore;
    }

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

  private boolean processOverlappingWith(IntervalNode root,
                                         int start,
                                         int end,
                                         Processor<? super T> processor,
                                         int modCountBefore,
                                         int deltaUpToRootExclusive) {
    if (root == null) {
      return true;
    }
    assert root.isValid();

    int delta = deltaUpToRootExclusive + root.delta;
    if (start > maxEndOf(root, deltaUpToRootExclusive)) {
      return true; // right of the rightmost interval in the subtree
    }

    if (!processOverlappingWith(root.getLeft(), start, end, processor, modCountBefore, delta)) return false;
    int myStartOffset = root.intervalStart() + delta;
    int myEndOffset = root.intervalEnd() + delta;
    boolean overlaps = Math.max(myStartOffset, start) <= Math.min(myEndOffset, end);
    T key = (T)root.getKey();
    if (key != null) {
      if (overlaps && !processor.process(key)) return false;
      assert modCount == modCountBefore;
    }

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

  private boolean processOverlapping(IntervalNode root,
                                     int offset,
                                     Processor<? super T> processor,
                                     int modCountBefore,
                                     int deltaUpToRootExclusive) {
    if (root == null) {
      return true;
    }
    assert root.isValid();
    int delta = deltaUpToRootExclusive + root.delta;
    if (offset > maxEndOf(root, deltaUpToRootExclusive)) {
      return true; // right of the rightmost interval in the subtree
    }

    if (!processOverlapping(root.getLeft(), offset, processor, modCountBefore, delta)) return false;
    int myStartOffset = root.intervalStart() + delta;
    int myEndOffset = root.intervalEnd() + delta;
    boolean overlaps = myStartOffset <= offset && offset < myEndOffset;

    T key = (T)root.getKey();
    if (key != null) {
      if (overlaps && !processor.process(key)) return false;
      assert modCount == modCountBefore;
    }

    if (offset < myStartOffset) {
      return true; // left of the root, cant be in the right subtree
    }

    return processOverlapping(root.getRight(), offset, processor, modCountBefore, delta);
  }

  protected void insert(@NotNull IntervalNode node) {
    node.color = Color.RED;
    node.setParent(null);
    node.setValid(true);
    node.maxEnd = 0;
    node.delta = 0;
    node.setLeft(null);
    node.setRight(null);

    List<IntervalNode> gced = new ArrayList<IntervalNode>();
    //T nodeKey = (T)node.getKey();
    //assert nodeKey != null;
    if (root == null) {
      root = node;
    }
    else {
      IntervalNode current = (IntervalNode)root;
      int delta = 0;
      loop:
      while (true) {
        delta += current.delta;
        int compResult = compareNodes(node, 0, current, delta, gced);
        if (compResult < 0) {
          if (current.getLeft() == null) {
            current.setLeft(node);
            break loop;
          }
          current = current.getLeft();
        }
        else if (compResult > 0) {
          if (current.getRight() == null) {
            current.setRight(node);
            break loop;
          }
          current = current.getRight();
        }
        else {
          T i1 = (T)node.getKey();
          T i2 = (T)current.getKey();
          int delta1 = 0;
          int delta2 = delta;
          assert false : "already inserted: " +
                         i1 +
                         ":" +
                         delta1 +
                         "; " +
                         ((RangeMarkerEx)i1).getId() +
                         " <-> " +
                         i2 +
                         ":" +
                         delta2 +
                         "; " +
                         ((RangeMarkerEx)i2).getId() +
                         " iden=" +
                         (i1 == i2);
          return;
        }
      }
      node.delta = -delta;
      node.setParent(current);
    }
    size++;
    correctMaxUp(node);
    insertCase1(node);
    verifyProperties();

    deleteNodes(gced);
  }

  private void deleteNodes(List<IntervalNode> collectedAway) {
    if (collectedAway.isEmpty()) return;
    try {
      l.writeLock().lock();
      checkMax(true);
      for (IntervalNode node : collectedAway) {
        removeNode(node);
      }
      checkMax(true);
    }
    finally {
      l.writeLock().unlock();
    }
  }

  @Override
  public IntervalNode addInterval(@NotNull T interval, int start, int end, Object data) {
    try {
      l.writeLock().lock();
      checkMax(true);
      processReferenceQueue();
      modCount++;
      IntervalNode newNode = createNewNode(interval, start, end, data);
      insert(newNode);

      checkMax(true); // myNode assigned
      return newNode;
    }
    finally {
      l.writeLock().unlock();
    }
  }

  // returns true if all markers are valid
  public boolean checkMax(boolean assertInvalid) {
    if (!VERIFY) return false;
    Ref<Boolean> allValid = new Ref<Boolean>(true);
    AtomicInteger counter = new AtomicInteger();
    checkMax(getRoot(), 0, assertInvalid, allValid, counter);
    if (assertInvalid) {
      assert size == counter.get() : "size: "+size+"; actual: "+counter;
    }
    return allValid.get();
  }

  // returns real (minStart, maxStart, maxEnd)
  protected Trinity<Integer,Integer,Integer> checkMax(IntervalNode root,
                                                      int deltaUpToRootExclusive,
                                                      boolean assertInvalid,
                                                      Ref<Boolean> allValid, AtomicInteger counter) {
    if (root == null) return Trinity.create(Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE);
    counter.getAndIncrement();
    int delta = deltaUpToRootExclusive + (root.isValid() ? root.delta : 0);
    Trinity<Integer, Integer, Integer> l = checkMax(root.getLeft(), delta, assertInvalid, allValid, counter);
    int minLeftStart = l.first;
    int maxLeftStart = l.second;
    int maxLeftEnd = l.third;
    Trinity<Integer, Integer, Integer> r = checkMax(root.getRight(), delta, assertInvalid, allValid, counter);
    int maxRightEnd = r.third;
    int minRightStart = r.first;
    int maxRightStart = r.second;
    if (!root.isValid()) {
      allValid.set(false);
      if (assertInvalid) assert false : (T)root;
      return Trinity.create(Math.min(minLeftStart, minRightStart), Math.max(maxLeftStart, maxRightStart), Math.max(maxRightEnd, maxLeftEnd));
    }
    IntervalNode parent = root.getParent();
    T rootKey = (T)root.getKey();
    if (parent != null && assertInvalid && rootKey != null) {
      int c = compareNodes(root, delta, parent, delta - root.delta, new ArrayList<IntervalNode>());
      assert c != 0;
      assert c < 0 && parent.getLeft() == root || c > 0 && parent.getRight() == root;
    }
    assert delta + root.maxEnd == Math.max(maxLeftEnd, Math.max(maxRightEnd, delta + root.intervalEnd()));
    int myStartOffset = delta + root.intervalStart();
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
    IntervalNode root = (IntervalNode)n;
    pushDelta(root.getParent());
    pushDelta(root);
    while (root.getRight() != null) {
      root = root.getRight();
      pushDelta(root);
    }
    return root;
  }

  @Override
  public boolean removeInterval(@NotNull T interval) {
    if (!interval.isValid()) return false;
    try {
      l.writeLock().lock();
      checkMax(true);
      processReferenceQueue();

      IntervalNode node = lookupNode(interval, root);
      if (node == null) return false;
      removeNode(node);
      checkMax(true);
      return true;
    }
    finally {
      l.writeLock().unlock();
    }
  }

  // run under write lock
  public void removeNode(@NotNull IntervalNode node) {
    deleteNode(node);
    IntervalNode parent = node.getParent();
    correctMaxUp(parent);
  }

  @Override
  protected void deleteNode(Node<T> n) {
    pushDeltaFromRoot((IntervalNode)n);
    super.deleteNode(n);
  }

  protected static void pushDelta(IntervalNode root) {
    if (root == null || !root.isValid()) return;
    int delta = root.delta;
    if (delta != 0) {
      root.setIntervalStart(root.intervalStart() + delta);
      root.setIntervalEnd(root.intervalEnd() + delta);
      root.maxEnd += delta;
      root.delta = 0;
      incDelta(root.getLeft(), delta);
      incDelta(root.getRight(), delta);
    }
  }

  private static void incDelta(IntervalNode root, int delta) {
    if (root == null) return;
    if (root.isValid()) {
      root.delta += delta;
    }
    else {
      incDelta(root.getLeft(), delta);
      incDelta(root.getRight(), delta);
    }
  }
  @Override
  protected IntervalNode swapWithMaxPred(Node<T> root, Node<T> maxPred) {
    checkMax(false);
    IntervalNode a = (IntervalNode)root;
    IntervalNode d = (IntervalNode)maxPred;
    Color acolor = a.color;
    Color dcolor = d.color;
    assert !a.isValid() || a.delta == 0 : a.delta;
    for (IntervalNode n = a.getLeft(); n != null; n = n.getRight()) {
      assert !n.isValid() || n.delta == 0 : n.delta;
    }
    swapNodes(a, d);

    // set range of the key to be deleted so it wont disrupt maxes
    a.setValid(false);
    //a.key.setIntervalStart(d.key.intervalStart());
    //a.key.setIntervalEnd(d.key.intervalEnd());

    //correctMaxUp(a);
    a.color = dcolor;
    d.color = acolor;
    correctMaxUp(a);

    checkMax(false);
    return a;
  }

  private void swapNodes(IntervalNode n1, IntervalNode n2) {
    IntervalNode l1 = n1.getLeft();
    IntervalNode r1 = n1.getRight();
    IntervalNode p1 = n1.getParent();
    IntervalNode l2 = n2.getLeft();
    IntervalNode r2 = n2.getRight();
    IntervalNode p2 = n2.getParent();

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

  protected static int maxEndOf(IntervalNode node, int deltaUpToRootExclusive) {
    if (node == null) {
      return 0;
    }
    if (node.isValid()) {
      return node.maxEnd + node.delta + deltaUpToRootExclusive;
    }
    return Math.max(maxEndOf(node.getLeft(), deltaUpToRootExclusive), maxEndOf(node.getRight(), deltaUpToRootExclusive));
  }

  // max of n.left's maxend, n.right's maxend and its own interval endOffset
  protected static void correctMax(@NotNull IntervalNode node, int deltaUpToRoot) {
    if (!node.isValid()) return;
    int realMax = Math.max(Math.max(maxEndOf(node.getLeft(), deltaUpToRoot), maxEndOf(node.getRight(), deltaUpToRoot)),
                           deltaUpToRoot + node.intervalEnd());
    node.maxEnd = realMax - deltaUpToRoot;
  }

  private static void correctMaxUp(IntervalNode node) {
    int delta = node == null ? 0 : node.computeDeltaUpToRoot();
    while (node != null) {
      if (node.isValid()) {
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
    IntervalNode node1 = (IntervalNode)n;
    IntervalNode node2 = node1.getLeft();
    IntervalNode node3 = node1.getRight();

    IntervalNode parent = node1.getParent();
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
    IntervalNode node1 = (IntervalNode)n;
    IntervalNode node2 = node1.getLeft();
    IntervalNode node3 = node1.getRight();

    IntervalNode parent = node1.getParent();
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
    IntervalNode myNode = (IntervalNode)node;
    if (child != null && myNode.isValid()) {
      ((IntervalNode)child).delta += myNode.delta;
      //todo correct max up to root??
    }
  }

  public Iterator<T> iterator() {
    IntervalNode firstNode = getRoot();
    while (firstNode != null && firstNode.getLeft() != null) {
      firstNode = firstNode.getLeft();
    }
    if (firstNode == null) {
      return ContainerUtil.emptyIterator();
    }
    return createIteratorFrom(firstNode);
  }

  private IntervalNode findMinOverlappingWith(IntervalNode root, Interval interval, int modCountBefore, int deltaUpToRootExclusive) {
    if (root == null) {
      return null;
    }
    assert root.isValid();

    int delta = deltaUpToRootExclusive + root.delta;
    if (interval.intervalStart() > maxEndOf(root, deltaUpToRootExclusive)) {
      return null; // right of the rightmost interval in the subtree
    }

    IntervalNode inLeft = findMinOverlappingWith(root.getLeft(), interval, modCountBefore, delta);
    if (inLeft != null) return inLeft;
    int myStartOffset = root.intervalStart() + delta;
    int myEndOffset = root.intervalEnd() + delta;
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
    IntervalNode firstOverlap = findMinOverlappingWith(getRoot(), interval, modCount, 0);
    if (firstOverlap == null) {
      return ContainerUtil.emptyIterator();
    }
    return createIteratorFrom(firstOverlap);
  }

  private Iterator<T> createIteratorFrom(@NotNull final IntervalNode firstNode) {
    checkMax(true);
    normalize();

    final int modCountBefore = modCount;
    return new Iterator<T>() {
      private IntervalNode node = firstNode;
      private T element = (T)firstNode.getKey();

      {
        // find first non-null key
        while (element == null) {
          moveNext();
          if (node == null) break;
        }
      }

      public boolean hasNext() {
        return element != null;
      }

      public T next() {
        assert modCount == modCountBefore : "Must not modify range markers during iterate";
        if (node == null || element == null) throw new NoSuchElementException();
        T current = element;
        moveNext();
        return current;
      }

      private void moveNext() {
        while (true) {
          node = getNextNode();
          if (node == null) {
            element = null;
            break;
          }
          element = (T)node.getKey();
          if (element != null) break;
        }
      }

      private IntervalNode getNextNode() {
        IntervalNode n = node.getRight();
        if (n != null) {
          while (n.getLeft()!= null) {
            n = n.getLeft();
          }
          return n;
        }
        IntervalNode parent = node.getParent();
        IntervalNode current = node;
        while (parent != null) {
          if (parent.getLeft() == current) return parent;
          current = parent;
          parent = parent.getParent();
        }
        return null;
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
      processReferenceQueue();
      if (normalized) return;
      normalize(getRoot());
      normalized = true;
    }
    finally {
      l.writeLock().unlock();
    }
  }

  private static void normalize(IntervalNode root) {
    if (root == null) return;
    pushDelta(root);
    normalize(root.getLeft());
    normalize(root.getRight());
  }

  // called under write lock
  private void processReferenceQueue() {
    int dead = 0;
    while (myReferenceQueue.poll() != null) {
      dead++;
    }

    deadReferenceCount += dead;
    if (deadReferenceCount > Math.max(1, size() / 3)) {
      purgeDeadNodes();
      deadReferenceCount = 0;
    }
  }

  private void purgeDeadNodes() {
    List<IntervalNode> gced = new ArrayList<IntervalNode>();
    collectGced(getRoot(), gced);
    deleteNodes(gced);
  }

  private static void collectGced(IntervalNode root, List<IntervalNode> gced) {
    if (root == null) return;
    if (root.getKey() == null) gced.add(root);
    collectGced(root.getLeft(), gced);
    collectGced(root.getRight(), gced);
  }

  public void changeAttribute(@NotNull IntervalNode node, @NotNull Runnable changeAttributes) {
    normalize();
    try {
      l.writeLock().lock();
      checkMax(true);
      removeNode(node);
      changeAttributes.run();
      insert(node);
      checkMax(true);
    }
    finally {
      l.writeLock().unlock();
    }
  }


  private void printSorted() { printSorted(getRoot());}
  private static void printSorted(IntervalNode root) {
    if (root == null) return;
    printSorted(root.getLeft());
    System.out.println(root);
    printSorted(root.getRight());
  }

}
