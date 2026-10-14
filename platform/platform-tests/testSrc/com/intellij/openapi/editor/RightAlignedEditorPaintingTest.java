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
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.ui.IslandsState;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;


@TestDataPath("$PROJECT_ROOT/community/platform/platform-tests/testData/editor/painting/right")
@TestApplication
public class RightAlignedEditorPaintingTest extends EditorPaintingTestCase {
  private record SelectionState(boolean enabled, boolean islands) {}

  private static SelectionState setNewSelectionEnabled(boolean enabled) {
    var value = Registry.get("editor.old.full.horizontal.selection.enabled");
    var islands = IslandsState.Companion.isEnabled();

    var state = new SelectionState(enabled, islands);
    value.setValue(!enabled);
    IslandsState.Companion.setEnabled(enabled, false);
    return state;
  }

  private static void restoreSelectionState(SelectionState state) {
    Registry.get("editor.old.full.horizontal.selection.enabled").setValue(!state.enabled());
    IslandsState.Companion.setEnabled(state.islands(), false);
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

  @NotNull
  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/right";
  }
}
