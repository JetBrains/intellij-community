// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.modTree;

import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@ApiStatus.Internal
public final class ModificationBTreeImpl implements ModificationTree {
  private static final int BRANCHING_FACTOR = 32;
  private static final int MAX_CHILDREN = BRANCHING_FACTOR;
  private static final int MIN_CHILDREN = MAX_CHILDREN / 2;

  private final Node root;
  private final int version0Length;
  private final int currentLength;

  private ModificationBTreeImpl(Node root, int version0Length, int currentLength) {
    if (version0Length < 0) {
      throw new IllegalArgumentException("version0Length must be >= 0: " + version0Length);
    }

    if (currentLength < 0) {
      throw new IllegalArgumentException("currentLength must be >= 0: " + currentLength);
    }

    this.root = root;
    this.version0Length = version0Length;
    this.currentLength = currentLength;
  }

  public static @NotNull ModificationBTreeImpl initial(int length) {
    if (length < 0) {
      throw new IllegalArgumentException("length must be >= 0: " + length);
    }

    Node root = length == 0
                ? null
                : new Leaf(0, length, 0);

    return new ModificationBTreeImpl(root, length, length);
  }

  @Override
  public int toCurrentOffset(int offsetInVersion0) {
    if (offsetInVersion0 < 0 || offsetInVersion0 > version0Length) {
      throw new IndexOutOfBoundsException(
        "offsetInVersion0=" + offsetInVersion0 +
        ", version0Length=" + version0Length
      );
    }

    Node node = root;
    int accumulatedDelta = 0;

    while (node != null) {
      accumulatedDelta = Math.addExact(accumulatedDelta, node.delta());

      if (node instanceof Leaf leaf) {
        if (offsetInVersion0 < leaf.start0()) {
          return Math.addExact(leaf.start0(), accumulatedDelta);
        }

        if (offsetInVersion0 < leaf.end0()) {
          return Math.addExact(offsetInVersion0, accumulatedDelta);
        }

        return currentLength;
      }

      Branch branch = (Branch)node;
      Node next = null;
      Node[] children = branch.children();

      int i = ObjectUtils.binarySearch(0, children.length, mid -> {
        Node child = children[mid];
        if (offsetInVersion0 < child.start0()) {
          return 1;
        }
        if (offsetInVersion0 < child.end0()) {
          return 0;
        }
        else {
          return -1;
        }
      });
      if (i >= 0) {
        next = children[i];
      }
      else {
        int insertionIndex = -i - 1;
        if (insertionIndex < children.length) {
          return children[insertionIndex].currentStart() + accumulatedDelta;
        }
      }

      //for (Node child : children) {
      //  if (offsetInVersion0 < child.start0()) {
      //    return Math.addExact(accumulatedDelta, child.currentStart());
      //  }
      //
      //  if (offsetInVersion0 < child.end0()) {
      //    next = child;
      //    break;
      //  }
      //}

      if (next == null) {
        return currentLength;
      }

      node = next;
    }

    return currentLength;
  }

  @Override
  public int toVersion0Offset(int offsetInCurrent) {
    if (offsetInCurrent < 0 || offsetInCurrent > currentLength) {
      throw new IndexOutOfBoundsException(
        "offsetInCurrent=" + offsetInCurrent +
        ", currentLength=" + currentLength
      );
    }

    Node node = root;
    int accumulatedDelta = 0;

    while (node != null) {
      accumulatedDelta = Math.addExact(accumulatedDelta, node.delta());

      if (node instanceof Leaf leaf) {
        int currentStart = Math.addExact(leaf.start0(), accumulatedDelta);
        int currentEnd = Math.addExact(leaf.end0(), accumulatedDelta);

        if (offsetInCurrent < currentStart) {
          return leaf.start0();
        }

        if (offsetInCurrent < currentEnd) {
          return Math.subtractExact(offsetInCurrent, accumulatedDelta);
        }

        return version0Length;
      }

      Branch branch = (Branch)node;
      Node next = null;

      for (Node child : branch.children()) {
        int childCurrentStart = Math.addExact(accumulatedDelta, child.currentStart());
        int childCurrentEnd = Math.addExact(accumulatedDelta, child.currentEnd());

        if (offsetInCurrent < childCurrentStart) {
          return child.start0();
        }

        if (offsetInCurrent < childCurrentEnd) {
          next = child;
          break;
        }
      }

      if (next == null) {
        return version0Length;
      }

      node = next;
    }

    return version0Length;
  }

