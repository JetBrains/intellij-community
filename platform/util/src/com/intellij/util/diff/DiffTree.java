// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.diff;

import com.intellij.openapi.util.Ref;
import com.intellij.util.ThreeState;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A class for computing difference between two trees and reporting the detected changes via {@link DiffTreeChangeBuilder}
 */
public final class DiffTree<OldNode, NewNode> {
  private static final int CHANGE_PARENT_VERSUS_CHILDREN_THRESHOLD = 20;
  private static final int MIN_CHUNKED_LENGTH = 64;
  private static final int CHUNK_LENGTH = 4096;

  private final FlyweightCapableTreeStructure<OldNode> myOldTree;
  private final FlyweightCapableTreeStructure<NewNode> myNewTree;
  private final ShallowNodeComparator<? super OldNode, ? super NewNode> myComparator;
  private final List<Ref<OldNode[]>> myOldChildrenLists = new ArrayList<>();
  private final List<Ref<NewNode[]>> myNewChildrenLists = new ArrayList<>();
  private final CharSequence myOldText;
  private final CharSequence myNewText;
  private final int myOldTreeStart;
  private final int myNewTreeStart;
  private char[] myOldChunk;
  private char[] myNewChunk;

  /**
   * The old text from {@code myMatchedOldStart} to {@code myMatchedOldEnd} is equal to the new text that starts {@code myMatchedShift} chars later.
   * A node inside this range at the same shift has the same text, so the drill-down does not compare its text again.
   */
  private int myMatchedOldStart;
  private int myMatchedOldEnd;
  private int myMatchedShift;

  /**
   * The first {@code myEqualPrefix} chars of the old root text and of the new root text are equal.
   * The diff extends this prefix only when a node asks for it, and then by at least one chunk.
   * So it reads each char at most once, and at most one chunk more than the nodes ask for.
   * {@code myPrefixEnded} is true when the compare found a mismatch or the end of a text.
   */
  private int myEqualPrefix;
  private boolean myPrefixEnded;

  /** The last {@code myEqualSuffix} chars of the old root text and of the new root text are equal. */
  private int myEqualSuffix;
  private boolean mySuffixEnded;
  private final int myOldRootLength;

  private DiffTree(@NotNull FlyweightCapableTreeStructure<OldNode> oldTree,
                   @NotNull FlyweightCapableTreeStructure<NewNode> newTree,
                   @NotNull ShallowNodeComparator<? super OldNode, ? super NewNode> comparator,
                   @NotNull CharSequence oldText) {
    myOldTree = oldTree;
    myNewTree = newTree;
    myComparator = comparator;
    myOldText = oldText;
    myOldTreeStart = oldTree.getStartOffset(oldTree.getRoot());
    myNewText = newTree.toString(newTree.getRoot());
    myNewTreeStart = newTree.getStartOffset(newTree.getRoot());
    myOldRootLength = oldTree.getEndOffset(oldTree.getRoot()) - myOldTreeStart;
    assert oldText.length() == myOldRootLength : "old text length " + oldText.length() + " != old root length " + myOldRootLength;
  }

  /**
   * Computes the differences between two trees and reports the changes using {@link DiffTreeChangeBuilder} consumer.
   *
   * @param oldTree the tree representing the old state
   * @param newTree the tree representing the new state
   * @param comparator the comparator used to determine equality between nodes
   * @param consumer the consumer to report changes detected during the diff operation
   * @param oldText the text of the old root, from its start offset to its end offset
   */
  public static <OldNode, NewNode> void diff(@NotNull FlyweightCapableTreeStructure<OldNode> oldTree,
                                             @NotNull FlyweightCapableTreeStructure<NewNode> newTree,
                                             @NotNull ShallowNodeComparator<? super OldNode, ? super NewNode> comparator,
                                             @NotNull DiffTreeChangeBuilder<? super OldNode, ? super NewNode> consumer,
                                             @NotNull CharSequence oldText) {
    DiffTree<OldNode, NewNode> tree = new DiffTree<>(oldTree, newTree, comparator, oldText);
    tree.build(oldTree.getRoot(), newTree.getRoot(), 0, consumer);
  }

