// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.modTree;

import com.intellij.openapi.util.Ref;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ModificationTreeImplTest {
  public record TreeImplementation(String name, IntFunction<? extends ModificationTree> factory) {
    public ModificationTree tree(int version0Length) {
      return factory.apply(version0Length);
    }

    @Override
    public @NotNull String toString() {
      return name;
    }
  }

  static List<TreeImplementation> implementations() {
    return List.of(
      new TreeImplementation("binary", length -> wrapChecked(ModificationTreeImpl.initial(length))),
      new TreeImplementation("b+tree", length1 -> wrapChecked(ModificationBTreeImpl.initial(length1)))
    );
  }

  static ModificationTree unwrapChecked(ModificationTree tree) {
    return tree instanceof WrappedCheckedModificationTree(ModificationTree myTree) ? myTree : tree;
  }

  static ModificationTree wrapChecked(ModificationTree tree) {
    return new WrappedCheckedModificationTree(tree);
  }

  /// calls [ModificationTree#checkInvariants()] on modifications, delegate all ops to [myTree]
  private record WrappedCheckedModificationTree(ModificationTree myTree) implements ModificationTree {
    @Override
    public int toCurrentOffset(int offsetInVersion0) {
      return myTree.toCurrentOffset(offsetInVersion0);
    }

    @Override
    public int toVersion0Offset(int offsetInCurrent) {
      return myTree.toVersion0Offset(offsetInCurrent);
    }

    @Override
    public @NotNull ModificationTree insert(int offsetInCurrent, int length) {
      ModificationTree newTree = myTree.insert(offsetInCurrent, length);
      newTree.checkInvariants();
      return wrapChecked(newTree);
    }

    @Override
    public @NotNull ModificationTree delete(int startInCurrent, int endInCurrent) {
      ModificationTree newTree = myTree.delete(startInCurrent, endInCurrent);
      newTree.checkInvariants();
      return wrapChecked(newTree);
    }

    @Override
    public void checkInvariants() {
      myTree.checkInvariants();
    }

    @Override
    public boolean equals(Object obj) {
      return myTree.equals(obj);
    }
  }

  @ParameterizedTest
  @MethodSource("implementations")
  void initialTreeIsIdentityMapping(TreeImplementation implementation) {
    ModificationTree tree = implementation.tree(11);

    for (int i = 0; i <= 11; i++) {
      assertEquals(i, tree.toCurrentOffset(i));
      assertEquals(i, tree.toVersion0Offset(i));
    }
  }

  @ParameterizedTest
  @MethodSource("implementations")
  void insertInMiddleShiftsOriginalTextAfterInsertionPoint(TreeImplementation implementation) {
    ModificationTree tree = implementation.tree(11);

    // version0: "hello world"
    // insert 10 chars before original offset 6:
    // current:  "hello __________world"
    ModificationTree modified = tree.insert(6, 10);

    assertEquals(0, modified.toCurrentOffset(0));
    assertEquals(5, modified.toCurrentOffset(5));

    // Right-biased: original boundary 6 maps after inserted text.
    assertEquals(16, modified.toCurrentOffset(6));
    assertEquals(17, modified.toCurrentOffset(7));
    assertEquals(21, modified.toCurrentOffset(11));

    // Inserted current gap [6,16) maps back to version-0 boundary 6.
    assertEquals(6, modified.toVersion0Offset(6));
    assertEquals(6, modified.toVersion0Offset(10));
    assertEquals(6, modified.toVersion0Offset(15));

    // Start of surviving "world".
    assertEquals(6, modified.toVersion0Offset(16));
    assertEquals(7, modified.toVersion0Offset(17));
    assertEquals(11, modified.toVersion0Offset(21));
  }

  @ParameterizedTest
  @MethodSource("implementations")
  void insertAtBeginningShiftsAllOriginalText(TreeImplementation implementation) {
    ModificationTree tree = implementation.tree(5);

    ModificationTree modified = tree.insert(0, 3);

    // version0 offsets move right by 3.
    assertEquals(3, modified.toCurrentOffset(0));
    assertEquals(4, modified.toCurrentOffset(1));
    assertEquals(8, modified.toCurrentOffset(5));

    // Current gap [0,3) maps to original boundary 0.
    assertEquals(0, modified.toVersion0Offset(0));
    assertEquals(0, modified.toVersion0Offset(1));
    assertEquals(0, modified.toVersion0Offset(2));

    // Original text starts at current offset 3.
    assertEquals(0, modified.toVersion0Offset(3));
    assertEquals(1, modified.toVersion0Offset(4));
    assertEquals(5, modified.toVersion0Offset(8));
  }

  @ParameterizedTest
  @MethodSource("implementations")
  void insertAtEndIncreasesCurrentLengthButDoesNotShiftOriginalText(TreeImplementation implementation) {
    ModificationTree tree = implementation.tree(5);

    ModificationTree modified = tree.insert(5, 3);

    assertEquals(0, modified.toCurrentOffset(0));
    assertEquals(4, modified.toCurrentOffset(4));

    // EOF maps to new current EOF.
    assertEquals(8, modified.toCurrentOffset(5));

    // Inserted EOF gap [5,8) maps back to version-0 EOF.
    assertEquals(5, modified.toVersion0Offset(5));
    assertEquals(5, modified.toVersion0Offset(6));
    assertEquals(5, modified.toVersion0Offset(7));
    assertEquals(5, modified.toVersion0Offset(8));
  }

  @ParameterizedTest
  @MethodSource("implementations")
  void insertInsideOriginalRunSplitsMappingRun(TreeImplementation implementation) {
    ModificationTree tree = implementation.tree(11);

    // Insert one char at current/original offset 8.
    //
    // version0: hello world
    //                  ^
    //
    // current:  hello wo_rld
    ModificationTree modified = tree.insert(8, 1);

    assertEquals(0, modified.toCurrentOffset(0));
    assertEquals(7, modified.toCurrentOffset(7));

    // Right-biased: boundary 8 maps after inserted char.
    assertEquals(9, modified.toCurrentOffset(8));
    assertEquals(10, modified.toCurrentOffset(9));
    assertEquals(12, modified.toCurrentOffset(11));

    // Inserted gap [8,9) maps back to original boundary 8.
    assertEquals(8, modified.toVersion0Offset(8));
    assertEquals(8, modified.toVersion0Offset(9));
    assertEquals(9, modified.toVersion0Offset(10));
    assertEquals(11, modified.toVersion0Offset(12));
  }

  @ParameterizedTest
  @MethodSource("implementations")
  void multipleInsertionsAtSameOriginalBoundaryAccumulateIntoSameGap(TreeImplementation implementation) {
    ModificationTree tree = implementation.tree(11);

    ModificationTree modified = tree
      .insert(6, 10)
      .insert(10, 1)
      .insert(12, 2);

    // All three insertions happened inside the current gap before original offset 6.
    // Total inserted length before original 6 is 13.
    assertEquals(0, modified.toCurrentOffset(0));
    assertEquals(5, modified.toCurrentOffset(5));
    assertEquals(19, modified.toCurrentOffset(6));
    assertEquals(20, modified.toCurrentOffset(7));
    assertEquals(24, modified.toCurrentOffset(11));

    // Whole inserted gap [6,19) maps to version-0 boundary 6.
    for (int current = 6; current < 19; current++) {
      assertEquals(6, modified.toVersion0Offset(current));
    }

    assertEquals(6, modified.toVersion0Offset(19));
    assertEquals(7, modified.toVersion0Offset(20));
    assertEquals(11, modified.toVersion0Offset(24));
  }

  @ParameterizedTest
  @MethodSource("implementations")
  void insertBeforeWorldThenInsertInsideWorld(TreeImplementation implementation) {
    ModificationTree tree = implementation.tree(11);

    // First:
    // version0: hello world
    // current:  hello __________world
    ModificationTree v1 = tree.insert(6, 10);

    // Original offset 8 is now current offset 18.
    assertEquals(18, v1.toCurrentOffset(8));

    // Insert one char before original offset 8.
    //
    // current becomes conceptually:
    // hello __________wo_rld
    ModificationTree v2 = v1.insert(18, 1);

    assertEquals(0, v2.toCurrentOffset(0));
    assertEquals(6, v2.toCurrentOffset(6) - 10); // original 6 shifted by first insert only
    assertEquals(16, v2.toCurrentOffset(6));

    // Original [6,8) has delta +10.
    assertEquals(16, v2.toCurrentOffset(6));
    assertEquals(17, v2.toCurrentOffset(7));

    // Original [8,11) has delta +11.
    assertEquals(19, v2.toCurrentOffset(8));
    assertEquals(20, v2.toCurrentOffset(9));
    assertEquals(22, v2.toCurrentOffset(11));

    // First inserted gap before original 6.
    assertEquals(6, v2.toVersion0Offset(6));
    assertEquals(6, v2.toVersion0Offset(15));

    // Original "wo".
    assertEquals(6, v2.toVersion0Offset(16));
    assertEquals(7, v2.toVersion0Offset(17));

    // Second inserted gap before original 8.
    assertEquals(8, v2.toVersion0Offset(18));

    // Original "rld".
    assertEquals(8, v2.toVersion0Offset(19));
    assertEquals(9, v2.toVersion0Offset(20));
    assertEquals(11, v2.toVersion0Offset(22));
  }

  @ParameterizedTest
  @MethodSource("implementations")
  void insertReturnsSameInstanceForZeroLengthInsertion(TreeImplementation implementation) {
    ModificationTree tree = implementation.tree(11);

    ModificationTree modified = tree.insert(6, 0);

    assertSame(unwrapChecked(tree), unwrapChecked(modified));
  }

  @ParameterizedTest
  @MethodSource("implementations")
  void insertIsPersistentAndDoesNotChangeOldTree(TreeImplementation implementation) {
    ModificationTree original = implementation.tree(11);

    ModificationTree modified = original.insert(6, 10);

    assertNotSame(original, modified);

    // Old tree remains identity.
    for (int i = 0; i <= 11; i++) {
      assertEquals(i, original.toCurrentOffset(i));
      assertEquals(i, original.toVersion0Offset(i));
    }

    // New tree has shifted mapping.
    assertEquals(16, modified.toCurrentOffset(6));
    assertEquals(21, modified.toCurrentOffset(11));
  }

  @ParameterizedTest
  @MethodSource("implementations")
  void roundTripVersion0ToCurrentToVersion0AfterInsertions(TreeImplementation implementation) {
    ModificationTree tree = implementation.tree(11)
      .insert(6, 10)
      .insert(18, 1)
      .insert(0, 3)
      .insert(25, 2);

    for (int offset0 = 0; offset0 <= 11; offset0++) {
      int current = tree.toCurrentOffset(offset0);
      int backToVersion0 = tree.toVersion0Offset(current);

      assertEquals(offset0, backToVersion0);
    }
  }

  @ParameterizedTest
  @MethodSource("implementations")
  void currentOffsetsInsideInsertedGapsMapToRightBiasedVersion0Boundary(TreeImplementation implementation) {
    ModificationTree tree = implementation.tree(11)
      .insert(6, 10);

    // Gap before original offset 6.
    for (int current = 6; current < 16; current++) {
      assertEquals(6, tree.toVersion0Offset(current));
    }

    // Mapping inserted gap current -> version0 -> current jumps to gap end.
    for (int current = 6; current < 16; current++) {
      int version0 = tree.toVersion0Offset(current);
      assertEquals(16, tree.toCurrentOffset(version0));
    }
  }

  @ParameterizedTest
  @MethodSource("implementations")
  void perf(TreeImplementation implementation) {
    Ref<ModificationTree> tree = new Ref<>(unwrapChecked(implementation.tree(1_000_000)));
    long ins = TimeoutUtil.measureExecutionTime(() -> {
      ModificationTree t = tree.get();
      for (int i = 0; i < 1_000_000; i++) {
        t = t.insert(2 * i, 1);
      }
      tree.set(t);
    });
    long que = TimeoutUtil.measureExecutionTime(() -> {
      ModificationTree t = tree.get();
      for (int n = 0; n < 100; n++) {
        for (int i = 0; i < 1_000_000; i++) {
          assertEquals(2 * i + 1, t.toCurrentOffset(i));
        }
      }
    });
    System.out.println("insert:" + ins + "ms; query:" + que + "ms");
  }
}