  @Override
  public @NotNull ModificationTree insert(int offsetInCurrent, int length) {
    if (offsetInCurrent < 0 || offsetInCurrent > currentLength) {
      throw new IndexOutOfBoundsException(
        "offsetInCurrent=" + offsetInCurrent +
        ", currentLength=" + currentLength
      );
    }

    if (length < 0) {
      throw new IllegalArgumentException("length must be >= 0: " + length);
    }

    if (length == 0) {
      return this;
    }

    int boundary0 = toVersion0Offset(offsetInCurrent);

    Split split = splitByVersion0(root, boundary0);
    Node shiftedRight = addDelta(split.right(), length);
    Node newRoot = concat(split.left(), shiftedRight);

    return new ModificationBTreeImpl(
      newRoot,
      version0Length,
      Math.addExact(currentLength, length)
    );
  }

  @Override
  public @NotNull ModificationTree delete(int startInCurrent, int endInCurrent) {
    if (startInCurrent < 0 || startInCurrent > currentLength) {
      throw new IndexOutOfBoundsException(
        "startInCurrent=" + startInCurrent +
        ", currentLength=" + currentLength
      );
    }

    if (endInCurrent < 0 || endInCurrent > currentLength) {
      throw new IndexOutOfBoundsException(
        "endInCurrent=" + endInCurrent +
        ", currentLength=" + currentLength
      );
    }

    if (startInCurrent > endInCurrent) {
      throw new IllegalArgumentException(
        "startInCurrent must be <= endInCurrent: " +
        startInCurrent + " > " + endInCurrent
      );
    }

    int deletedLength = endInCurrent - startInCurrent;

    if (deletedLength == 0) {
      return this;
    }

    int start0 = toVersion0Offset(startInCurrent);
    int end0 = toVersion0Offset(endInCurrent);

    Split first = splitByVersion0(root, start0);
    Split second = splitByVersion0(first.right(), end0);

    Node shiftedRight = addDelta(second.right(), -deletedLength);
    Node newRoot = concat(first.left(), shiftedRight);

    return new ModificationBTreeImpl(
      newRoot,
      version0Length,
      Math.subtractExact(currentLength, deletedLength)
    );
  }

  @Override
  public void checkInvariants() {
    if (version0Length < 0) {
      throw new IllegalStateException("version0Length < 0: " + version0Length);
    }

    if (currentLength < 0) {
      throw new IllegalStateException("currentLength < 0: " + currentLength);
    }

    if (root != null) {
      CheckInfo info = checkNode(root, true, 0);

      if (info.start0() < 0) {
        throw new IllegalStateException("root starts before version 0");
      }

      if (info.end0() > version0Length) {
        throw new IllegalStateException(
          "root end exceeds version0Length: " +
          info.end0() + " > " + version0Length
        );
      }

      if (info.currentStart() < 0) {
        throw new IllegalStateException("root currentStart < 0: " + info.currentStart());
      }

      if (info.currentEnd() > currentLength) {
        throw new IllegalStateException(
          "root currentEnd exceeds currentLength: " +
          info.currentEnd() + " > " + currentLength
        );
      }
    }

    int currentEof = toCurrentOffset(version0Length);

    if (currentEof != currentLength) {
      throw new IllegalStateException(
        "version-0 EOF maps to " + currentEof +
        ", but currentLength is " + currentLength
      );
    }

    int version0Eof = toVersion0Offset(currentLength);

    if (version0Eof != version0Length) {
      throw new IllegalStateException(
        "current EOF maps to " + version0Eof +
        ", but version0Length is " + version0Length
      );
    }
  }

  /// # Node model
  ///
  /// The tree stores only surviving original text.
  ///
  /// `Leaf(start0, end0, delta)` represents one live version-0 range:
  ///
  /// `[start0, end0)`
  ///
  /// Its current-version range is:
  ///
  /// `[start0 + effectiveDelta, end0 + effectiveDelta)`
  ///
  /// where:
  ///
  /// `effectiveDelta = sum(delta on path from root to leaf)`
  ///
  /// Deleted original text is absent from the tree.
  /// Inserted current text is represented as gaps between transformed leaves.

