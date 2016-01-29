/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Processor;
import com.intellij.util.ui.UIUtil;
import gnu.trove.Equality;

import java.awt.*;

public class BorderEffect {
  private final Graphics myGraphics;
  private final int myStartOffset;
  private final int myEndOffset;
  private final TextRange myRange;
  private final EditorImpl myEditor;
  private final ClipDetector myClipDetector;
  private static final Equality<TextAttributes> SAME_COLOR_BOXES = new Equality<TextAttributes>() {
    @Override
    public boolean equals(final TextAttributes attributes1, final TextAttributes attributes2) {
      Color effectColor = attributes1.getEffectColor();
      EffectType effectType = attributes1.getEffectType();
      return effectColor != null
             && effectColor.equals(attributes2.getEffectColor())
             && (EffectType.BOXED == effectType || EffectType.ROUNDED_BOX == effectType) &&
             effectType == attributes2.getEffectType();
    }
  };
  private static final Condition<TextAttributes> BOX_FILTER = new Condition<TextAttributes>() {
                              @Override
                              public boolean value(TextAttributes attributes) {
                                return isBorder(attributes);
                              }
                            };

  public BorderEffect(EditorImpl editor, Graphics graphics, int clipStartOffset, int clipEndOffset) {
    myEditor = editor;
    myGraphics = graphics;
    myStartOffset = clipStartOffset;
    myEndOffset = clipEndOffset;
    myRange = new TextRange(myStartOffset, myEndOffset);
    myClipDetector = new ClipDetector(editor, graphics.getClipBounds());
  }

  private static boolean isBorder(TextAttributes textAttributes) {
    return textAttributes != null &&
           textAttributes.getEffectColor() != null &&
           (EffectType.BOXED == textAttributes.getEffectType() || EffectType.ROUNDED_BOX == textAttributes.getEffectType());
  }

  private void paintBorder(RangeHighlighterEx rangeHighlighter, TextAttributes textAttributes) {
    paintBorder(textAttributes.getEffectColor(),
                rangeHighlighter.getAffectedAreaStartOffset(),
                rangeHighlighter.getAffectedAreaEndOffset(),
                textAttributes.getEffectType());
  }

  private void paintBorder(Color color, int startOffset, int endOffset, EffectType effectType) {
    paintBorder(myGraphics, myEditor, startOffset, endOffset, color, effectType);
  }

  public void paintHighlighters(MarkupModelEx markupModel) {
    markupModel.processRangeHighlightersOverlappingWith(myStartOffset, myEndOffset, new Processor<RangeHighlighterEx>() {
      @Override
      public boolean process(RangeHighlighterEx rangeHighlighter) {
        TextAttributes textAttributes = rangeHighlighter.getTextAttributes();
        if (isBorder(textAttributes)) {
          paintBorder(rangeHighlighter, textAttributes);
        }
        return true;
      }
    });
  }

  public void paintHighlighters(EditorHighlighter highlighter) {
    int startOffset = startOfLineByOffset(myStartOffset);
    if (startOffset < 0 || startOffset >= myEditor.getDocument().getTextLength()) return;
    RangeIterator iterator = new RangeIterator(new FoldingOrNewLineGaps(myEditor), SAME_COLOR_BOXES,
                                               highlighter.createIterator(startOffset),
                                               BOX_FILTER);
    iterator.init(myRange);
    while (!iterator.atEnd()) {
      iterator.advance();
      paintBorder(myGraphics, myEditor, iterator.getStart(), iterator.getEnd(),
                  iterator.getTextAttributes().getEffectColor(), iterator.getTextAttributes().getEffectType());
    }
  }

  private int startOfLineByOffset(int offset) {
    int line = myEditor.offsetToLogicalLine(offset);
    if (line >= myEditor.getDocument().getLineCount()) return -1;
    return myEditor.getDocument().getLineStartOffset(line);
  }

  private void paintBorder(Graphics g, EditorImpl editor, int startOffset, int endOffset, Color color, EffectType effectType) {
    Color savedColor = g.getColor();
    g.setColor(color);
    paintBorder(g, editor, startOffset, endOffset, effectType);
    g.setColor(savedColor);
  }

