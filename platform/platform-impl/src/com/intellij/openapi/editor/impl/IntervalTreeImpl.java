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

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Trinity;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TLongHashSet;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * User: cdr
 */
public abstract class IntervalTreeImpl<T extends MutableInterval> extends RedBlackTree<T> implements IntervalTree<T> {
  private int keySize; // number of all intervals, counting all duplicates, some of them maybe gced
  protected final ReadWriteLock l = new ReentrantReadWriteLock();
  private IntervalNode minNode; // left most node in the tree

  protected abstract EqualStartIntervalComparator<IntervalNode> getComparator();
  private final ReferenceQueue<T> myReferenceQueue = new ReferenceQueue<T>();
  private int deadReferenceCount;

  protected class IntervalNode extends Node<T> implements MutableInterval {
    private volatile int myStart;
    private volatile int myEnd;
    private volatile boolean isValid = true;
    protected final SmartList<Getable<T>> intervals;
    protected int maxEnd; // max of all intervalEnd()s among all children.
    protected int delta;  // delta of startOffset. getStartOffset() = myStartOffset + Sum of deltas up to root
    IntervalNode next; // node following this in the in-order tree traversal. used for optimised tree iteration

    public IntervalNode(@NotNull T key, int start, int end) {
      // maxEnd == 0 so to not disrupt existing maxes
      intervals = new SmartList<Getable<T>>(createGetable(key));
      myStart = start;
      myEnd = end;
    }


    @Override
    public IntervalNode getLeft() {
      return (IntervalNode)left;
    }

    @Override
    public IntervalNode getRight() {
      return (IntervalNode)right;
    }

    @Override
    public IntervalNode getParent() {
      return (IntervalNode)parent;
    }

    @Override
    public boolean processAliveKeys(@NotNull Processor<? super T> processor) {
      for (Getable<T> interval : intervals) {
        T key = interval.get();
        if (key != null && !processor.process(key)) return false;
      }
      return true;
    }

    public boolean hasAliveKey(boolean purgeDead) {
      for (int i = intervals.size() - 1; i >= 0; i--) {
        Getable<T> interval = intervals.get(i);
        if (interval.get() != null) return true;
        if (purgeDead) {
          assertUnderWriteLock();
          intervals.remove(i);
          assert keySize > 0 : keySize;
          keySize--;
        }
      }
      return false;
    }

    // removes interval and the node, if node become empty
    // returns true if node was removed
    public boolean removeInterval(@NotNull T key) {
      checkBelongsToTheTree(key, true);
      assertUnderWriteLock();
      for (int i = intervals.size() - 1; i >= 0; i--) {
        Getable<T> interval = intervals.get(i);
        T t = interval.get();
        if (t == key) {
          intervals.remove(i);
          assert keySize > 0 : keySize;
          keySize--;
          if (intervals.isEmpty()) {
            removeNode(this);
            return true;
          }
          return false;
        }
      }
      assert false: "interval not found: "+key +"; "+ intervals;
      return false;
    }

    public void addInterval(@NotNull T interval) {
      assertUnderWriteLock();
      intervals.add(createGetable(interval));
      keySize++;
    }

    protected Getable<T> createGetable(@NotNull T interval) {
      return new WeakReferencedGetable<T>(interval, myReferenceQueue);
    }

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

    public IntervalTreeImpl<T> getTree() {
      return IntervalTreeImpl.this;
    }
  }

  private void assertUnderWriteLock() {
    String s = l.writeLock().toString();
    assert s.contains("Locked by thread") : s;
  }

  private void pushDeltaFromRoot(IntervalNode node) {
    if (normalized) return;
    if (node != null) {
      pushDeltaFromRoot(node.getParent());
      pushDelta(node);
    }
  }

  @NotNull
  protected abstract IntervalNode createNewNode(@NotNull T key, int start, int end, boolean greedyToLeft, boolean greedyToRight, int layer);
  protected abstract IntervalNode lookupNode(@NotNull T key);

