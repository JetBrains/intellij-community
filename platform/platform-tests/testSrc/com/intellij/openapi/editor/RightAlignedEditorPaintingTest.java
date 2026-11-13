/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.ui.IslandsState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


@TestDataPath("$PROJECT_ROOT/community/platform/platform-tests/testData/editor/painting/right")
@TestApplication
public class RightAlignedEditorPaintingTest extends EditorPaintingTestCase {
  private static final String OLD_SELECTION_KEY = "editor.old.full.horizontal.selection.enabled";

  private record SelectionState(@Nullable Boolean rounded, boolean islands, boolean oldSelection) {}

  /**
   * Sets the rounding option, and makes the selection hug or fill.
   * The selection hugs under Islands while the registry key {@link #OLD_SELECTION_KEY} is off.
   */
  private static SelectionState setNewSelectionEnabled(boolean enabled) {
    var settings = EditorSettingsExternalizable.getInstance();
    var oldSelection = Registry.get(OLD_SELECTION_KEY);
    var state = new SelectionState(
      settings.getRoundedHighlightingOption(),
      IslandsState.Companion.isEnabled(),
      oldSelection.asBoolean()
    );
    IslandsState.Companion.setEnabled(enabled, false);
    oldSelection.setValue(!enabled);
    settings.setRoundedHighlightingOption(enabled);
    return state;
  }

  private static void restoreSelectionState(SelectionState state) {
    IslandsState.Companion.setEnabled(state.islands(), false);
    Registry.get(OLD_SELECTION_KEY).setValue(state.oldSelection());
    EditorSettingsExternalizable.getInstance().setRoundedHighlightingOption(state.rounded());
  }

  private void initText(@NotNull String fileText) {
    myFixture.initText(fileText);
    ((EditorImpl)myFixture.getEditor()).setHorizontalTextAlignment(EditorImpl.TEXT_ALIGNMENT_RIGHT);
  }

  @Test
  public void testWholeLineHighlighterAtDocumentEnd() {
    myFixture.runTest(() -> {
      initText("foo");
      addLineHighlighter(0, 3, HighlighterLayer.WARNING, null, Color.red);
      checkResult();
    });
  }

  @Test
  public void testBoxedHighlightingLastLinePartially() {
    myFixture.runTest(() -> {
      initText("foo\nbar bar");
      addBorderHighlighter(2, 7, HighlighterLayer.WARNING, Color.red);
      checkResult();
    });
  }

  @Test
  public void testUpperHighlighterCanSetDefaultForegroundColor() {
    myFixture.runTest(() -> {
      initText("foo");
      addRangeHighlighter(1, 3, HighlighterLayer.WARNING, Color.red, null);
      addRangeHighlighter(2, 3, HighlighterLayer.ERROR, Color.black, null);
      checkResult();
    });
  }

  @Test
  public void testCaretRowWinsOverSyntaxEvenInPresenceOfHighlighter() {
    myFixture.runTest(() -> {
      initText("foo");
      setUniformEditorHighlighter(new TextAttributes(null, Color.red, null, null, Font.PLAIN));
      addRangeHighlighter(0, 3, 0, null, Color.blue);
      checkResult();
    });
  }

  @Test
  public void testEmptyBorderInEmptyDocument() {
    myFixture.runTest(() -> {
      initText("");
      addBorderHighlighter(0, 0, HighlighterLayer.WARNING, Color.red);
      checkResult();
    });
  }

  @Test
  public void testPrefixWithEmptyText() {
    myFixture.runTest(() -> {
      initText("");
      ((EditorEx)myFixture.getEditor()).setPrefixTextAndAttributes(">", new TextAttributes(Color.blue, Color.gray, null, null, Font.PLAIN));
      checkResult();
    });
  }

  @Test
  public void testBorderAtLastLine() {
    myFixture.runTest(() -> {
      initText("a\nbc");
      addBorderHighlighter(3, 4, HighlighterLayer.WARNING, Color.red);
      checkResult();
    });
  }

  @Test
  public void testFoldedRegionShownOnlyWithBorder() {
    myFixture.runTest(() -> {
      initText("abc");
      myFixture.addCollapsedFoldRegion(0, 3, "...");
      myFixture.getEditor().getColorsScheme().setAttributes(
        EditorColors.FOLDED_TEXT_ATTRIBUTES,
        new TextAttributes(null, null, Color.blue, EffectType.BOXED, Font.PLAIN)
      );
      checkResult();
    });
  }

