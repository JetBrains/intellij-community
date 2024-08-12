// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class GuardedBlocksIndexTest extends AbstractEditorTest {

  public void testCreateIndex() {
    assertEquals(
      index(0, 6, r(2, 4)),
      index(0, 3, r(2, 4)),
      index(3, 6, r(2, 4)),
      index(2, 4, r(2, 4))
    );
  }

  public void testCreateIndex1() {
    assertEquals(
      index(0, 10, r(2, 4), r(6, 8)),
      index(0, 6, r(2, 4), r(6, 8)),
      index(4, 10, r(2, 4), r(6, 8)),
      index(4, 6, r(2, 4), r(6, 8)),
      index(0, 10, r(2, 4), r(6, 8), r(2, 2), r(6, 6), r(8, 8))
    );
  }

  public void testNearestRight1() {
    GuardedBlocksIndex index = index(0, 6, r(2, 4));
    assertEquals(2, index.nearestRight(0));
    assertEquals(2, index.nearestRight(1));
    assertEquals(2, index.nearestRight(2));
    assertEquals(4, index.nearestRight(3));
    assertEquals(4, index.nearestRight(4));
    assertEquals(-1, index.nearestRight(5));
    assertEquals(-1, index.nearestRight(6));
  }

  public void testNearestRight2() {
    GuardedBlocksIndex index = index(0, 8, r(2, 4), r(6, 6));
    assertEquals(2, index.nearestRight(0));
    assertEquals(2, index.nearestRight(1));
    assertEquals(2, index.nearestRight(2));
    assertEquals(4, index.nearestRight(3));
    assertEquals(4, index.nearestRight(4));
    assertEquals(6, index.nearestRight(5));
    assertEquals(6, index.nearestRight(6));
    assertEquals(-1, index.nearestRight(7));
    assertEquals(-1, index.nearestRight(8));
  }

  public void testNearestLeft1() {
    GuardedBlocksIndex index = index(0, 6, r(2, 4));
    assertEquals(-1, index.nearestLeft(0));
    assertEquals(-1, index.nearestLeft(1));
    assertEquals(2, index.nearestLeft(2));
    assertEquals(2, index.nearestLeft(3));
    assertEquals(4, index.nearestLeft(4));
    assertEquals(4, index.nearestLeft(5));
    assertEquals(4, index.nearestLeft(6));
  }

  public void testNearestLeft2() {
    GuardedBlocksIndex index = index(0, 8, r(2, 4), r(6, 6));
    assertEquals(-1, index.nearestLeft(0));
    assertEquals(-1, index.nearestLeft(1));
    assertEquals(2, index.nearestLeft(2));
    assertEquals(2, index.nearestLeft(3));
    assertEquals(4, index.nearestLeft(4));
    assertEquals(4, index.nearestLeft(5));
    assertEquals(6, index.nearestLeft(6));
    assertEquals(6, index.nearestLeft(7));
    assertEquals(6, index.nearestLeft(8));
  }

  public void testNearestRightEmpty() {
    GuardedBlocksIndex index = index(0, 3);
    assertEquals(-1, index.nearestRight(0));
    assertEquals(-1, index.nearestRight(1));
    assertEquals(-1, index.nearestRight(2));
    assertEquals(-1, index.nearestRight(3));
  }

  public void testNearestLeftEmpty() {
    GuardedBlocksIndex index = index(0, 3);
    assertEquals(-1, index.nearestLeft(0));
    assertEquals(-1, index.nearestLeft(1));
    assertEquals(-1, index.nearestLeft(2));
    assertEquals(-1, index.nearestLeft(3));
  }

  public void testIsGuarded() {
    GuardedBlocksIndex index = index(0, 8, r(2, 4), r(6, 6));
    assertFalse(index.isGuarded(-1));
    assertFalse(index.isGuarded(0));
    assertFalse(index.isGuarded(1));
    assertTrue(index.isGuarded(2));
    assertTrue(index.isGuarded(3));
    assertFalse(index.isGuarded(4));
    assertFalse(index.isGuarded(5));
    assertFalse(index.isGuarded(6));
    assertFalse(index.isGuarded(7));
    assertFalse(index.isGuarded(8));
    assertFalse(index.isGuarded(9));
  }

  private static void assertEquals(Object o1, Object o2, Object o3, Object o4) {
    assertEquals(o1, o2);
    assertEquals(o2, o3);
    assertEquals(o3, o4);
  }

  private static void assertEquals(Object o1, Object o2, Object o3, Object o4, Object o5) {
    assertEquals(o1, o2);
    assertEquals(o2, o3);
    assertEquals(o3, o4);
    assertEquals(o4, o5);
  }

  private static @NotNull GuardedBlocksIndex index(int start, int end, @NotNull RangeMarker range) {
    return new GuardedBlocksIndex.Builder().build(start, end, List.of(range));
  }

  private static @NotNull GuardedBlocksIndex index(int start, int end, RangeMarker... ranges) {
    return new GuardedBlocksIndex.Builder().build(start, end, Arrays.asList(ranges));
  }

  private static @NotNull RangeMarker r(int start, int end) {
    return new MockRangeMarker(start, end);
  }

  private record MockRangeMarker(int startOffset, int endOffset) implements RangeMarker {

    @Override
    public int getStartOffset() {
      return startOffset;
    }

    @Override
    public int getEndOffset() {
      return endOffset;
    }

    //region unsupported
    @Override
    public @NotNull Document getDocument() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isValid() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setGreedyToLeft(boolean greedy) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setGreedyToRight(boolean greedy) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isGreedyToRight() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isGreedyToLeft() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void dispose() {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> @Nullable T getUserData(@NotNull Key<T> key) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
      throw new UnsupportedOperationException();
    }
    // endregion
  }
}