  private enum CompareResult {
    /** 100% equal */
    EQUAL,

    /** element types are equal, but elements are composite */
    DRILL_DOWN_NEEDED,

    /** only element types are equal */
    TYPE_ONLY,

    /** 100% different */
    NOT_EQUAL;

    /** The constants go from the best match to the worst. */
    boolean isBetterThan(@NotNull CompareResult other) {
      return ordinal() < other.ordinal();
    }
  }

  private static @NotNull <OldNode, NewNode> DiffTreeChangeBuilder<OldNode, NewNode> emptyConsumer() {
    //noinspection unchecked
    return (DiffTreeChangeBuilder<OldNode, NewNode>)EMPTY_CONSUMER;
  }

  private static final DiffTreeChangeBuilder<?, ?> EMPTY_CONSUMER = new DiffTreeChangeBuilder<Object, Object>() {
    @Override
    public void nodeReplaced(@NotNull Object oldChild, @NotNull Object newChild) { }

    @Override
    public void nodeDeleted(@NotNull Object oldParent, @NotNull Object oldNode) { }

    @Override
    public void nodeInserted(@NotNull Object oldParent, @NotNull Object newNode, int pos) { }
  };

  private @NotNull CompareResult build(@NotNull OldNode oldNode,
                                       @NotNull NewNode newNode,
                                       int level,
                                       @NotNull DiffTreeChangeBuilder<? super OldNode, ? super NewNode> consumer) {
    if (level == myNewChildrenLists.size()) {
      myNewChildrenLists.add(new Ref<>());
      myOldChildrenLists.add(new Ref<>());
    }

    Ref<OldNode[]> oldChildrenR = myOldChildrenLists.get(level);
    int oldChildrenSize = myOldTree.getChildren(oldNode, oldChildrenR);
    OldNode[] oldChildren = oldChildrenR.get(); // null if there are no children

    Ref<NewNode[]> newChildrenR = myNewChildrenLists.get(level);
    int newChildrenSize = myNewTree.getChildren(newNode, newChildrenR);
    NewNode[] newChildren = newChildrenR.get(); // null if there are no children

    CompareResult result;
    if (Math.abs(oldChildrenSize - newChildrenSize) > CHANGE_PARENT_VERSUS_CHILDREN_THRESHOLD) {
      consumer.nodeReplaced(oldNode, newNode);
      result = CompareResult.NOT_EQUAL;
    }
    else if (oldChildrenSize == 0 && newChildrenSize == 0) {
      if (!myComparator.typesEqual(oldNode, newNode) || !myComparator.hashCodesEqual(oldNode, newNode)) {
        consumer.nodeReplaced(oldNode, newNode);
        result = CompareResult.NOT_EQUAL;
      }
      else {
        result = CompareResult.EQUAL;
      }
    }
    else {
      int minSize = Math.min(oldChildrenSize, newChildrenSize);
      int suffixLength = match(oldNode, oldChildren, oldChildrenSize - 1, newNode, newChildren, newChildrenSize - 1, level, -1, minSize);
      // for equal size old and new children we have to compare one element less because it was already checked in (unsuccessful) suffix match
      int maxPrefixLength = minSize - suffixLength - (oldChildrenSize == newChildrenSize && suffixLength < minSize ? 1 : 0);
      int prefixLength = match(oldNode, oldChildren, 0, newNode, newChildren, 0, level, 1, maxPrefixLength);

      if (oldChildrenSize == newChildrenSize && suffixLength + prefixLength == oldChildrenSize) {
        result = CompareResult.EQUAL;
      }
      else if (consumer == emptyConsumer()) {
        result = CompareResult.NOT_EQUAL;
      }
      else {
        int oldIndex = prefixLength;
        int newIndex = prefixLength;
        while (oldIndex < oldChildrenSize - suffixLength || newIndex < newChildrenSize - suffixLength) {
          ThreeElementMatchResult vicinityMatch = matchNext3Children(oldChildren, newChildren, oldIndex, newIndex,
                                                                     oldChildrenSize - suffixLength,
                                                                     newChildrenSize - suffixLength);
          if (vicinityMatch.hasStartMatch()) {
            if (vicinityMatch == ThreeElementMatchResult.DRILL_DOWN_START_MATCH) {
              build(oldChildren[oldIndex], newChildren[newIndex], level + 1, consumer);
            }
            else if (vicinityMatch == ThreeElementMatchResult.REPLACE_START) {
              consumer.nodeReplaced(oldChildren[oldIndex], newChildren[newIndex]);
            }
            oldIndex++;
            newIndex++;
          }
          else if (vicinityMatch != ThreeElementMatchResult.NO_MATCH) {
            for (int i = vicinityMatch.skipOldCount() - 1; i >= 0; i--) {
              consumer.nodeDeleted(oldNode, oldChildren[oldIndex]);
              oldIndex++;
            }
            for (int i = vicinityMatch.skipNewCount() - 1; i >= 0; i--) {
              consumer.nodeInserted(oldNode, newChildren[newIndex], newIndex);
              newIndex++;
            }
          }
          else {
            // last resort: maybe the last elements are more similar?
            int suffixMatch = matchLastChildren(level, consumer,
                                                oldChildrenSize - suffixLength, oldChildren, oldIndex,
                                                newChildrenSize - suffixLength, newChildren, newIndex);
            if (suffixMatch > 0) {
              suffixLength += suffixMatch;
            }
            else {
              consumer.nodeReplaced(oldChildren[oldIndex], newChildren[newIndex]);
              oldIndex++;
              newIndex++;
            }
          }
        }
        result = CompareResult.NOT_EQUAL;
      }
    }
    myOldTree.disposeChildren(oldChildren, oldChildrenSize);
    myNewTree.disposeChildren(newChildren, newChildrenSize);
    return result;
  }

