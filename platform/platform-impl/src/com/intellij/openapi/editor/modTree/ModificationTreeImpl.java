// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.modTree;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ModificationTreeImpl implements ModificationTree {
  private final Node root;

  private final int version0Length;
  private final int currentLength;

  private ModificationTreeImpl(Node root, int version0Length, int currentLength) {
    this.root = root;
    this.version0Length = version0Length;
    this.currentLength = currentLength;
  }

  static @NotNull ModificationTreeImpl initial(int length) {
    Node root = length == 0
                ? null
                : new Node(
                  0,          // start in version 0
                  length,     // end in version 0
                  0,          // local delta
                  null,
                  null
                );

    return new ModificationTreeImpl(root, length, length);
  }

  /// One persistent mapping node.
  ///
  /// The node represents one live run of original text:
  ///
  /// [start0, end0)
  ///
  /// in version-0 coordinates.
  ///
  /// Its current-version range is:
  ///
  /// [start0 + effectiveDelta, end0 + effectiveDelta)
  ///
  /// where:
  ///
  /// effectiveDelta = sum(delta from root to this node)
  ///
  /// Deleted original ranges are absent from the tree.
  /// Inserted current text is represented as gaps between transformed nodes.
  private record Node(
    int start0,
    int end0,
    int delta,
    Node left,
    Node right,
    int height
  ) {
    Node(int start0, int end0, int delta, Node left, Node right) {
      this(
        start0,
        end0,
        delta,
        left,
        right,
        1 + Math.max(height(left), height(right))
      );
    }

    Node {
      if (start0 >= end0) {
        throw new IllegalArgumentException(
          "Invalid live run: [" + start0 + ", " + end0 + ")"
        );
      }

      int expectedHeight = 1 + Math.max(height(left), height(right));
      if (height != expectedHeight) {
        throw new IllegalArgumentException(
          "Invalid height: " + height + ", expected " + expectedHeight
        );
      }
    }

    int length0() {
      return end0 - start0;
    }

    int currentStart(int accumulatedDeltaBeforeThisNode) {
      int effectiveDelta = accumulatedDeltaBeforeThisNode + delta;
      return start0 + effectiveDelta;
    }

    int currentEnd(int accumulatedDeltaBeforeThisNode) {
      int effectiveDelta = accumulatedDeltaBeforeThisNode + delta;
      return end0 + effectiveDelta;
    }

    Node withDelta(int newDelta) {
      if (delta == newDelta) {
        return this;
      }
      return new Node(start0, end0, newDelta, left, right);
    }

    Node addDelta(int deltaDiff) {
      if (deltaDiff == 0) {
        return this;
      }
      return new Node(start0, end0, delta + deltaDiff, left, right);
    }

    Node withChildren(Node newLeft, Node newRight) {
      if (left == newLeft && right == newRight) {
        return this;
      }
      return new Node(start0, end0, delta, newLeft, newRight);
    }

    Node withRange(int newStart0, int newEnd0) {
      if (start0 == newStart0 && end0 == newEnd0) {
        return this;
      }
      return new Node(newStart0, newEnd0, delta, left, right);
    }

    static int height(@Nullable Node node) {
      return node == null ? 0 : node.height;
    }
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

    // If offsetInCurrent is inside inserted text, it is not contained by any
    // live original run. In that case we return the version-0 boundary after
    // the inserted gap, i.e. right-biased mapping.
    //
    // Example:
    //
    //   version0:  "hello world"
    //   current:   "hello beautiful world"
    //
    // Live runs:
    //   [0,6)  delta 0       -> current [0,6)
    //   [6,11) delta +10     -> current [16,21)
    //
    // Current offsets [6,16) are inserted text.
    // toVersion0Offset(10) returns 6.
    int successorVersion0Start = version0Length;
    boolean hasSuccessor = false;

    while (node != null) {
      accumulatedDelta += node.delta;

      int currentStart = node.start0 + accumulatedDelta;
      int currentEnd = node.end0 + accumulatedDelta;

      if (offsetInCurrent < currentStart) {
        // Target is before this node's current range.
        // This node is the best "next original run" candidate so far.
        successorVersion0Start = node.start0;
        hasSuccessor = true;
        node = node.left;
      }
      else if (offsetInCurrent >= currentEnd) {
        // Target is after this node's current range.
        node = node.right;
      }
      else {
        // Target lies inside surviving original text.
        return offsetInCurrent - accumulatedDelta;
      }
    }

    // Not found => offset is inside inserted current text, or EOF.
    //
    // Right-biased result:
    //   - inside inserted gap: return next live run's version-0 start
    //   - after last live run / EOF: return version0Length
    return hasSuccessor ? successorVersion0Start : version0Length;
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

    // If offsetInVersion0 is inside deleted original text, it will not be
    // contained by any live node. In that case we return the current start of
    // the next live node, i.e. right-biased mapping.
    //
    // Example:
    //
    //   version0:  abcdef
    //   current:   abef
    //
    // Live runs:
    //   [0,2) delta 0
    //   [4,6) delta -2
    //
    // toCurrentOffset(3) should return 2, the current start of original [4,6).
    int successorCurrentStart = currentLength;
    boolean hasSuccessor = false;

    while (node != null) {
      accumulatedDelta += node.delta;

      if (offsetInVersion0 < node.start0) {
        // Target is before this live run in version-0 space.
        // This node is the best successor seen so far.
        successorCurrentStart = node.start0 + accumulatedDelta;
        hasSuccessor = true;
        node = node.left;
      }
      else if (offsetInVersion0 >= node.end0) {
        // Target is after this live run.
        node = node.right;
      }
      else {
        // Target lies inside surviving original text.
        return offsetInVersion0 + accumulatedDelta;
      }
    }

    // Not found => the offset is inside deleted original text, or it is the EOF
    // boundary. Right-biased result is the next live run's current start, or
    // currentLength if there is no next live run.
    return hasSuccessor ? successorCurrentStart : currentLength;
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

    // Find the version-0 boundary before which the inserted text should appear.
    //
    // If offsetInCurrent is inside surviving original text, this returns the
    // corresponding original offset.
    //
    // If offsetInCurrent is inside already-inserted text, this returns the
    // version-0 boundary after that inserted gap.
    int boundary0 = toVersion0Offset(offsetInCurrent);

    // Split live original runs at boundary0.
    //
    // left:  all live runs strictly before boundary0
    // right: all live runs starting at or after boundary0
    Split split = splitByVersion0(root, boundary0);

    // Inserting current text before boundary0 shifts all surviving original text
    // after boundary0 by +length.
    //
    // The inserted text itself is not represented as a node. It is represented
    // as a current-coordinate gap between transformed live runs.
    Node shiftedRight = addDelta(split.right(), length);

    Node newRoot = concat(split.left(), shiftedRight);

    return new ModificationTreeImpl(
      newRoot,
      version0Length,
      currentLength + length
    );
  }

  private record Split(Node left, Node right) {
  }

  private record RemoveMax(Node rest, Node max) {
  }

  /// Adds delta to the whole subtree.
  ///
  /// Since `node.delta` is path-summed, increasing the root node's delta shifts
  /// the entire subtree.
  private static Node addDelta(Node node, int delta) {
    if (node == null || delta == 0) {
      return node;
    }

    return node.addDelta(delta);
  }

  /// Creates a node from standalone children.
  ///
  /// Important:
  ///
  /// The children passed to this method are standalone roots, meaning their delta
  /// already represents their effective shift without this parent.
  ///
  /// But once attached under this parent, they will inherit parent.delta.
  /// Therefore we subtract parent.delta from the child root delta.
  private static @NotNull Node makeNode(
    int start0,
    int end0,
    int delta,
    Node leftStandalone,
    Node rightStandalone) {
    return new Node(
      start0,
      end0,
      delta,
      addDelta(leftStandalone, -delta),
      addDelta(rightStandalone, -delta)
    );
  }

  private static @NotNull Node nodeAlone(@NotNull Node node) {
    return new Node(
      node.start0(),
      node.end0(),
      node.delta(),
      null,
      null
    );
  }

  /// Returns `node.left` as a standalone subtree.
  private static Node standaloneLeft(@NotNull Node node) {
    return addDelta(node.left(), node.delta());
  }

  /// Returns `node.right` as a standalone subtree.
  private static Node standaloneRight(@NotNull Node node) {
    return addDelta(node.right(), node.delta());
  }

  /// Splits the tree by a version-0 boundary.
  ///
  /// Result:
  ///
  /// left  contains all live runs before boundary0
  /// right contains all live runs at or after boundary0
  ///
  /// If boundary0 falls inside a live run, that run is split.
  private static @NotNull Split splitByVersion0(Node node, int boundary0) {
    if (node == null) {
      return new Split(null, null);
    }

    if (boundary0 <= node.start0()) {
      Split splitLeft = splitByVersion0(standaloneLeft(node), boundary0);

      Node nodeAndRight = concat(
        nodeAlone(node),
        standaloneRight(node)
      );

      return new Split(
        splitLeft.left(),
        concat(splitLeft.right(), nodeAndRight)
      );
    }

    if (boundary0 >= node.end0()) {
      Split splitRight = splitByVersion0(standaloneRight(node), boundary0);

      Node leftAndNode = concat(
        standaloneLeft(node),
        nodeAlone(node)
      );

      return new Split(
        concat(leftAndNode, splitRight.left()),
        splitRight.right()
      );
    }

    // boundary0 is strictly inside this live run:
    //
    //     [start0, end0)
    //
    // becomes:
    //
    //     [start0, boundary0)
    //     [boundary0, end0)
    Node leftRun = new Node(
      node.start0(),
      boundary0,
      node.delta(),
      null,
      null
    );

    Node rightRun = new Node(
      boundary0,
      node.end0(),
      node.delta(),
      null,
      null
    );

    return new Split(
      concat(standaloneLeft(node), leftRun),
      concat(rightRun, standaloneRight(node))
    );
  }

  /// Concatenates two trees where every run in left is before every run in right.
  private static Node concat(Node left, Node right) {
    if (left == null) {
      return right;
    }

    if (right == null) {
      return left;
    }

    int leftHeight = Node.height(left);
    int rightHeight = Node.height(right);

    if (leftHeight > rightHeight + 1) {
      Node newRight = concat(standaloneRight(left), right);

      return balance(makeNode(
        left.start0(),
        left.end0(),
        left.delta(),
        standaloneLeft(left),
        newRight
      ));
    }

    if (rightHeight > leftHeight + 1) {
      Node newLeft = concat(left, standaloneLeft(right));

      return balance(makeNode(
        right.start0(),
        right.end0(),
        right.delta(),
        newLeft,
        standaloneRight(right)
      ));
    }

    RemoveMax removed = removeMax(left);

    return balance(makeNode(
      removed.max().start0(),
      removed.max().end0(),
      removed.max().delta(),
      removed.rest(),
      right
    ));
  }

  private static @NotNull RemoveMax removeMax(@NotNull Node node) {
    Node right = standaloneRight(node);

    if (right == null) {
      return new RemoveMax(standaloneLeft(node), nodeAlone(node));
    }

    RemoveMax removed = removeMax(right);

    Node rebuilt = balance(makeNode(
      node.start0(),
      node.end0(),
      node.delta(),
      standaloneLeft(node),
      removed.rest()
    ));

    return new RemoveMax(rebuilt, removed.max());
  }

  private static @NotNull Node balance(@NotNull Node node) {
    int balance = Node.height(node.left()) - Node.height(node.right());

    if (balance > 1) {
      Node left = standaloneLeft(node);

      if (Node.height(left.right()) > Node.height(left.left())) {
        left = rotateLeft(left);
      }

      Node rebuilt = makeNode(
        node.start0(),
        node.end0(),
        node.delta(),
        left,
        standaloneRight(node)
      );

      return rotateRight(rebuilt);
    }

    if (balance < -1) {
      Node right = standaloneRight(node);

      if (Node.height(right.left()) > Node.height(right.right())) {
        right = rotateRight(right);
      }

      Node rebuilt = makeNode(
        node.start0(),
        node.end0(),
        node.delta(),
        standaloneLeft(node),
        right
      );

      return rotateLeft(rebuilt);
    }

    return node;
  }

  private static @NotNull Node rotateRight(@NotNull Node node) {
    Node left = standaloneLeft(node);

    Node a = standaloneLeft(left);
    Node b = standaloneRight(left);
    Node c = standaloneRight(node);

    Node newRight = makeNode(
      node.start0(),
      node.end0(),
      node.delta(),
      b,
      c
    );

    return makeNode(
      left.start0(),
      left.end0(),
      left.delta(),
      a,
      newRight
    );
  }

  private static @NotNull Node rotateLeft(@NotNull Node node) {
    Node right = standaloneRight(node);

    Node a = standaloneLeft(node);
    Node b = standaloneLeft(right);
    Node c = standaloneRight(right);

    Node newLeft = makeNode(
      node.start0(),
      node.end0(),
      node.delta(),
      a,
      b
    );

    return makeNode(
      right.start0(),
      right.end0(),
      right.delta(),
      newLeft,
      c
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

    /*
     * Convert current-version deletion endpoints to version-0 boundaries.
     *
     * If an endpoint lies inside inserted text, toVersion0Offset() returns the
     * right-biased original boundary after that inserted gap.
     */
    int start0 = toVersion0Offset(startInCurrent);
    int end0 = toVersion0Offset(endInCurrent);

    /*
     * Split by original coordinates:
     *
     *   left   = live original text before deleted range
     *   middle = live original text deleted by this operation
     *   right  = live original text after deleted range
     *
     * Inserted current text inside [startInCurrent, endInCurrent) is not stored
     * as nodes, so deleting it is represented only by shifting the suffix left.
     */
    Split first = splitByVersion0(root, start0);
    Split second = splitByVersion0(first.right(), end0);

    Node left = first.left();

    // second.left() is discarded: it is original text deleted from this version.
    Node right = second.right();

    /*
     * Everything after the deleted current range moves left.
     */
    Node shiftedRight = addDelta(right, -deletedLength);

    /*
     * concatCoalescing() is not required for correctness, but it keeps the tree
     * compact when deleting an inserted gap makes two adjacent original runs
     * have the same effective delta again.
     */
    Node newRoot = concatCoalescing(left, shiftedRight);

    return new ModificationTreeImpl(
      newRoot,
      version0Length,
      Math.subtractExact(currentLength, deletedLength)
    );
  }

  private record RemoveMin(Node min, Node rest) {
  }

  /**
   * Concatenates two ordered trees and merges the boundary runs when possible.
   * <p>
   * This preserves the invariant that adjacent live original runs with the same
   * effective delta should be represented as one run.
   */
  private static Node concatCoalescing(Node left, Node right) {
    if (left == null) {
      return right;
    }

    if (right == null) {
      return left;
    }

    RemoveMax leftRemoved = removeMax(left);
    RemoveMin rightRemoved = removeMin(right);

    Node leftMax = leftRemoved.max();
    Node rightMin = rightRemoved.min();

    if (canMerge(leftMax, rightMin)) {
      Node merged = new Node(
        leftMax.start0(),
        rightMin.end0(),
        leftMax.delta(),
        null,
        null
      );

      return concat(
        concat(leftRemoved.rest(), merged),
        rightRemoved.rest()
      );
    }

    return concat(
      concat(leftRemoved.rest(), leftMax),
      concat(rightMin, rightRemoved.rest())
    );
  }

  private static boolean canMerge(@NotNull Node left, @NotNull Node right) {
    return left.end0() == right.start0()
           && left.delta() == right.delta();
  }

  private static @NotNull RemoveMin removeMin(Node node) {
    Node left = standaloneLeft(node);

    if (left == null) {
      return new RemoveMin(
        nodeAlone(node),
        standaloneRight(node)
      );
    }

    RemoveMin removed = removeMin(left);

    Node rebuilt = balance(makeNode(
      node.start0(),
      node.end0(),
      node.delta(),
      removed.rest(),
      standaloneRight(node)
    ));

    return new RemoveMin(removed.min(), rebuilt);
  }

  @Override
  public void checkInvariants() {
    if (version0Length < 0) {
      throw new IllegalStateException("version0Length < 0: " + version0Length);
    }

    if (currentLength < 0) {
      throw new IllegalStateException("currentLength < 0: " + currentLength);
    }

    if (root == null) {
      // No surviving original text.
      // This is valid: the current document may still contain only inserted text.
      checkBoundaryMappings();
      return;
    }

    CheckInfo info = checkNode(root, 0);

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

    checkBoundaryMappings();
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

  private CheckInfo checkNode(Node node, int accumulatedDeltaBeforeNode) {
    if (node == null) {
      throw new IllegalStateException("checkNode called with null");
    }

    int effectiveDelta = Math.addExact(accumulatedDeltaBeforeNode, node.delta());

    checkNodeOwnFields(node, effectiveDelta);
    checkNodeHeightAndBalance(node);

    int nodeCurrentStart = Math.addExact(node.start0(), effectiveDelta);
    int nodeCurrentEnd = Math.addExact(node.end0(), effectiveDelta);

    CheckInfo leftInfo = null;
    CheckInfo rightInfo = null;

    if (node.left() != null) {
      leftInfo = checkNode(node.left(), effectiveDelta);

      if (leftInfo.end0() > node.start0()) {
        throw new IllegalStateException(
          "left subtree overlaps node: leftEnd=" +
          leftInfo.end0() + ", nodeStart=" + node.start0()
        );
      }

      if (leftInfo.currentEnd() > nodeCurrentStart) {
        throw new IllegalStateException(
          "left subtree overlaps node in current coordinates: leftCurrentEnd=" +
          leftInfo.currentEnd() + ", nodeCurrentStart=" + nodeCurrentStart
        );
      }

      if (
        leftInfo.end0() == node.start0() &&
        leftInfo.lastEffectiveDelta() == effectiveDelta
      ) {
        throw new IllegalStateException(
          "mergeable adjacent runs remain between left subtree and node at version0 offset " +
          node.start0()
        );
      }
    }

    if (node.right() != null) {
      rightInfo = checkNode(node.right(), effectiveDelta);

      if (node.end0() > rightInfo.start0()) {
        throw new IllegalStateException(
          "node overlaps right subtree: nodeEnd=" +
          node.end0() + ", rightStart=" + rightInfo.start0()
        );
      }

      if (nodeCurrentEnd > rightInfo.currentStart()) {
        throw new IllegalStateException(
          "node overlaps right subtree in current coordinates: nodeCurrentEnd=" +
          nodeCurrentEnd + ", rightCurrentStart=" + rightInfo.currentStart()
        );
      }

      if (
        node.end0() == rightInfo.start0() &&
        effectiveDelta == rightInfo.firstEffectiveDelta()
      ) {
        throw new IllegalStateException(
          "mergeable adjacent runs remain between node and right subtree at version0 offset " +
          node.end0()
        );
      }
    }

    int subtreeStart0 = leftInfo == null ? node.start0() : leftInfo.start0();
    int subtreeEnd0 = rightInfo == null ? node.end0() : rightInfo.end0();

    int subtreeCurrentStart = leftInfo == null
                              ? nodeCurrentStart
                              : leftInfo.currentStart();

    int subtreeCurrentEnd = rightInfo == null
                            ? nodeCurrentEnd
                            : rightInfo.currentEnd();

    int runCount = 1
                   + (leftInfo == null ? 0 : leftInfo.runCount())
                   + (rightInfo == null ? 0 : rightInfo.runCount());

    int firstEffectiveDelta = leftInfo == null
                              ? effectiveDelta
                              : leftInfo.firstEffectiveDelta();

    int lastEffectiveDelta = rightInfo == null
                             ? effectiveDelta
                             : rightInfo.lastEffectiveDelta();

    return new CheckInfo(
      subtreeStart0,
      subtreeEnd0,
      subtreeCurrentStart,
      subtreeCurrentEnd,
      node.height(),
      runCount,
      firstEffectiveDelta,
      lastEffectiveDelta
    );
  }

  private void checkNodeOwnFields(Node node, int effectiveDelta) {
    if (node.start0() < 0) {
      throw new IllegalStateException("node.start0 < 0: " + node.start0());
    }

    if (node.end0() > version0Length) {
      throw new IllegalStateException(
        "node.end0 exceeds version0Length: " +
        node.end0() + " > " + version0Length
      );
    }

    if (node.start0() >= node.end0()) {
      throw new IllegalStateException(
        "invalid node range: [" + node.start0() + ", " + node.end0() + ")"
      );
    }

    int currentStart = Math.addExact(node.start0(), effectiveDelta);
    int currentEnd = Math.addExact(node.end0(), effectiveDelta);

    if (currentStart < 0) {
      throw new IllegalStateException(
        "node currentStart < 0: " + currentStart +
        ", node=[" + node.start0() + ", " + node.end0() + ")" +
        ", effectiveDelta=" + effectiveDelta
      );
    }

    if (currentEnd > currentLength) {
      throw new IllegalStateException(
        "node currentEnd exceeds currentLength: " +
        currentEnd + " > " + currentLength +
        ", node=[" + node.start0() + ", " + node.end0() + ")" +
        ", effectiveDelta=" + effectiveDelta
      );
    }

    if (currentStart >= currentEnd) {
      throw new IllegalStateException(
        "invalid current range: [" + currentStart + ", " + currentEnd + ")"
      );
    }
  }

  private static void checkNodeHeightAndBalance(Node node) {
    int leftHeight = Node.height(node.left());
    int rightHeight = Node.height(node.right());

    int expectedHeight = 1 + Math.max(leftHeight, rightHeight);

    if (node.height() != expectedHeight) {
      throw new IllegalStateException(
        "height mismatch for node [" +
        node.start0() + ", " + node.end0() + "): " +
        node.height() + " != " + expectedHeight
      );
    }

    int balance = leftHeight - rightHeight;

    if (balance < -1 || balance > 1) {
      throw new IllegalStateException(
        "AVL balance violation for node [" +
        node.start0() + ", " + node.end0() + "): " +
        "leftHeight=" + leftHeight +
        ", rightHeight=" + rightHeight
      );
    }
  }

  private void checkBoundaryMappings() {
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
}
