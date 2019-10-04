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

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

@TestDataPath("$CONTENT_ROOT/testData/editor/painting")
public class EditorPaintingTest extends EditorPaintingTestCase {

  public void testWholeLineHighlighterAtDocumentEnd() throws Exception {
    initText("foo");
    addLineHighlighter(0, 3, HighlighterLayer.WARNING, null, Color.red);
    checkResult();
  }

  public void testBoxedHighlightingLastLinePartially() throws Exception {
    initText("foo\nbar bar");
    addBorderHighlighter(2, 7, HighlighterLayer.WARNING, Color.red);
    checkResult();
  }

  public void testUpperHighlighterCanSetDefaultForegroundColor() throws Exception {
    initText("foo");
    addRangeHighlighter(1, 3, HighlighterLayer.WARNING, Color.red, null);
    addRangeHighlighter(2, 3, HighlighterLayer.ERROR, Color.black, null);
    checkResult();
  }
  
  public void testCaretRowWinsOverSyntaxEvenInPresenceOfHighlighter() throws Exception {
    initText("foo");
    setUniformEditorHighlighter(new TextAttributes(null, Color.red, null, null, Font.PLAIN));
    addRangeHighlighter(0, 3, 0, null, Color.blue);
    checkResult();
  }
  
  public void testEmptyBorderInEmptyDocument() throws Exception {
    initText("");
    addBorderHighlighter(0, 0, HighlighterLayer.WARNING, Color.red);
    checkResult();
  }
  
  public void testPrefixWithEmptyText() throws Exception {
    initText("");
    ((EditorEx)getEditor()).setPrefixTextAndAttributes(">", new TextAttributes(Color.blue, Color.gray, null, null, Font.PLAIN));
    checkResult();
  }
  
  public void testBorderAtLastLine() throws Exception {
    initText("a\nbc");
    addBorderHighlighter(3, 4, HighlighterLayer.WARNING, Color.red);
    checkResult();
  }
  
  public void testFoldedRegionShownOnlyWithBorder() throws Exception {
    initText("abc");
    addCollapsedFoldRegion(0, 3, "...");
    getEditor().getColorsScheme().setAttributes(
      EditorColors.FOLDED_TEXT_ATTRIBUTES,
      new TextAttributes(null, null, Color.blue, EffectType.BOXED, Font.PLAIN)
    );
    checkResult();
  }

  public void testEraseMarker() throws Exception {
    initText("abc");
    setUniformEditorHighlighter(new TextAttributes(null, null, null, null, Font.BOLD));
    addRangeHighlighter(1, 2, 0, TextAttributes.ERASE_MARKER);
    checkResult();
  }

  public void testInlayAtEmptyLine() throws Exception {
    initText("\n");
    getEditor().getInlayModel().addInlineElement(0, new MyInlayRenderer());
    checkResult();
  }

  public void testMultilineBorderWithInlays() throws Exception {
    initText("abc\ndef");
    getEditor().getInlayModel().addInlineElement(1, new MyInlayRenderer());
    getEditor().getInlayModel().addInlineElement(6, new MyInlayRenderer());
    addBorderHighlighter(0, 7, 0, Color.red);
    checkResult();
  }

  public void testSoftWrapAtHighlighterBoundary() throws Exception {
    initText("a bc");
    configureSoftWraps(2);
    assertNotNull(getEditor().getSoftWrapModel().getSoftWrap(2));
    addRangeHighlighter(1, 3, HighlighterLayer.CARET_ROW + 1, null, Color.red);
    addRangeHighlighter(1, 2, HighlighterLayer.CARET_ROW + 2, null, Color.blue);
    checkResult();
  }

  public void testFontStyleAfterMove() throws Exception {
    initText("text\ntext\n");
    addRangeHighlighter(0, 4, 0, new TextAttributes(null, null, null, null, Font.BOLD));
    addRangeHighlighter(5, 9, 0, new TextAttributes(null, null, null, null, Font.BOLD));
    checkResult(); // initial text layout cache population

    getEditor().getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent event) {
        // force population of text layout cache on document update
        // this can be done by editor implementation in real life,
        // but we're doing it manually here to cover more potential cases
        getEditor().visualPositionToXY(new VisualPosition(0, 10));
      }
    });

    WriteCommandAction.runWriteCommandAction(getProject(), () -> ((DocumentEx)getEditor().getDocument()).moveText(5, 10, 0));
    checkResult();
  }

  public void testSoftWrapWithWithLineSeparator() throws Exception {
    initText("x\nabcef\ny");
    configureSoftWraps(2);
    verifySoftWrapPositions(4, 5);

    RangeHighlighter topHighlighter = addRangeHighlighter(4, 4, 0, null);
    topHighlighter.setLineSeparatorColor(Color.red);
    topHighlighter.setLineSeparatorPlacement(SeparatorPlacement.TOP);

    RangeHighlighter bottomHighlighter = addRangeHighlighter(4, 4, 0, null);
    bottomHighlighter.setLineSeparatorColor(Color.blue);
    bottomHighlighter.setLineSeparatorPlacement(SeparatorPlacement.BOTTOM);

    checkResult();
  }

  public void testSoftWrappedLineHighlighterWithBlockInlay() throws Exception {
    initText("some text");
    configureSoftWraps(5);
    addBlockInlay(0);
    addLineHighlighter(0, 0, HighlighterLayer.CARET_ROW + 1, null, Color.red);
    checkResultWithGutter();
  }

  public void testBlockInlaysWithSelection() throws Exception {
    initText("line 1\nline 2\n");
    addBlockInlay(getEditor().getDocument().getLineStartOffset(0));
    addBlockInlay(getEditor().getDocument().getLineStartOffset(1));
    executeAction(IdeActions.ACTION_EDITOR_TEXT_END);
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
    checkResult();
  }

  public void testMarginIsShownOverSelectionInBlockInlayRange() throws Exception {
    initText("  \n");
    addBlockInlay(0);
    executeAction(IdeActions.ACTION_SELECT_ALL);
    getEditor().getSettings().setRightMargin(1);
    checkResult();
  }
}
