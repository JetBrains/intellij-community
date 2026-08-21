// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LineWrapPositionStrategy;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapApplianceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EditorCharacterGridSizeTest extends AbstractEditorTest {
  private static final String LONG_LINE = "x".repeat(200);
  private static final int CONTENT_WIDTH = LONG_LINE.length() * TEST_CHAR_WIDTH;
  private static final char WRAP_MARKER = '|';

  private @NotNull SoftWrapApplianceManager softWrapApplianceManager() {
    return ((SoftWrapModelImpl)getEditor().getSoftWrapModel()).getApplianceManager();
  }

  private void enableGridMode(boolean allowSoftWrapOptimizations) {
    getEditor().getSettings().setCharacterGridWidthMultiplier(1.0f);
    ((EditorEx)getEditor()).reinitSettings();
    assertNotNull(((EditorImpl)getEditor()).getCharacterGrid());
    var softWrapApplianceManager = softWrapApplianceManager();
    softWrapApplianceManager.setAllowGridModeOptimizations(allowSoftWrapOptimizations);
    softWrapApplianceManager.recalculateIfNecessary("allow grid mode optimizations maybe toggled");
  }

  private int preferredWidth() {
    return getEditor().getContentComponent().getPreferredSize().width;
  }

  // IJPL-247496: non-wrapping grid editors (Markdown) must expose the full line width, not the viewport.
  public void testNonWrappingGridEditorIsScrollableToTheEndOfLongLines() {
    initText(LONG_LINE);
    getEditor().getSettings().setAdditionalColumnsCount(0);
    setEditorVisibleSize(20, 10);
    enableGridMode(false);

    assertTrue(getEditor().getScrollingModel().getVisibleArea().width < CONTENT_WIDTH);
    assertTrue("preferred=" + preferredWidth(), preferredWidth() >= CONTENT_WIDTH);
  }

  // IJPL-180831: soft-wrapping grid editors (terminal) still clamp preferred width to the viewport.
  public void testWrappingGridEditorStaysClampedToViewport() {
    initText(LONG_LINE);
    getEditor().getSettings().setAdditionalColumnsCount(0);
    configureSoftWraps(20);
    enableGridMode(true);

    assertTrue("preferred=" + preferredWidth(), preferredWidth() < CONTENT_WIDTH);
  }

  // IJPL-251582: The grid mode optimized soft-wrapping must not place soft-wraps inside collapsed fold regions.
  public void testGridEditorWithSoftWrapOptimizationsDoesNotWrapInsideCollapsedFoldRegion() {
    initText(LONG_LINE.substring(0, 20));
    configureSoftWraps(10, false);
    addCollapsedFoldRegion(8, 14, "..."); // covers the grid column boundary at offset 11
    enableGridMode(true);

    verifySoftWrapPositions(8);
  }

  public void testGridEditorWithSoftWrapOptimizationsRespectsLineStrategy() {
    initText(LONG_LINE.substring(0, 20));
    configureSoftWraps(10, false);
    softWrapApplianceManager().setLineWrapPositionStrategy(new MarkerLineWrapPositionStrategy() {
      @Override
      public boolean isSoftWrappingAllowed(@NotNull Editor editor, int offset) {
        return false;
      }
    });
    enableGridMode(true);

    verifySoftWrapPositions();
  }

  public void testLineStrategyIsNotConsultedForLinesThatFit() {
    initText("short line\nanother short line");
    configureSoftWraps(20, false);
    int[] calls = {0};
    softWrapApplianceManager().setLineWrapPositionStrategy(new MarkerLineWrapPositionStrategy() {
      @Override
      public boolean isSoftWrappingAllowed(@NotNull Editor editor, int offset) {
        calls[0]++;
        return true;
      }
    });

    softWrapApplianceManager().recalculateIfNecessary("line strategy changed");

    assertEquals(0, calls[0]);
    verifySoftWrapPositions();
  }

  // The grid mode soft-wrapping optimization is not a pure optimization:
  //  it places wraps at grid column boundaries without consulting LineWrapPositionStrategy.
  // Grid editors that didn't opt in should keep the strategy-based wrap positions.
  public void testGridEditorWithoutSoftWrapOptimizationsRespectsLineWrapPositionStrategy() {
    initText(("xxx" + WRAP_MARKER).repeat(10));
    configureSoftWraps(10, false);
    softWrapApplianceManager().setLineWrapPositionStrategy(new MarkerLineWrapPositionStrategy());
    enableGridMode(/* allowSoftWrapOptimizations */ false);

    verifySoftWrapPositions(7, 15, 23, 31);
  }

  // Wraps right before {@link #WRAP_MARKER} only
  private static class MarkerLineWrapPositionStrategy implements LineWrapPositionStrategy {
    @Override
    public int calculateWrapPosition(@NotNull Document document,
                                     @Nullable Project project,
                                     int startOffset,
                                     int endOffset,
                                     int maxPreferredOffset,
                                     boolean allowToBeyondMaxPreferredOffset,
                                     boolean isSoftWrap) {
      assertTrue(isSoftWrap);
      CharSequence text = document.getImmutableCharSequence();
      for (int offset = endOffset - 1; offset > startOffset; offset--) {
        if (text.charAt(offset) == WRAP_MARKER) {
          return offset;
        }
      }
      return -1;
    }

    @Override
    public boolean canWrapLineAtOffset(CharSequence text, int offset) {
      return false;
    }
  }
}
