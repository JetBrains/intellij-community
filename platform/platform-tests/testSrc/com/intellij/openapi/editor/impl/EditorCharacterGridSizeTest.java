// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LineWrapPositionStrategy;
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapApplianceManager;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.intellij.openapi.editor.impl.EditorModelFixture.TEST_CHAR_WIDTH;
import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class EditorCharacterGridSizeTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;

  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
  }

  private static final String LONG_LINE = "x".repeat(200);
  private static final int CONTENT_WIDTH = LONG_LINE.length() * TEST_CHAR_WIDTH;
  private static final char WRAP_MARKER = '|';

  private @NotNull SoftWrapApplianceManager softWrapApplianceManager() {
    return ((SoftWrapModelImpl)myFixture.getEditor().getSoftWrapModel()).getApplianceManager();
  }

  private void enableGridMode(boolean allowSoftWrapOptimizations) {
    myFixture.getEditor().getSettings().setCharacterGridWidthMultiplier(1.0f);
    ((EditorEx)myFixture.getEditor()).reinitSettings();
    assertNotNull(((EditorImpl)myFixture.getEditor()).getCharacterGrid());
    var softWrapApplianceManager = softWrapApplianceManager();
    softWrapApplianceManager.setAllowGridModeOptimizations(allowSoftWrapOptimizations);
    softWrapApplianceManager.recalculateIfNecessary("allow grid mode optimizations maybe toggled");
  }

  private int preferredWidth() {
    return myFixture.getEditor().getContentComponent().getPreferredSize().width;
  }

  // IJPL-247496: non-wrapping grid editors (Markdown) must expose the full line width, not the viewport.
  @Test
  public void testNonWrappingGridEditorIsScrollableToTheEndOfLongLines() {
    myFixture.runTest(() -> {
      myFixture.initText(LONG_LINE);
      myFixture.getEditor().getSettings().setAdditionalColumnsCount(0);
      myFixture.setEditorVisibleSize(20, 10);
      enableGridMode(false);

      assertTrue(myFixture.getEditor().getScrollingModel().getVisibleArea().width < CONTENT_WIDTH);
      assertTrue(preferredWidth() >= CONTENT_WIDTH, "preferred=" + preferredWidth());
    });
  }

  // IJPL-180831: soft-wrapping grid editors (terminal) still clamp preferred width to the viewport.
  @Test
  public void testWrappingGridEditorStaysClampedToViewport() {
    myFixture.runTest(() -> {
      myFixture.initText(LONG_LINE);
      myFixture.getEditor().getSettings().setAdditionalColumnsCount(0);
      myFixture.configureSoftWraps(20);
      enableGridMode(true);

      assertTrue(preferredWidth() < CONTENT_WIDTH, "preferred=" + preferredWidth());
    });
  }

  // IJPL-251582: The grid mode optimized soft-wrapping must not place soft-wraps inside collapsed fold regions.
  @Test
  public void testGridEditorWithSoftWrapOptimizationsDoesNotWrapInsideCollapsedFoldRegion() {
    myFixture.runTest(() -> {
      myFixture.initText(LONG_LINE.substring(0, 20));
      myFixture.configureSoftWraps(10, false);
      myFixture.addCollapsedFoldRegion(8, 14, "..."); // covers the grid column boundary at offset 11
      enableGridMode(true);

      myFixture.verifySoftWrapPositions(8);
    });
  }

  @Test
  public void testGridEditorWithSoftWrapOptimizationsRespectsLineStrategy() {
    myFixture.runTest(() -> {
      myFixture.initText(LONG_LINE.substring(0, 20));
      myFixture.configureSoftWraps(10, false);
      softWrapApplianceManager().setLineWrapPositionStrategy(new MarkerLineWrapPositionStrategy() {
        @Override
        public boolean isSoftWrappingAllowed(@NotNull Editor editor, int offset) {
          return false;
        }
      });
      enableGridMode(true);

      myFixture.verifySoftWrapPositions();
    });
  }

  @Test
  public void testLineStrategyIsNotConsultedForLinesThatFit() {
    myFixture.runTest(() -> {
      myFixture.initText("short line\nanother short line");
      myFixture.configureSoftWraps(20, false);
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
      myFixture.verifySoftWrapPositions();
    });
  }

  // The grid mode soft-wrapping optimization is not a pure optimization:
  //  it places wraps at grid column boundaries without consulting LineWrapPositionStrategy.
  // Grid editors that didn't opt in should keep the strategy-based wrap positions.
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void testGridEditorWithoutSoftWrapOptimizationsRespectsLineWrapPositionStrategy(boolean useLigatures) {
    myFixture.runTest(() -> {
      myFixture.initText(("xxx" + WRAP_MARKER).repeat(10));
      var colorsScheme = myFixture.getEditor().getColorsScheme();
      var fontPreferences = new FontPreferencesImpl();
      colorsScheme.getFontPreferences().copyTo(fontPreferences);
      fontPreferences.setUseLigatures(useLigatures);
      colorsScheme.setFontPreferences(fontPreferences);
      myFixture.configureSoftWraps(10, false);
      softWrapApplianceManager().setLineWrapPositionStrategy(new MarkerLineWrapPositionStrategy());
      enableGridMode(/* allowSoftWrapOptimizations */ false);

      myFixture.verifySoftWrapPositions(7, 15, 23, 31);
    });
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