  private int compareNodes(@NotNull IntervalNode i1, int delta1, @NotNull IntervalNode i2, int delta2, @NotNull List<IntervalNode> invalid) {
    if (!i2.hasAliveKey(false)) {
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

  protected IntervalNode getRoot() {
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
    if (!root.processAliveKeys(processor)) return false;
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
    if (overlaps) {
      if (!root.processAliveKeys(processor)) return false;
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

    if (overlaps) {
      if (!root.processAliveKeys(processor)) return false;
      assert modCount == modCountBefore;
    }

    if (offset < myStartOffset) {
      return true; // left of the root, cant be in the right subtree
    }

    return processOverlapping(root.getRight(), offset, processor, modCountBefore, delta);
  }

  protected IntervalNode findOrInsert(@NotNull IntervalNode node) {
    node.color = Color.RED;
    node.setParent(null);
    node.setValid(true);
    node.maxEnd = 0;
    node.delta = 0;
    node.setLeft(null);
    node.setRight(null);

    List<IntervalNode> gced = new ArrayList<IntervalNode>();
    if (root == null) {
      root = node;
    }
    else {
      IntervalNode current = getRoot();
      int delta = 0;
      loop:
      while (true) {
        delta += current.delta;
        int compResult = compareNodes(node, 0, current, delta, gced);
        if (compResult == 0) {
          return current;
        }
        if (compResult < 0) {
          if (current.getLeft() == null) {
            current.setLeft(node);
            break loop;
          }
          current = current.getLeft();
        }
        else /*if (compResult > 0)*/ {
          if (current.getRight() == null) {
            current.setRight(node);
            break loop;
          }
          current = current.getRight();
        }
      }
      node.delta = -delta;
      node.setParent(current);
    }
    linkNode(node);
    correctMaxUp(node);
    onInsertNode();
    assertUnderWriteLock();
    keySize += node.intervals.size();
    insertCase1(node);
    verifyProperties();

    deleteNodes(gced);
    return node;
  }

  private void linkNode(@NotNull IntervalNode node) {
    IntervalNode previous = previous(node);
    if (previous == null) {
      node.next = minNode;
      minNode = node;
    }
    else {
      node.next = previous.next;
      previous.next = node;
    }
  }

  private void unlinkNode(@NotNull IntervalNode node) {
    IntervalNode previous = previous(node);
    if (previous == null) {
      minNode = node.next;
    }
    else {
      previous.next = node.next;
    }
    node.next = null;
  }

  // finds previous in the in-order traversal
  private IntervalNode previous(@NotNull IntervalNode node) {
    IntervalNode left = node.getLeft();
    if (left != null) {
      while (left.getRight() != null) {
        left = left.getRight();
      }
      return left;
    }
    IntervalNode parent = node.getParent();
    while (parent != null) {
      if (parent.getRight() == node) break;
      node = parent;
      parent = parent.getParent();
    }
    return parent;
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

  public IntervalTreeImpl<T>.IntervalNode addInterval(@NotNull T interval, int start, int end, boolean greedyToLeft, boolean greedyToRight, int layer) {
    try {
      l.writeLock().lock();
      checkMax(true);
      processReferenceQueue();
      modCount++;
      IntervalNode newNode = createNewNode(interval, start, end, greedyToLeft, greedyToRight, layer);
      IntervalNode insertedNode = findOrInsert(newNode);
      if (insertedNode != newNode) {
        // merged
        insertedNode.addInterval(interval);
      }

      checkMax(true);
      return insertedNode;
    }
    finally {
      l.writeLock().unlock();
    }
  }

  // returns true if all markers are valid
  public boolean checkMax(boolean assertInvalid) {
    if (!VERIFY) return false;
    Ref<Boolean> allValid = new Ref<Boolean>(true);
    AtomicInteger keyCounter = new AtomicInteger();
    AtomicInteger nodeCounter = new AtomicInteger();
    TLongHashSet ids = new TLongHashSet();
    checkMax(getRoot(), 0, assertInvalid, allValid, keyCounter, nodeCounter, ids);
    if (assertInvalid) {
      assert nodeSize() == nodeCounter.get() : "node size: "+ nodeSize() +"; actual: "+nodeCounter;
      assert keySize == keyCounter.get() : "key size: "+ keySize +"; actual: "+keyCounter;
    }
    return allValid.get();
  }

  // returns real (minStart, maxStart, maxEnd)
  private Trinity<Integer,Integer,Integer> checkMax(IntervalNode root,
                                                    int deltaUpToRootExclusive,
                                                    boolean assertInvalid,
                                                    Ref<Boolean> allValid,
                                                    AtomicInteger keyCounter,
                                                    AtomicInteger nodeCounter,
                                                    TLongHashSet ids) {
    if (root == null) return Trinity.create(Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE);
    for (int i = root.intervals.size() - 1; i >= 0; i--) {
      T t = root.intervals.get(i).get();
      if (t == null) continue;
      checkBelongsToTheTree(t, assertInvalid);
      assert ids.add(((RangeMarkerImpl)t).getId()) : t;
    }

    keyCounter.addAndGet(root.intervals.size());
    nodeCounter.incrementAndGet();
    int delta = deltaUpToRootExclusive + (root.isValid() ? root.delta : 0);
    Trinity<Integer, Integer, Integer> l = checkMax(root.getLeft(), delta, assertInvalid, allValid, keyCounter, nodeCounter, ids);
    int minLeftStart = l.first;
    int maxLeftStart = l.second;
    int maxLeftEnd = l.third;
    Trinity<Integer, Integer, Integer> r = checkMax(root.getRight(), delta, assertInvalid, allValid, keyCounter, nodeCounter, ids);
    int maxRightEnd = r.third;
    int minRightStart = r.first;
    int maxRightStart = r.second;
    if (!root.isValid()) {
      allValid.set(false);
      if (assertInvalid) assert false : root;
      return Trinity.create(Math.min(minLeftStart, minRightStart), Math.max(maxLeftStart, maxRightStart), Math.max(maxRightEnd, maxLeftEnd));
    }
    IntervalNode parent = root.getParent();
    if (parent != null && assertInvalid && root.hasAliveKey(false)) {
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

  protected void checkBelongsToTheTree(T interval, boolean assertInvalid) {
    IntervalNode root = lookupNode(interval);
    if (root == null) return;
    assert !root.intervals.isEmpty();

    assert root.getTree() == this : root.getTree() +"; this: "+this;
    if (!VERIFY) return;
    boolean contains = false;
    for (int i = root.intervals.size() - 1; i >= 0; i--) {
      T key = root.intervals.get(i).get();
      if (key == null) continue;
      contains |= key == interval;
      IntervalNode node = lookupNode(key);
      assert assertInvalid && node == root || !assertInvalid && (node == null || node == root) : node;
      assert assertInvalid && node.getTree() == this || !assertInvalid && (node == null || node.getTree() == this) : node;
    }

    assert contains : root.intervals + "; " + interval;

    IntervalNode e = root;
    while (e.getParent() != null) e = e.getParent();
    assert e == getRoot(); // assert the node belongs to our tree
  }

  @Override
  public boolean removeInterval(@NotNull T interval) {
    if (!interval.isValid()) return false;
    try {
      l.writeLock().lock();
      checkBelongsToTheTree(interval, true);
      checkMax(true);
      processReferenceQueue();

      IntervalNode node = lookupNode(interval);
      if (node == null) return false;

      node.removeInterval(interval);
      checkMax(true);
      return true;
    }
    finally {
      l.writeLock().unlock();
    }
  }

  // run under write lock
  void removeNode(@NotNull IntervalNode node) {
    deleteNode(node);
    IntervalNode parent = node.getParent();
    correctMaxUp(parent);
  }

  @Override
  protected void deleteNode(Node<T> n) {
    IntervalNode node = (IntervalNode)n;
    pushDeltaFromRoot(node);
    unlinkNode(node);

    super.deleteNode(n);

    assertUnderWriteLock();
    keySize -= node.intervals.size();
    assert keySize >= 0 : keySize;
  }

  @Override
  public int size() {
    return keySize;
  }

  // returns true if all deltas involved are still 0
  protected boolean pushDelta(IntervalNode root) {
    if (root == null || !root.isValid()) return true;
    int delta = root.delta;
    if (delta != 0) {
      root.setIntervalStart(root.intervalStart() + delta);
      root.setIntervalEnd(root.intervalEnd() + delta);
      root.maxEnd += delta;
      root.delta = 0;
      //noinspection NonShortCircuitBooleanExpression
      return
      incDelta(root.getLeft(), delta) &
      incDelta(root.getRight(), delta);
    }
    return true;
  }

  // returns true if all deltas involved are still 0
  private boolean incDelta(IntervalNode root, int delta) {
    if (root == null) return true;
    if (root.isValid()) {
      int newDelta = root.delta += delta;
      return newDelta == 0;
    }
    else {
      //noinspection NonShortCircuitBooleanExpression
      return
      incDelta(root.getLeft(), delta) &
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

  private int maxEndOf(IntervalNode node, int deltaUpToRootExclusive) {
    if (node == null) {
      return 0;
    }
    if (node.isValid()) {
      return node.maxEnd + node.delta + deltaUpToRootExclusive;
    }
    return Math.max(maxEndOf(node.getLeft(), deltaUpToRootExclusive), maxEndOf(node.getRight(), deltaUpToRootExclusive));
  }

  // max of n.left's maxend, n.right's maxend and its own interval endOffset
  protected void correctMax(@NotNull IntervalNode node, int deltaUpToRoot) {
    if (!node.isValid()) return;
    int realMax = Math.max(Math.max(maxEndOf(node.getLeft(), deltaUpToRoot), maxEndOf(node.getRight(), deltaUpToRoot)),
                           deltaUpToRoot + node.intervalEnd());
    node.maxEnd = realMax - deltaUpToRoot;
  }

  private void correctMaxUp(IntervalNode node) {
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
    IntervalNode firstNode = minNode;
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
      private int indexInCurrentList = 0;
      T current;

      public boolean hasNext() {
        if (current != null) return true;
        while (node != null) {
          while (indexInCurrentList != node.intervals.size()) {
            current = node.intervals.get(indexInCurrentList).get();
            if (current != null) return true;
            indexInCurrentList++;
          }
          indexInCurrentList = 0;
          node = getNextNode(node);
        }
        return false;
      }

      public T next() {
        assert modCount == modCountBefore : "Must not modify range markers during iterate";
        if (!hasNext()) throw new NoSuchElementException();

        T t = current;
        current = null;

        indexInCurrentList++;
        return t;
      }


      private IntervalNode getNextNode(IntervalNode node) {
        return node.next;
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
  private void normalize(IntervalNode root) {
    if (root == null) return;
    pushDelta(root);
    normalize(root.getLeft());
    normalize(root.getRight());
  }

  public void changeData(T interval, int start, int end, boolean greedyToLeft, boolean greedyToRight, int layer) {
    try {
      l.writeLock().lock();

      IntervalNode node = lookupNode(interval);
      if (node == null) return;
      int before = size();
      boolean nodeRemoved = node.removeInterval(interval);
      assert nodeRemoved || !node.intervals.isEmpty();

      IntervalNode insertedNode = addInterval(interval, start, end, greedyToLeft, greedyToRight, layer);
      assert node != insertedNode;

      int after = size();
      // can be gced
      assert before >= after : before +";" + after;
      checkBelongsToTheTree(interval, true);
      checkMax(true);
    }
    finally {
      l.writeLock().unlock();
    }
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

  private void collectGced(IntervalNode root, List<IntervalNode> gced) {
    if (root == null) return;
    if (!root.hasAliveKey(true)) gced.add(root);
    collectGced(root.getLeft(), gced);
    collectGced(root.getRight(), gced);
  }


  private void printSorted() { printSorted(getRoot());}
  private void printSorted(IntervalNode root) {
    if (root == null) return;
    printSorted(root.getLeft());
    System.out.println(root);
    printSorted(root.getRight());
  }
}
