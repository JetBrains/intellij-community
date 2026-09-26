// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.modTree;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.intellij.openapi.editor.impl.modTree.ModificationTreeImplTest.TreeImplementation;
import static com.intellij.openapi.editor.impl.modTree.ModificationTreeImplTest.unwrapChecked;
import static com.intellij.openapi.editor.impl.modTree.ModificationTreeImplTest.wrapChecked;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModificationTreeImplDeleteTest {
  static Stream<TreeImplementation> implementations() {
    return Stream.of(
      new TreeImplementation("binary", length -> wrapChecked(ModificationTreeImpl.initial(length))),
      new TreeImplementation("b+tree", length -> wrapChecked(ModificationBTreeImpl.initial(length)))
    );
  }

  private static void assertIdentityMapping(ModificationTree tree, int length) {
    for (int i = 0; i <= length; i++) {
      assertEquals(i, tree.toCurrentOffset(i), "toCurrentOffset(" + i + ")");
      assertEquals(i, tree.toVersion0Offset(i), "toVersion0Offset(" + i + ")");
    }
  }

  @ParameterizedTest
  @MethodSource("implementations")
  void deleteMiddleOfOriginalText(TreeImplementation implementation) {
    ModificationTree tree = implementation.tree(6);

    // version0: abcdef
    // delete:      cd
    // current:  abef
    ModificationTree modified = tree.delete(2, 4);

    assertEquals(0, modified.toCurrentOffset(0));
    assertEquals(1, modified.toCurrentOffset(1));

    // Deleted original range [2,4) collapses to current offset 2.
    assertEquals(2, modified.toCurrentOffset(2));
    assertEquals(2, modified.toCurrentOffset(3));
    assertEquals(2, modified.toCurrentOffset(4));

    assertEquals(3, modified.toCurrentOffset(5));
    assertEquals(4, modified.toCurrentOffset(6));

    assertEquals(0, modified.toVersion0Offset(0));
    assertEquals(1, modified.toVersion0Offset(1));

    // Current offset 2 is now original offset 4.
    assertEquals(4, modified.toVersion0Offset(2));
    assertEquals(5, modified.toVersion0Offset(3));
    assertEquals(6, modified.toVersion0Offset(4));
  }

  @ParameterizedTest
  @MethodSource("implementations")
  void deleteAtBeginningOfOriginalText(TreeImplementation implementation) {
    ModificationTree tree = implementation.tree(5);

    // version0: abcde
    // delete:   ab
    // current:  cde
    ModificationTree modified = tree.delete(0, 2);

    // Deleted original prefix collapses to current offset 0.
    assertEquals(0, modified.toCurrentOffset(0));
    assertEquals(0, modified.toCurrentOffset(1));
    assertEquals(0, modified.toCurrentOffset(2));

    assertEquals(1, modified.toCurrentOffset(3));
    assertEquals(2, modified.toCurrentOffset(4));
    assertEquals(3, modified.toCurrentOffset(5));

    assertEquals(2, modified.toVersion0Offset(0));
    assertEquals(3, modified.toVersion0Offset(1));
    assertEquals(4, modified.toVersion0Offset(2));
    assertEquals(5, modified.toVersion0Offset(3));
  }

  @ParameterizedTest
  @MethodSource("implementations")
  void deleteAtEndOfOriginalText(TreeImplementation implementation) {
    ModificationTree tree = implementation.tree(5);

    // version0: abcde
    // delete:      de
    // current:  abc
    ModificationTree modified = tree.delete(3, 5);

    assertEquals(0, modified.toCurrentOffset(0));
    assertEquals(1, modified.toCurrentOffset(1));
    assertEquals(2, modified.toCurrentOffset(2));

    // Deleted suffix collapses to current EOF.
    assertEquals(3, modified.toCurrentOffset(3));
    assertEquals(3, modified.toCurrentOffset(4));
    assertEquals(3, modified.toCurrentOffset(5));

    assertEquals(0, modified.toVersion0Offset(0));
    assertEquals(1, modified.toVersion0Offset(1));
    assertEquals(2, modified.toVersion0Offset(2));

    // Current EOF maps to version-0 EOF because mapping is right-biased.
    assertEquals(5, modified.toVersion0Offset(3));
  }

  @ParameterizedTest
  @MethodSource("implementations")
  void deleteWholeOriginalText(TreeImplementation implementation) {
    ModificationTree tree = implementation.tree(5);

    ModificationTree modified = tree.delete(0, 5);

    for (int i = 0; i <= 5; i++) {
      assertEquals(0, modified.toCurrentOffset(i));
    }

    // Only current offset is EOF. Right-biased result is version-0 EOF.
    assertEquals(5, modified.toVersion0Offset(0));
  }

  @ParameterizedTest
  @MethodSource("implementations")
  void deleteOnlyPartOfInsertedGap(TreeImplementation implementation) {
    ModificationTree tree = implementation.tree(11)
      .insert(6, 10);

    // version0: hello world
    // current:  hello __________world
    //
    // Delete 5 chars from the inserted gap:
    //
    // current gap [6,16) -> [6,11)
    ModificationTree modified = tree.delete(8, 13);

    assertEquals(0, modified.toCurrentOffset(0));
    assertEquals(5, modified.toCurrentOffset(5));

    // Original offset 6 now starts after a 5-char inserted gap.
    assertEquals(11, modified.toCurrentOffset(6));
    assertEquals(12, modified.toCurrentOffset(7));
    assertEquals(16, modified.toCurrentOffset(11));

    // Remaining inserted gap [6,11) maps to original boundary 6.
    assertEquals(6, modified.toVersion0Offset(6));
    assertEquals(6, modified.toVersion0Offset(7));
    assertEquals(6, modified.toVersion0Offset(10));

    // Start of surviving original text.
    assertEquals(6, modified.toVersion0Offset(11));
    assertEquals(7, modified.toVersion0Offset(12));
    assertEquals(11, modified.toVersion0Offset(16));
  }

  @ParameterizedTest
  @MethodSource("implementations")
  void deleteEntireInsertedGapRestoresIdentityMapping(TreeImplementation implementation) {
    ModificationTree tree = implementation.tree(11)
      .insert(6, 10);

    ModificationTree modified = tree.delete(6, 16);

    assertIdentityMapping(modified, 11);
  }

  @ParameterizedTest
  @MethodSource("implementations")
  void deleteOriginalTextAfterInsertedGap(TreeImplementation implementation) {
    ModificationTree tree = implementation.tree(11)
      .insert(6, 10);

    // version0: hello world
    // current:  hello __________world
    //
    // Delete "world", i.e. current [16,21).
    ModificationTree modified = tree.delete(16, 21);

    assertEquals(0, modified.toCurrentOffset(0));
    assertEquals(5, modified.toCurrentOffset(5));

    // Original "world" [6,11) was deleted.
    // It collapses to current EOF, which is 16.
    assertEquals(16, modified.toCurrentOffset(6));
    assertEquals(16, modified.toCurrentOffset(7));
    assertEquals(16, modified.toCurrentOffset(11));

    // Current inserted suffix [6,16) has no exact original character.
    // With right-biased mapping it maps to version-0 EOF.
    assertEquals(11, modified.toVersion0Offset(6));
    assertEquals(11, modified.toVersion0Offset(10));
    assertEquals(11, modified.toVersion0Offset(15));
    assertEquals(11, modified.toVersion0Offset(16));
  }

  @ParameterizedTest
  @MethodSource("implementations")
  void deleteRangeSpanningOriginalTextInsertedGapAndMoreOriginalText(TreeImplementation implementation) {
    ModificationTree tree = implementation.tree(11)
      .insert(6, 10);

    // version0: hello world
    // current:  hello __________world
    //
    // Delete current [4,18):
    //   original [4,6)
    //   inserted gap [6,16)
    //   original [6,8)
    //
    // Remaining original text:
    //   [0,4) and [8,11)
    ModificationTree modified = tree.delete(4, 18);

    assertEquals(0, modified.toCurrentOffset(0));
    assertEquals(1, modified.toCurrentOffset(1));
    assertEquals(2, modified.toCurrentOffset(2));
    assertEquals(3, modified.toCurrentOffset(3));

    // Deleted original range [4,8) collapses to current offset 4.
    assertEquals(4, modified.toCurrentOffset(4));
    assertEquals(4, modified.toCurrentOffset(5));
    assertEquals(4, modified.toCurrentOffset(6));
    assertEquals(4, modified.toCurrentOffset(7));
    assertEquals(4, modified.toCurrentOffset(8));

    assertEquals(5, modified.toCurrentOffset(9));
    assertEquals(6, modified.toCurrentOffset(10));
    assertEquals(7, modified.toCurrentOffset(11));

    assertEquals(0, modified.toVersion0Offset(0));
    assertEquals(1, modified.toVersion0Offset(1));
    assertEquals(2, modified.toVersion0Offset(2));
    assertEquals(3, modified.toVersion0Offset(3));

    // Current offset 4 is now original offset 8.
    assertEquals(8, modified.toVersion0Offset(4));
    assertEquals(9, modified.toVersion0Offset(5));
    assertEquals(10, modified.toVersion0Offset(6));
    assertEquals(11, modified.toVersion0Offset(7));
  }

  @ParameterizedTest
  @MethodSource("implementations")
  void deleteInsideOriginalRunAfterPreviousInsertions(TreeImplementation implementation) {
    ModificationTree tree = implementation.tree(11)
      .insert(6, 10)
      .insert(18, 1);

    // Conceptual current:
    //   hello __________wo_rld
    //
    // Original mapping before deletion:
    //   [0,6)   delta 0
    //   [6,8)   delta +10
    //   [8,11)  delta +11
    //
    // Delete current [17,20):
    //   original offset 7, inserted char before 8, original offset 8
    //
    // Remaining:
    //   [0,6), [6,7), [9,11)
    ModificationTree modified = tree.delete(17, 20);

    assertEquals(16, modified.toCurrentOffset(6));

    // Original [7,9) was deleted/collapsed.
    assertEquals(17, modified.toCurrentOffset(7));
    assertEquals(17, modified.toCurrentOffset(8));
    assertEquals(17, modified.toCurrentOffset(9));

    assertEquals(18, modified.toCurrentOffset(10));
    assertEquals(19, modified.toCurrentOffset(11));

    assertEquals(6, modified.toVersion0Offset(16));
    assertEquals(9, modified.toVersion0Offset(17));
    assertEquals(10, modified.toVersion0Offset(18));
    assertEquals(11, modified.toVersion0Offset(19));
  }

  @ParameterizedTest
  @MethodSource("implementations")
  void zeroLengthDeleteReturnsSameInstance(TreeImplementation implementation) {
    ModificationTree tree = implementation.tree(11)
      .insert(6, 10);

    ModificationTree modified = tree.delete(8, 8);

    assertSame(unwrapChecked(tree), unwrapChecked(modified));
  }

  @ParameterizedTest
  @MethodSource("implementations")
  void deleteIsPersistentAndDoesNotChangeOldTree(TreeImplementation implementation) {
    ModificationTree original = implementation.tree(11);
    ModificationTree afterInsert = original.insert(6, 10);
    ModificationTree afterDelete = afterInsert.delete(6, 16);

    assertNotSame(original, afterInsert);
    assertNotSame(afterInsert, afterDelete);

    // Original remains identity.
    assertIdentityMapping(original, 11);

    // Inserted version still has the gap.
    assertEquals(16, afterInsert.toCurrentOffset(6));
    assertEquals(21, afterInsert.toCurrentOffset(11));
    assertEquals(6, afterInsert.toVersion0Offset(10));

    // Deleted version restored identity.
    assertIdentityMapping(afterDelete, 11);
  }

  @ParameterizedTest
  @MethodSource("implementations")
  void liveOriginalOffsetsRoundTripAfterDeletion(TreeImplementation implementation) {
    ModificationTree tree = implementation.tree(11)
      .insert(6, 10)
      .delete(4, 18);

    // After the deletion, live original offsets are:
    //   [0,4) and [8,11]
    int[] liveOffsets = {
      0, 1, 2, 3,
      8, 9, 10, 11
    };

    for (int offset0 : liveOffsets) {
      int current = tree.toCurrentOffset(offset0);
      int roundTrip = tree.toVersion0Offset(current);

      assertEquals(offset0, roundTrip, "offset0=" + offset0);
    }
  }

  @ParameterizedTest
  @MethodSource("implementations")
  void invalidDeleteArgumentsAreRejected(TreeImplementation implementation) {
    ModificationTree tree = implementation.tree(5);

    assertThrows(IndexOutOfBoundsException.class, () -> tree.delete(-1, 2));
    assertThrows(IndexOutOfBoundsException.class, () -> tree.delete(2, 6));
    assertThrows(IllegalArgumentException.class, () -> tree.delete(4, 2));
  }
}