  private sealed interface Node permits Branch, Leaf {
    int delta();

    int start0();

    int end0();

    int currentStart();

    int currentEnd();

    int height();

    int runCount();
  }

  private record Leaf(
    int start0,
    int end0,
    int delta
  ) implements Node {
    Leaf {
      if (start0 >= end0) {
        throw new IllegalArgumentException(
          "Invalid leaf range: [" + start0 + ", " + end0 + ")"
        );
      }
    }

    @Override
    public int currentStart() {
      return Math.addExact(start0, delta);
    }

    @Override
    public int currentEnd() {
      return Math.addExact(end0, delta);
    }

    @Override
    public int height() {
      return 0;
    }

    @Override
    public int runCount() {
      return 1;
    }

    @NotNull
    Leaf addDelta(int deltaDiff) {
      if (deltaDiff == 0) {
        return this;
      }

      return new Leaf(start0, end0, Math.addExact(delta, deltaDiff));
    }
  }

  private record Branch(
    int delta,
    @NotNull Node[] children,
    int start0,
    int end0,
    int currentStart,
    int currentEnd,
    int height,
    int runCount
  ) implements Node {
    static @NotNull Branch of(int delta, @NotNull Node[] children) {
      Objects.requireNonNull(children, "children");

      if (children.length < 2) {
        throw new IllegalArgumentException("Branch must contain at least two children");
      }

      if (children.length > MAX_CHILDREN) {
        throw new IllegalArgumentException("Too many children: " + children.length);
      }

      int childHeight = children[0].height();
      int runCount = 0;

      for (int i = 0; i < children.length; i++) {
        Node child = children[i];

        Objects.requireNonNull(child, "children[" + i + "]");

        if (child.height() != childHeight) {
          throw new IllegalArgumentException("Children must have equal height");
        }

        if (i > 0 && children[i - 1].end0() > child.start0()) {
          throw new IllegalArgumentException("Overlapping version-0 child ranges");
        }

        if (i > 0 && children[i - 1].currentEnd() > child.currentStart()) {
          throw new IllegalArgumentException("Overlapping current child ranges");
        }

        runCount = Math.addExact(runCount, child.runCount());
      }

      Node first = children[0];
      Node last = children[children.length - 1];

      return new Branch(
        delta,
        children,
        first.start0(),
        last.end0(),
        Math.addExact(delta, first.currentStart()),
        Math.addExact(delta, last.currentEnd()),
        childHeight + 1,
        runCount
      );
    }

    Branch {
      Objects.requireNonNull(children, "children");
    }

    @NotNull
    Branch addDelta(int deltaDiff) {
      if (deltaDiff == 0) {
        return this;
      }

      return Branch.of(Math.addExact(delta, deltaDiff), children);
    }
  }

  private record Split(Node left, Node right) {
  }

  private record CheckInfo(
    int start0,
    int end0,
    int currentStart,
    int currentEnd,
    int height,
    int runCount,
    int firstEffectiveDelta,
    int lastEffectiveDelta
  ) {
  }

  /// # Split
  ///
  /// Splits the tree by version-0 coordinate.
  ///
  /// Result:
  ///
  /// - `left`: all live original ranges before `boundary0`
  /// - `right`: all live original ranges at or after `boundary0`
  ///
  /// If `boundary0` falls inside a leaf, the leaf is split into two leaves.

  private static @NotNull Split splitByVersion0(Node node, int boundary0) {
    if (node == null) {
      return new Split(null, null);
    }

    if (boundary0 <= node.start0()) {
      return new Split(null, node);
    }

    if (boundary0 >= node.end0()) {
      return new Split(node, null);
    }

    if (node instanceof Leaf(int start0, int end0, int delta)) {
      Leaf left = new Leaf(start0, boundary0, delta);
      Leaf right = new Leaf(boundary0, end0, delta);

      return new Split(left, right);
    }

    Branch branch = (Branch)node;
    Node[] children = materializedChildren(branch);

    ArrayList<Node> left = new ArrayList<>(children.length);
    ArrayList<Node> right = new ArrayList<>(children.length);

    boolean splitDone = false;

    for (Node child : children) {
      if (splitDone) {
        appendNode(right, child);
        continue;
      }

      if (boundary0 <= child.start0()) {
        appendNode(right, child);
        splitDone = true;
      }
      else if (boundary0 >= child.end0()) {
        appendNode(left, child);
      }
      else {
        Split childSplit = splitByVersion0(child, boundary0);

        if (childSplit.left() != null) {
          appendNode(left, childSplit.left());
        }

        if (childSplit.right() != null) {
          appendNode(right, childSplit.right());
        }

        splitDone = true;
      }
    }

    return new Split(
      concatAll(left),
      concatAll(right)
    );
  }