  private ThreeElementMatchResult matchNext3Children(OldNode[] oldChildren,
                                                     NewNode[] newChildren,
                                                     int oldIndex,
                                                     int newIndex,
                                                     int oldLimit,
                                                     int newLimit) {
    if (oldIndex >= oldLimit) return ThreeElementMatchResult.SKIP_NEW_1;
    if (newIndex >= newLimit) return ThreeElementMatchResult.SKIP_OLD_1;

    OldNode oldChild1 = oldChildren[oldIndex];
    NewNode newChild1 = newChildren[newIndex];

    CompareResult c11 = looksEqual(oldChild1, newChild1);
    if (c11 == CompareResult.EQUAL) return ThreeElementMatchResult.FULL_START_MATCH;
    if (c11 == CompareResult.DRILL_DOWN_NEEDED) return ThreeElementMatchResult.DRILL_DOWN_START_MATCH;

    OldNode oldChild2 = oldIndex < oldLimit - 1 ? oldChildren[oldIndex + 1] : null;
    NewNode newChild2 = newIndex < newLimit - 1 ? newChildren[newIndex + 1] : null;

    CompareResult c12 = looksEqual(oldChild1, newChild2);
    if (c12 == CompareResult.EQUAL || c12 == CompareResult.DRILL_DOWN_NEEDED) return ThreeElementMatchResult.SKIP_NEW_1;

    CompareResult c21 = looksEqual(oldChild2, newChild1);
    if (c21 == CompareResult.EQUAL || c21 == CompareResult.DRILL_DOWN_NEEDED) return ThreeElementMatchResult.SKIP_OLD_1;

    if (c11 == CompareResult.TYPE_ONLY) return ThreeElementMatchResult.REPLACE_START;

    if (c12 == CompareResult.TYPE_ONLY) return ThreeElementMatchResult.SKIP_NEW_1;
    if (c21 == CompareResult.TYPE_ONLY) return ThreeElementMatchResult.SKIP_OLD_1;

    // check whether two children are inserted/deleted
    // (which frequently is a case when e.g. a PsiMethod inserted, the trailing PsiWhiteSpace is appended too)
    OldNode oldChild3 = oldIndex < oldLimit - 2 ? oldChildren[oldIndex + 2] : null;
    NewNode newChild3 = newIndex < newLimit - 2 ? newChildren[newIndex + 2] : null;

    // a skip of two children needs a better match than the match of the third children
    CompareResult c13 = looksEqual(oldChild1, newChild3);
    if (c13 != CompareResult.NOT_EQUAL && c13.isBetterThan(looksEqual(oldChild3, newChild3))) return ThreeElementMatchResult.SKIP_NEW_2;
    CompareResult c31 = looksEqual(oldChild3, newChild1);
    if (c31 != CompareResult.NOT_EQUAL && c31.isBetterThan(looksEqual(oldChild3, newChild3))) return ThreeElementMatchResult.SKIP_OLD_2;

    return ThreeElementMatchResult.NO_MATCH;
  }