  @Test
  public void testEraseMarker() {
    myFixture.runTest(() -> {
      initText("abc");
      setUniformEditorHighlighter(new TextAttributes(null, null, null, null, Font.BOLD));
      addRangeHighlighter(1, 2, 0, TextAttributes.ERASE_MARKER);
      checkResult();
    });
  }

  @Test
  public void testInlayAtEmptyLine() {
    myFixture.runTest(() -> {
      initText("\n");
      myFixture.getEditor().getInlayModel().addInlineElement(0, new MyInlayRenderer());
      checkResult();
    });
  }

  @Test
  public void testMultilineBorderWithInlays() {
    myFixture.runTest(() -> {
      initText("abc\ndef");
      myFixture.getEditor().getInlayModel().addInlineElement(1, new MyInlayRenderer());
      myFixture.getEditor().getInlayModel().addInlineElement(6, new MyInlayRenderer());
      addBorderHighlighter(0, 7, 0, Color.red);
      checkResult();
    });
  }

  @Test
  public void testSelectionInsideLine() {
    myFixture.runTest(() -> {
      var state = setNewSelectionEnabled(false);
      try {
        initText("first line\nsecond line");
        myFixture.getEditor().getSelectionModel().setSelection(6, 12);
        checkResult();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testSelectionInsideLineNewSelection() {
    myFixture.runTest(() -> {
      var state = setNewSelectionEnabled(true);
      try {
        initText("first longer line\nsecond line");
        myFixture.getEditor().getSelectionModel().setSelection(13, 19);
        checkResult();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testSelectionInsideLineNewSelection2() {
    myFixture.runTest(() -> {
      var state = setNewSelectionEnabled(true);
      try {
        initText("first longer line\nsecond line");
        myFixture.getEditor().getSelectionModel().setSelection(18, 21);
        checkResult();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testHuggingSelectionAcrossSoftWrap() {
    myFixture.runTest(() -> {
      var state = setNewSelectionEnabled(true);
      try {
        initText("abcdefgh ij");
        configureSoftWraps(8);
        myFixture.getEditor().getSelectionModel().setSelection(6, 10);
        assertOnlySmallBlockBeforeSoftWrappedLine();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testHuggingSelectionAcrossSoftWrapInsideWord() {
    myFixture.runTest(() -> {
      var state = setNewSelectionEnabled(true);
      try {
        initText("abcdefghijk\n<caret>l");
        configureSoftWraps(8);
        myFixture.getEditor().getSelectionModel().setSelection(6, 10);
        assertOnlySmallBlockBeforeSoftWrappedLine();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testCaretAfterEmptyLine() {
    myFixture.runTest(() -> {
      initText("\nsecond line<caret>");
      checkResult();
    });
  }

  @Test
  public void testCaretOnEmptyLine() {
    myFixture.runTest(() -> {
      initText("<caret>\n    second line");
      checkResult();
    });
  }

  /**
   * Asserts that the hugging selection on visual line 1 covers only the small block before the text.
   * Visual line 1 starts at the one soft wrap of the first line, inside the selection.
   * The empty left side of visual line 1 keeps the editor background.
   */
  private void assertOnlySmallBlockBeforeSoftWrappedLine() throws IOException {
    Editor editor = myFixture.getEditor();
    List<? extends SoftWrap> softWraps = editor.getSoftWrapModel().getSoftWrapsForLine(0);
    assertEquals(1, softWraps.size(), "the first line has no single soft wrap");
    int wrapOffset = softWraps.getFirst().getStart();
    SelectionModel selectionModel = editor.getSelectionModel();
    assertTrue(selectionModel.getSelectionStart() < wrapOffset && wrapOffset < selectionModel.getSelectionEnd(),
               "the selection does not cross the soft wrap at " + wrapOffset);
    BufferedImage image = paintEditor(false, null, null);
    Color selection = selectionModel.getTextAttributes().getBackgroundColor();
    int[] yRange = editor.visualLineToYRange(1);
    int middleY = (yRange[0] + yRange[1]) / 2;
    int textX = editor.visualPositionToXY(editor.offsetToVisualPosition(wrapOffset, false, false)).x;
    int leftX = editor.getContentComponent().getInsets().left;
    assertTrue(textX - leftX > 2 * EditorUtil.getPlainSpaceWidth(editor), "the left side of visual line 1 is too narrow");
    assertNotEquals(selection.getRGB(), image.getRGB(leftX, middleY), "the selection fills the left side of visual line 1");
    assertEquals(selection.getRGB(), image.getRGB(textX - 1, middleY), "the small block before the text is absent");
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/right";
  }
}
