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
import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

@TestDataPath("$CONTENT_ROOT/testData/editor/painting/right")
public class RightAlignedEditorPaintingTest extends EditorPaintingTestCase {
  @Override
  protected void initText(@NotNull String fileText) {
    super.initText(fileText);
    ((EditorImpl)myEditor).setHorizontalTextAlignment(EditorImpl.TEXT_ALIGNMENT_RIGHT);
  }

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
    ((EditorEx)myEditor).setPrefixTextAndAttributes(">", new TextAttributes(Color.blue, Color.gray, null, null, Font.PLAIN));
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
    myEditor.getColorsScheme().setAttributes(
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
    myEditor.getInlayModel().addInlineElement(0, new MyInlayRenderer());
    checkResult();
  }

  public void testMultilineBorderWithInlays() throws Exception {
    initText("abc\ndef");
    myEditor.getInlayModel().addInlineElement(1, new MyInlayRenderer());
    myEditor.getInlayModel().addInlineElement(6, new MyInlayRenderer());
    addBorderHighlighter(0, 7, 0, Color.red);
    checkResult();
  }

  public void testSelectionInsideLine() throws Exception {
    initText("first line\nsecond line");
    myEditor.getSelectionModel().setSelection(6, 12);
    checkResult();
  }

  public void testCaretAfterEmptyLine() throws Exception {
    initText("\nsecond line<caret>");
    checkResult();
  }

  public void testCaretOnEmptyLine() throws Exception {
    initText("<caret>\n    second line");
    checkResult();
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/right";
  }
}
