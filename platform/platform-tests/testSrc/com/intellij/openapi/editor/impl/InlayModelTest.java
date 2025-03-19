// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Inlay;
import org.jetbrains.annotations.Nullable;

public class InlayModelTest extends AbstractEditorTest{

  public void testWidestBlockInlayIsNullIfNoInlayExists() {
    initText("text");
    assertNull(
      "no widest block inlay expected if no inlay exists",
      getWidestVisibleBlockInlay()
    );
  }

  public void testWidestBlockInlayIsNullIfInlayFolded() {
    initText("text");
    addBlockInlay(1, true, 10);
    toggleFoldRegionState(addFoldRegion(0, 2, ""), false);
    assertNull(
      "no widest block inlay expected if no visible inlay exists",
      getWidestVisibleBlockInlay()
    );
  }

  public void testWidestBlockInlayWithOneInlay() {
    initText("text");
    Inlay<?> expected = addBlockInlay(0, true, 10);
    Inlay<?> actual = getWidestVisibleBlockInlay();
    assertEquals(expected, actual);
  }

  public void testWidestBlockInlayWithTwoInlays() {
    initText("text");
    addBlockInlay(0, true, 10);
    Inlay<?> expected = addBlockInlay(1, true, 20);
    Inlay<?> actual = getWidestVisibleBlockInlay();
    assertEquals(expected, actual);
  }

  public void testWidestBlockInlayWithTwoInlaysReversed() {
    initText("text");
    Inlay<?> expected = addBlockInlay(0, true, 20);
    addBlockInlay(1, true, 10);
    Inlay<?> actual = getWidestVisibleBlockInlay();
    assertEquals(expected, actual);
  }

  private @Nullable Inlay<?> getWidestVisibleBlockInlay() {
    return ((InlayModelImpl) getEditor().getInlayModel()).getWidestVisibleBlockInlay();
  }
}