  /// # Concatenation with fringe repacking
  ///
  /// Concatenates two ordered trees.
  ///
  /// This descends only through the touching fringes:
  ///
  /// - right fringe of `left`
  /// - left fringe of `right`
  ///
  /// Boundary nodes are repacked evenly, which prevents the tree from
  /// degrading into a binary-shaped tree after many edits.

  private static Node concat(Node left, Node right) {
    if (left == null) {
      return right;
    }

    if (right == null) {
      return left;
    }

    return packUpAsRoot(appendNodes(left, right));
  }

  private static Node concatAll(@NotNull List<Node> nodes) {
    Node result = null;

    for (Node node : nodes) {
      result = concat(result, node);
    }

    return result;
  }

  private static @NotNull List<Node> appendNodes(
    @NotNull Node left,
    @NotNull Node right
  ) {
    if (left.height() == right.height()) {
      if (left instanceof Leaf leftLeaf && right instanceof Leaf rightLeaf) {
        ArrayList<Node> nodes = new ArrayList<>(2);

        appendNode(nodes, leftLeaf);
        appendNode(nodes, rightLeaf);

        return nodes;
      }

      Branch leftBranch = (Branch)left;
      Branch rightBranch = (Branch)right;

      Node[] leftChildren = materializedChildren(leftBranch);
      Node[] rightChildren = materializedChildren(rightBranch);

      ArrayList<Node> children = new ArrayList<>(leftChildren.length + rightChildren.length);

      for (int i = 0; i < leftChildren.length - 1; i++) {
        appendNode(children, leftChildren[i]);
      }

      for (Node node : appendNodes(leftChildren[leftChildren.length - 1], rightChildren[0])) {
        appendNode(children, node);
      }

      for (int i = 1; i < rightChildren.length; i++) {
        appendNode(children, rightChildren[i]);
      }

      return packChildren(children);
    }

    if (left.height() > right.height()) {
      Branch leftBranch = (Branch)left;
      Node[] children = materializedChildren(leftBranch);

      ArrayList<Node> newChildren = new ArrayList<>(children.length + 1);

      for (int i = 0; i < children.length - 1; i++) {
        appendNode(newChildren, children[i]);
      }

      for (Node node : appendNodes(children[children.length - 1], right)) {
        appendNode(newChildren, node);
      }

      return packChildren(newChildren);
    }

    Branch rightBranch = (Branch)right;
    Node[] children = materializedChildren(rightBranch);

    ArrayList<Node> newChildren = new ArrayList<>(children.length + 1);

    for (Node node : appendNodes(left, children[0])) {
      appendNode(newChildren, node);
    }

    for (int i = 1; i < children.length; i++) {
      appendNode(newChildren, children[i]);
    }

    return packChildren(newChildren);
  }

  /// # Packing
  ///
  /// `packChildren` packs same-height nodes into branches.
  ///
  /// `packUpAsRoot` repeatedly packs levels until one root remains.
  ///
  /// Root nodes may be underfull.
  /// Non-root branches are expected to have at least `MIN_CHILDREN` children.

  private static Node packUpAsRoot(@NotNull List<Node> nodes) {
    if (nodes.isEmpty()) {
      return null;
    }

    List<Node> level = nodes;

    while (level.size() > 1) {
      level = packChildren(level);
    }

    return level.get(0);
  }