  private void paintBorder(Graphics g, EditorImpl editor, int startOffset, int endOffset, EffectType effectType) {
    if (!myClipDetector.rangeCanBeVisible(startOffset, endOffset)) return;
    Point startPoint = offsetToXY(editor, startOffset);
    Point endPoint = offsetToXY(editor, endOffset);
    int height = endPoint.y - startPoint.y;
    int startX = startPoint.x;
    int startY = startPoint.y;
    int endX = endPoint.x;
    int lineHeight = editor.getLineHeight();
    if (height == 0) {
      int width = endX == startX ? 1 : endX - startX - 1;
      if (effectType == EffectType.ROUNDED_BOX) {
        UIUtil.drawRectPickedOut((Graphics2D)g, startX, startY, width, lineHeight - 1);
      } else {
        g.drawRect(startX, startY, width, lineHeight - 1);
      }
      return;
    }
    int startLine = editor.offsetToVisualLine(startOffset);
    int endLine = editor.offsetToVisualLine(endOffset);
    int maxWidth = Math.max(endX, editor.getMaxWidthInVisualLineRange(startLine, endLine - 1, false));
    BorderGraphics border = new BorderGraphics(g, startX, startY, effectType);
    border.horizontalTo(maxWidth);
    border.verticalRel(height - 1);
    border.horizontalTo(endX);
    if (endX > 0) {
      border.verticalRel(lineHeight);
      border.horizontalTo(0);
      border.verticalRel(-height + 1);
    }
    else if (height > lineHeight) {
      border.verticalRel(-height + lineHeight + 1);
    }
    border.horizontalTo(startX);
    border.verticalTo(startY);
  }

  private static Point offsetToXY(EditorImpl editor, int offset) {
    return editor.logicalPositionToXY(editor.offsetToLogicalPosition(offset));
  }

  public static void paintFoldedEffect(Graphics g, int foldingXStart,
                                       int y, int foldingXEnd, int lineHeight, Color effectColor,
                                       EffectType effectType) {
    if (effectColor == null || effectType != EffectType.BOXED) return;
    g.setColor(effectColor);
    g.drawRect(foldingXStart, y, foldingXEnd - foldingXStart, lineHeight - 1);
  }

  private static class FoldingOrNewLineGaps implements RangeIterator.Gaps {
    private final RangeIterator.FoldingGaps myFoldingGaps;
    private final CharSequence myChars;

    public FoldingOrNewLineGaps(CharSequence chars, RangeIterator.FoldingGaps foldingGaps) {
      myChars = chars;
      myFoldingGaps = foldingGaps;
    }

    public FoldingOrNewLineGaps(EditorImpl editor) {
      this(editor.getDocument().getCharsSequence(), new RangeIterator.FoldingGaps(editor.getFoldingModel()));
    }

    @Override
    public boolean isGapAt(int offset) {
      return myChars.charAt(offset) == '\n' || myFoldingGaps.isGapAt(offset);
    }
  }

  public static class BorderGraphics {
    private final Graphics myGraphics;

    private int myX;
    private int myY;
    private EffectType myEffectType;

    public BorderGraphics(Graphics graphics, int startX, int stIntY, EffectType effectType) {
      myGraphics = graphics;

      myX = startX;
      myY = stIntY;
      myEffectType = effectType;
    }

    public void horizontalTo(int x) {
      lineTo(x, myY);
    }

    public void horizontalRel(int width) {
      lineTo(myX + width, myY);
    }

    private void lineTo(int x, int y) {
      if (myEffectType == EffectType.ROUNDED_BOX) {
        UIUtil.drawLinePickedOut(myGraphics, myX, myY, x, y);
      } else {
        UIUtil.drawLine(myGraphics, myX, myY, x, y);
      }
      myX = x;
      myY = y;
    }

    public void verticalRel(int height) {
      lineTo(myX, myY + height);
    }

    public void verticalTo(int y) {
      lineTo(myX, y);
    }
  }
}