  /** Represents the result of matching among 3 next node children in before and after tree */
  private enum ThreeElementMatchResult {
    /** first children match completely */
    FULL_START_MATCH,

    /** first children match well, PSI instance should be preserved */
    DRILL_DOWN_START_MATCH,

    /** PSI instance should be replaced for first children */
    REPLACE_START,

    /** first 1 or 2 "new" children don't match, report them as inserted and try matching after them */
    SKIP_NEW_1, SKIP_NEW_2,

    /** first 1 or 2 "old" children don't match, report them as deleted and try matching after them */
    SKIP_OLD_1, SKIP_OLD_2,

    /** nothing in the 3-children scope matches both the "old" and the "old" first child */
    NO_MATCH;

    int skipNewCount() { return this == SKIP_NEW_1 ? 1 : this == SKIP_NEW_2 ? 2 : 0; }

    int skipOldCount() { return this == SKIP_OLD_1 ? 1 : this == SKIP_OLD_2 ? 2 : 0; }

    boolean hasStartMatch() { return this == FULL_START_MATCH || this == DRILL_DOWN_START_MATCH || this == REPLACE_START; }
  }

  private int matchLastChildren(int level,
                                @NotNull DiffTreeChangeBuilder<? super OldNode, ? super NewNode> consumer,
                                int oldChildrenLimit,
                                OldNode[] oldChildren,
                                int oldIndex,
                                int newChildrenLimit,
                                NewNode[] newChildren,
                                int newIndex) {
    int len = 0;
    while (oldIndex < oldChildrenLimit - len && newIndex < newChildrenLimit - len) {
      OldNode oldLastChild = oldChildren[oldChildrenLimit - len - 1];
      NewNode newLastChild = newChildren[newChildrenLimit - len - 1];
      CompareResult c = looksEqual(oldLastChild, newLastChild);
      if (c == CompareResult.NOT_EQUAL) break;

      if (c == CompareResult.DRILL_DOWN_NEEDED) {
        build(oldLastChild, newLastChild, level + 1, consumer);
      }
      else if (c == CompareResult.TYPE_ONLY) {
        consumer.nodeReplaced(oldLastChild, newLastChild);
      }
      len++;
    }
    return len;
  }

  /**
   * tries to match as many nodes as possible from the beginning (if step=1) of from the end (if step =-1)
   * returns number of nodes matched
   */
  private int match(@NotNull OldNode oldParent,
                    OldNode[] oldChildren,
                    int oldIndex,
                    @NotNull NewNode newParent,
                    NewNode[] newChildren,
                    int newIndex,
                    int level,
                    int step, // 1 if we go from the start to the end; -1 if we go from the end to the start
                    int maxLength) {
    int delta = 0;
    while (delta != maxLength * step) {
      OldNode oldChild = oldChildren[oldIndex + delta];
      NewNode newChild = newChildren[newIndex + delta];

      CompareResult c11 = looksEqual(oldChild, newChild);

      if (c11 == CompareResult.DRILL_DOWN_NEEDED) {
        if (step < 0 && isShiftedInEqualPrefix(oldParent, oldChild, newParent, newChild)) {
          // the prefix match keeps the child at the same offset
          break;
        }
        c11 = drillDownIfTextMatches(oldChild, newChild, level);
        assert c11 != CompareResult.DRILL_DOWN_NEEDED;
      }
      if (c11 != CompareResult.EQUAL) {
        break;
      }
      delta += step;
    }
    return delta * step;
  }