  private static @NotNull List<Node> packChildren(@NotNull List<Node> children) {
    if (children.isEmpty()) {
      return List.of();
    }

    if (children.size() == 1) {
      return List.of(children.get(0));
    }

    int height = children.get(0).height();

    for (Node child : children) {
      if (child.height() != height) {
        throw new IllegalArgumentException(
          "Cannot pack children with different heights"
        );
      }
    }

    int groupCount = groupCount(children.size(), MAX_CHILDREN);
    ArrayList<Node> result = new ArrayList<>(groupCount);

    int baseSize = children.size() / groupCount;
    int remainder = children.size() % groupCount;
    int index = 0;

    for (int group = 0; group < groupCount; group++) {
      int groupSize = baseSize + (group < remainder ? 1 : 0);

      Node[] groupChildren = new Node[groupSize];

      for (int i = 0; i < groupSize; i++) {
        groupChildren[i] = children.get(index++);
      }

      if (groupChildren.length == 1) {
        result.add(groupChildren[0]);
      }
      else {
        result.add(Branch.of(0, groupChildren));
      }
    }

    return result;
  }

  private static int groupCount(int itemCount, int maxGroupSize) {
    if (itemCount <= 0) {
      throw new IllegalArgumentException("itemCount must be positive: " + itemCount);
    }

    return (itemCount + maxGroupSize - 1) / maxGroupSize;
  }

  /// # Boundary coalescing
  ///
  /// Adjacent leaves with equal effective delta are merged.
  ///
  /// This is what keeps repeated insert/delete around the same original
  /// boundary from producing unnecessary fragmentation.

  private static void appendNode(@NotNull List<Node> nodes, @NotNull Node node) {
    if (nodes.isEmpty()) {
      nodes.add(node);
      return;
    }

    Node last = nodes.get(nodes.size() - 1);

    if (last instanceof Leaf leftLeaf && node instanceof Leaf rightLeaf && canMerge(leftLeaf, rightLeaf)) {
      nodes.set(
        nodes.size() - 1,
        new Leaf(leftLeaf.start0(), rightLeaf.end0(), leftLeaf.delta())
      );
      return;
    }

    nodes.add(node);
  }

  private static boolean canMerge(@NotNull Leaf left, @NotNull Leaf right) {
    return left.end0() == right.start0()
           && left.delta() == right.delta();
  }

  /// # Lazy delta handling
  ///
  /// Adding delta to a subtree shifts all live ranges in that subtree.
  ///
  /// Since deltas are path-summed, this only changes the subtree root.

  private static Node addDelta(Node node, int deltaDiff) {
    if (node == null || deltaDiff == 0) {
      return node;
    }

    if (node instanceof Leaf leaf) {
      return leaf.addDelta(deltaDiff);
    }

    return ((Branch)node).addDelta(deltaDiff);
  }

  private static @NotNull Node[] materializedChildren(@NotNull Branch branch) {
    Node[] source = branch.children();
    Node[] result = new Node[source.length];

    for (int i = 0; i < source.length; i++) {
      result[i] = addDelta(source[i], branch.delta());
    }

    return result;
  }

  /// # Invariant checking

  private CheckInfo checkNode(
    @NotNull Node node,
    boolean isRoot,
    int accumulatedDeltaBeforeNode
  ) {
    if (node instanceof Leaf leaf) {
      return checkLeaf(leaf, accumulatedDeltaBeforeNode);
    }

    return checkBranch((Branch)node, isRoot, accumulatedDeltaBeforeNode);
  }

  private CheckInfo checkLeaf(
    @NotNull Leaf leaf,
    int accumulatedDeltaBeforeLeaf
  ) {
    if (leaf.start0() < 0) {
      throw new IllegalStateException("leaf starts before version 0");
    }

    if (leaf.end0() > version0Length) {
      throw new IllegalStateException(
        "leaf end exceeds version0Length: " +
        leaf.end0() + " > " + version0Length
      );
    }

    if (leaf.start0() >= leaf.end0()) {
      throw new IllegalStateException(
        "invalid leaf range: [" + leaf.start0() + ", " + leaf.end0() + ")"
      );
    }

    if (leaf.height() != 0) {
      throw new IllegalStateException("leaf height must be 0");
    }

    if (leaf.runCount() != 1) {
      throw new IllegalStateException("leaf runCount must be 1");
    }

    int effectiveDelta = Math.addExact(accumulatedDeltaBeforeLeaf, leaf.delta());
    int currentStart = Math.addExact(leaf.start0(), effectiveDelta);
    int currentEnd = Math.addExact(leaf.end0(), effectiveDelta);

    if (currentStart < 0) {
      throw new IllegalStateException("leaf currentStart < 0: " + currentStart);
    }

    if (currentEnd > currentLength) {
      throw new IllegalStateException(
        "leaf currentEnd exceeds currentLength: " +
        currentEnd + " > " + currentLength
      );
    }

    if (currentStart >= currentEnd) {
      throw new IllegalStateException(
        "invalid leaf current range: [" + currentStart + ", " + currentEnd + ")"
      );
    }

    if (Math.addExact(accumulatedDeltaBeforeLeaf, leaf.currentStart()) != currentStart) {
      throw new IllegalStateException("leaf currentStart summary mismatch");
    }

    if (Math.addExact(accumulatedDeltaBeforeLeaf, leaf.currentEnd()) != currentEnd) {
      throw new IllegalStateException("leaf currentEnd summary mismatch");
    }

    return new CheckInfo(
      leaf.start0(),
      leaf.end0(),
      currentStart,
      currentEnd,
      0,
      1,
      effectiveDelta,
      effectiveDelta
    );
  }