  /**
   * Returns true if the old and the new child have different offsets, the old and the new parent start at the same offset,
   * and the old or the new child ends in the equal prefix of the root texts.
   * In a parent with a shift, the prefix match cannot keep a child at its offset.
   */
  private boolean isShiftedInEqualPrefix(@NotNull OldNode oldParent,
                                         @NotNull OldNode oldChild,
                                         @NotNull NewNode newParent,
                                         @NotNull NewNode newChild) {
    int oldStart = myOldTree.getStartOffset(oldChild) - myOldTreeStart;
    int newStart = myNewTree.getStartOffset(newChild) - myNewTreeStart;
    if (oldStart == newStart) return false;
    if (myOldTree.getStartOffset(oldParent) - myOldTreeStart != myNewTree.getStartOffset(newParent) - myNewTreeStart) return false;
    int oldEnd = myOldTree.getEndOffset(oldChild) - myOldTreeStart;
    int newEnd = myNewTree.getEndOffset(newChild) - myNewTreeStart;
    return isInEqualPrefix(Math.min(oldEnd, newEnd));
  }

  /**
   * Compares the children of two nodes only if the nodes have the same text.
   * When the texts match, the comparison goes all the way down.
   */
  private @NotNull CompareResult drillDownIfTextMatches(@NotNull OldNode oldChild, @NotNull NewNode newChild, int level) {
    int oldStart = myOldTree.getStartOffset(oldChild) - myOldTreeStart;
    int oldEnd = myOldTree.getEndOffset(oldChild) - myOldTreeStart;
    int newStart = myNewTree.getStartOffset(newChild) - myNewTreeStart;
    int newEnd = myNewTree.getEndOffset(newChild) - myNewTreeStart;
    if (oldEnd - oldStart != newEnd - newStart) return CompareResult.NOT_EQUAL;
    if (oldStart >= myMatchedOldStart && oldEnd <= myMatchedOldEnd && newStart - oldStart == myMatchedShift ||
        isInEqualPrefix(oldStart, oldEnd, newStart) ||
        isInEqualSuffix(oldStart, oldEnd, newStart)) {
      return build(oldChild, newChild, level + 1, emptyConsumer());
    }
    if (!regionMatches(oldStart, newStart, oldEnd - oldStart)) return CompareResult.NOT_EQUAL;

    int outerStart = myMatchedOldStart;
    int outerEnd = myMatchedOldEnd;
    int outerShift = myMatchedShift;
    myMatchedOldStart = oldStart;
    myMatchedOldEnd = oldEnd;
    myMatchedShift = newStart - oldStart;
    try {
      return build(oldChild, newChild, level + 1, emptyConsumer());
    }
    finally {
      myMatchedOldStart = outerStart;
      myMatchedOldEnd = outerEnd;
      myMatchedShift = outerShift;
    }
  }

  /**
   * Returns true if the node from {@code oldStart} to {@code oldEnd} has no shift and is in the equal prefix of the root texts.
   * Extends the equal prefix past {@code oldEnd} if necessary.
   */
  private boolean isInEqualPrefix(int oldStart, int oldEnd, int newStart) {
    return newStart == oldStart && isInEqualPrefix(oldEnd);
  }

  /**
   * Returns true if the text up to {@code end} is in the equal prefix of the root texts.
   * Extends the equal prefix past {@code end} if necessary.
   */
  private boolean isInEqualPrefix(int end) {
    if (end > myEqualPrefix && !myPrefixEnded) {
      int textEnd = Math.min(myOldRootLength, myNewText.length());
      int limit = Math.min(Math.max(end, myEqualPrefix + CHUNK_LENGTH), textEnd);
      myEqualPrefix += matchingLength(myEqualPrefix, myEqualPrefix, limit - myEqualPrefix, 1);
      if (myEqualPrefix < limit || limit == textEnd) myPrefixEnded = true;
    }
    return end <= myEqualPrefix;
  }