  private CheckInfo checkBranch(
    @NotNull Branch branch,
    boolean isRoot,
    int accumulatedDeltaBeforeBranch
  ) {
    Node[] children = branch.children();

    if (children.length == 0) {
      throw new IllegalStateException("empty branch");
    }

    if (children.length == 1) {
      throw new IllegalStateException("branch with one child should be collapsed");
    }

    if (children.length > MAX_CHILDREN) {
      throw new IllegalStateException(
        "branch has too many children: " + children.length
      );
    }

    if (!isRoot && children.length < MIN_CHILDREN) {
      throw new IllegalStateException(
        "non-root branch is underfull: " +
        children.length + " < " + MIN_CHILDREN
      );
    }

    int accumulatedDelta = Math.addExact(accumulatedDeltaBeforeBranch, branch.delta());
    int childHeight = children[0].height();
    int expectedRunCount = 0;

    CheckInfo firstInfo = null;
    CheckInfo previousInfo = null;
    CheckInfo lastInfo = null;

    for (Node child : children) {
      if (child == null) {
        throw new IllegalStateException("null child");
      }

      if (child.height() != childHeight) {
        throw new IllegalStateException("children with different heights");
      }

      CheckInfo childInfo = checkNode(child, false, accumulatedDelta);

      expectedRunCount = Math.addExact(expectedRunCount, childInfo.runCount());

      if (firstInfo == null) {
        firstInfo = childInfo;
      }

      if (previousInfo != null) {
        if (previousInfo.end0() > childInfo.start0()) {
          throw new IllegalStateException("overlapping version-0 child ranges");
        }

        if (previousInfo.currentEnd() > childInfo.currentStart()) {
          throw new IllegalStateException("overlapping current child ranges");
        }

        if (
          previousInfo.end0() == childInfo.start0() &&
          previousInfo.lastEffectiveDelta() == childInfo.firstEffectiveDelta()
        ) {
          throw new IllegalStateException(
            "mergeable adjacent leaves remain across child boundary"
          );
        }
      }

      previousInfo = childInfo;
      lastInfo = childInfo;
    }

    int expectedHeight = childHeight + 1;

    if (branch.height() != expectedHeight) {
      throw new IllegalStateException(
        "branch height mismatch: " +
        branch.height() + " != " + expectedHeight
      );
    }

    if (branch.runCount() != expectedRunCount) {
      throw new IllegalStateException(
        "branch runCount mismatch: " +
        branch.runCount() + " != " + expectedRunCount
      );
    }

    if (branch.start0() != firstInfo.start0()) {
      throw new IllegalStateException("branch start0 summary mismatch");
    }

    if (branch.end0() != lastInfo.end0()) {
      throw new IllegalStateException("branch end0 summary mismatch");
    }

    if (Math.addExact(accumulatedDeltaBeforeBranch, branch.currentStart()) != firstInfo.currentStart()) {
      throw new IllegalStateException("branch currentStart summary mismatch");
    }

    if (Math.addExact(accumulatedDeltaBeforeBranch, branch.currentEnd()) != lastInfo.currentEnd()) {
      throw new IllegalStateException("branch currentEnd summary mismatch");
    }

    return new CheckInfo(
      firstInfo.start0(),
      lastInfo.end0(),
      firstInfo.currentStart(),
      lastInfo.currentEnd(),
      expectedHeight,
      expectedRunCount,
      firstInfo.firstEffectiveDelta(),
      lastInfo.lastEffectiveDelta()
    );
  }
}