  /**
   * Returns true if the node from {@code oldStart} to {@code oldEnd} has the shift of the root end and is in the equal suffix of the root texts.
   * Extends the equal suffix past {@code oldStart} if necessary.
   */
  private boolean isInEqualSuffix(int oldStart, int oldEnd, int newStart) {
    int shift = myNewText.length() - myOldRootLength;
    if (newStart - oldStart != shift) return false;
    int needed = myOldRootLength - oldStart;
    if (needed > myEqualSuffix && !mySuffixEnded) {
      int textEnd = Math.min(myOldRootLength, myNewText.length());
      int limit = Math.min(Math.max(needed, myEqualSuffix + CHUNK_LENGTH), textEnd);
      myEqualSuffix += matchingLength(myOldRootLength - myEqualSuffix, myNewText.length() - myEqualSuffix, limit - myEqualSuffix, -1);
      if (myEqualSuffix < limit || limit == textEnd) mySuffixEnded = true;
    }
    return needed <= myEqualSuffix;
  }

  /**
   * Returns how many chars are equal, from {@code oldOffset} and {@code newOffset} forward if {@code step} is 1,
   * or from the chars before {@code oldOffset} and {@code newOffset} backward if {@code step} is -1.
   * Compares at most {@code length} chars, in chunks.
   */
  private int matchingLength(int oldOffset, int newOffset, int length, int step) {
    ensureChunks();
    int matched = 0;
    while (matched < length) {
      int chunkLength = Math.min(CHUNK_LENGTH, length - matched);
      int oldChunkStart = step > 0 ? oldOffset + matched : oldOffset - matched - chunkLength;
      int newChunkStart = step > 0 ? newOffset + matched : newOffset - matched - chunkLength;
      CharArrayUtil.getChars(myOldText, myOldChunk, oldChunkStart, 0, chunkLength);
      CharArrayUtil.getChars(myNewText, myNewChunk, newChunkStart, 0, chunkLength);
      for (int i = 0; i < chunkLength; i++) {
        int index = step > 0 ? i : chunkLength - 1 - i;
        if (myOldChunk[index] != myNewChunk[index]) return matched + i;
      }
      matched += chunkLength;
    }
    return matched;
  }

  private void ensureChunks() {
    if (myOldChunk == null) {
      myOldChunk = new char[CHUNK_LENGTH];
      myNewChunk = new char[CHUNK_LENGTH];
    }
  }

  /**
   * Compares the old text at {@code oldStart} with the new text at {@code newStart}.
   * A long range is copied in chunks, because each char read of a rope text, such as a document text, walks the rope.
   */
  private boolean regionMatches(int oldStart, int newStart, int length) {
    if (length < MIN_CHUNKED_LENGTH) {
      return CharArrayUtil.regionMatches(myOldText, oldStart, oldStart + length, myNewText, newStart, newStart + length);
    }
    ensureChunks();
    for (int offset = 0; offset < length; offset += CHUNK_LENGTH) {
      int chunkLength = Math.min(CHUNK_LENGTH, length - offset);
      CharArrayUtil.getChars(myOldText, myOldChunk, oldStart + offset, 0, chunkLength);
      CharArrayUtil.getChars(myNewText, myNewChunk, newStart + offset, 0, chunkLength);
      for (int i = 0; i < chunkLength; i++) {
        if (myOldChunk[i] != myNewChunk[i]) return false;
      }
    }
    return true;
  }

  private @NotNull CompareResult looksEqual(@Nullable OldNode oldChild1,
                                            @Nullable NewNode newChild1) {
    if (oldChild1 == null || newChild1 == null || !myComparator.typesEqual(oldChild1, newChild1)) return CompareResult.NOT_EQUAL;
    ThreeState ret = myComparator.deepEqual(oldChild1, newChild1);
    if (ret == ThreeState.YES) return CompareResult.EQUAL;
    if (ret == ThreeState.UNSURE) return CompareResult.DRILL_DOWN_NEEDED;
    return CompareResult.TYPE_ONLY;
  }
}