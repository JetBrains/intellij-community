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
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorLinePainter;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.LineExtensionInfo;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.*;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.Processor;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TFloatArrayList;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

class EditorPainter {
  private static final Stroke IME_COMPOSED_TEXT_UNDERLINE_STROKE = new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0,
                                                                                   new float[]{0, 2, 0, 2}, 0);

  private final EditorView myView;
  private final EditorImpl myEditor;
  private final Document myDocument;

  EditorPainter(EditorView view) {
    myView = view;
    myEditor = view.getEditor();
    myDocument = myEditor.getDocument();
  }

  void paint(Graphics2D g) {
    Rectangle clip = g.getClipBounds();
    
    if (myEditor.getContentComponent().isOpaque()) {
      g.setColor(myEditor.getBackgroundColor());
      g.fillRect(clip.x, clip.y, clip.width, clip.height);
    }
    
    if (paintPlaceholderText(g)) {
      paintCaret(g);
      return;
    }
    
    int startLine = myView.yToVisualLine(Math.max(clip.y, 0));
    int endLine = myView.yToVisualLine(Math.max(clip.y + clip.height, 0));
    int lineCount = myDocument.getLineCount();
    int startOffset = startLine >= lineCount ? myDocument.getTextLength() : myDocument.getLineStartOffset(startLine);
    int endOffset = endLine >= lineCount ? myDocument.getTextLength() : myDocument.getLineEndOffset(endLine);
    
    paintBackground(g, clip, startLine, endLine);
    paintRightMargin(g, clip);
    paintCustomRenderers(g, startOffset, endOffset);
    MarkupModelEx docMarkup = (MarkupModelEx)DocumentMarkupModel.forDocument(myDocument, myEditor.getProject(), true);
    paintLineMarkersSeparators(g, clip, docMarkup, startOffset, endOffset);
    paintLineMarkersSeparators(g, clip, myEditor.getMarkupModel(), startOffset, endOffset);
    paintTextWithEffects(g, clip, startLine, endLine);
    paintHighlightersAfterEndOfLine(g, docMarkup, startOffset, endOffset);
    paintHighlightersAfterEndOfLine(g, myEditor.getMarkupModel(), startOffset, endOffset);
    paintBorderEffect(g, myEditor.getHighlighter(), startOffset, endOffset);
    paintBorderEffect(g, docMarkup, startOffset, endOffset);
    paintBorderEffect(g, myEditor.getMarkupModel(), startOffset, endOffset);
    
    paintCaret(g);
    
    paintComposedTextDecoration(g);
  }
  
  private boolean paintPlaceholderText(Graphics2D g) {
    CharSequence hintText = myEditor.getPlaceholder();
    EditorComponentImpl editorComponent = myEditor.getContentComponent();
    if (myDocument.getTextLength() > 0 || hintText == null || hintText.length() == 0 ||
        KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() == editorComponent &&
        !myEditor.getShowPlaceholderWhenFocused()) {
      return false;
    }
  
    hintText = SwingUtilities.layoutCompoundLabel(g.getFontMetrics(), hintText.toString(), null, 0, 0, 0, 0,
                                                  editorComponent.getBounds(), new Rectangle(), new Rectangle(), 0);
    g.setColor(myEditor.getFoldingModel().getPlaceholderAttributes().getForegroundColor());
    g.setFont(myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));
    g.drawString(hintText.toString(), 0, myView.getAscent());
    return true;
  }
  
  private void paintRightMargin(Graphics g, Rectangle clip) {
    EditorSettings settings = myEditor.getSettings();
    Color rightMargin = myEditor.getColorsScheme().getColor(EditorColors.RIGHT_MARGIN_COLOR);
    if (!settings.isRightMarginShown() || rightMargin == null) return;
  
    int x = settings.getRightMargin(myEditor.getProject()) * myView.getPlainSpaceWidth();
    g.setColor(rightMargin);
    UIUtil.drawLine(g, x, clip.y, x, clip.y + clip.height);
  }

  private void paintBackground(Graphics2D g, Rectangle clip, int startLine, int endLine) {
    int lineCount = myDocument.getLineCount();
    for (int line = startLine; line <= endLine; line++) {
      int y = myView.visualLineToY(line);
      float x = 0;
      if (line == 0 && myView.getPrefixLayout() != null) {
        x += myView.getPrefixTextWidthInPixels();
        paintBackground(g, myView.getPrefixAttributes(), 0, y, x);
      }
      if (line >= lineCount) break;
      paintLineFragments(g, clip, line, x, y, new LineFragmentPainter() {
        @Override
        public void paint(Graphics2D g, LineLayout.Fragment fragment, int fragmentStartOffset, int start, int end, 
                          TextAttributes attributes, float xStart, float xEnd, int y, boolean isRtl) {
          paintBackground(g, attributes, xStart, y, xEnd - xStart);
        }

        @Override
        public void paintAfterLineEnd(Graphics2D g, Rectangle clip, IterationState2 it, float x, int y) {
          x = paintAfterLineEndBackgroundSegments(g, it, x, y);
          if (it.getEndOffset() < myDocument.getTextLength()) {
            paintBackground(g, it.getPastLineEndBackgroundAttributes(), x, y, clip.x + clip.width - x);
          }
          else {
            if (it.hasPastFileEndBackgroundSegments()) {
              x = paintAfterLineEndBackgroundSegments(g, it, x, y);
            }
            paintBackground(g, it.getPastFileEndBackground(), x, y, clip.x + clip.width - x);
          }
        }
      });
    }
  }

  private void paintBackground(Graphics2D g, TextAttributes attributes, float x, int y, float width) {
    if (attributes == null) return;
    paintBackground(g, attributes.getBackgroundColor(), x, y, width);
  }

  private void paintBackground(Graphics2D g, Color color, float x, int y, float width) {
    if (width <= 0 ||
        color == null ||
        color.equals(myEditor.getColorsScheme().getDefaultBackground()) ||
        color.equals(myEditor.getBackgroundColor())) return;
    g.setColor(color);
    g.fillRect((int)x, y, (int)width, myView.getLineHeight());
  }

  private float paintAfterLineEndBackgroundSegments(Graphics2D g, IterationState2 it, float x, int y) {
    while (it.hasPastLineEndBackgroundSegment()) {
      int width = myView.getPlainSpaceWidth() * it.getPastLineEndBackgroundSegmentWidth();
      paintBackground(g, it.getPastLineEndBackgroundAttributes(), x, y, width);
      x += width;
      it.advanceToNextPastLineEndBackgroundSegment();
    }
    return x;
  }

  private void paintCustomRenderers(final Graphics2D g, final int startOffset, final int endOffset) {
    myEditor.getMarkupModel().processRangeHighlightersOverlappingWith(startOffset, endOffset, new Processor<RangeHighlighterEx>() {
      @Override
      public boolean process(RangeHighlighterEx highlighter) {
        if (highlighter.getEditorFilter().avaliableIn(myEditor)) {
          CustomHighlighterRenderer customRenderer = highlighter.getCustomRenderer();
          if (customRenderer != null && startOffset < highlighter.getEndOffset() && highlighter.getStartOffset() < endOffset) {
            customRenderer.paint(myEditor, highlighter, g);
          }
        }
        return true;
      }
    });
  }

  private void paintLineMarkersSeparators(final Graphics g,
                                          final Rectangle clip,
                                          MarkupModelEx markupModel,
                                          int startOffset,
                                          int endOffset) {
    markupModel.processRangeHighlightersOverlappingWith(startOffset, endOffset, new Processor<RangeHighlighterEx>() {
      @Override
      public boolean process(RangeHighlighterEx highlighter) {
        if (highlighter.getEditorFilter().avaliableIn(myEditor)) {
          paintLineMarkerSeparator(highlighter, clip, g);
        }
        return true;
      }
    });
  }

  private void paintLineMarkerSeparator(RangeHighlighter marker, Rectangle clip, Graphics g) {
    Color separatorColor = marker.getLineSeparatorColor();
    LineSeparatorRenderer lineSeparatorRenderer = marker.getLineSeparatorRenderer();
    if (separatorColor == null && lineSeparatorRenderer == null) {
      return;
    }
    int line = myDocument.getLineNumber(marker.getLineSeparatorPlacement() == SeparatorPlacement.TOP
                                        ? marker.getStartOffset()
                                        : marker.getEndOffset());
    int y = myView.visualLineToY(line + (marker.getLineSeparatorPlacement() == SeparatorPlacement.TOP ? 0 : 1)) - 1;
    int endShift = clip.x + clip.width;
    EditorSettings settings = myEditor.getSettings();
    if (settings.isRightMarginShown() && myEditor.getColorsScheme().getColor(EditorColors.RIGHT_MARGIN_COLOR) != null) {
      endShift = Math.min(endShift, settings.getRightMargin(myEditor.getProject()) * myView.getPlainSpaceWidth());
    }

    g.setColor(separatorColor);
    if (lineSeparatorRenderer != null) {
      lineSeparatorRenderer.drawLine(g, 0, endShift, y);
    }
    else {
      UIUtil.drawLine(g, 0, y, endShift, y);
    }
  }


  private void paintTextWithEffects(Graphics2D g, Rectangle clip, int startLine, int endLine) {
    final CharSequence text = myDocument.getImmutableCharSequence();
    final EditorImpl.LineWhitespacePaintingStrategy whitespacePaintingStrategy = myEditor.new LineWhitespacePaintingStrategy();
    int lineCount = myDocument.getLineCount();
    for (int line = startLine; line <= endLine; line++) {
      int y = myView.visualLineToY(line) + myView.getAscent();
      float x = 0;
      LineLayout prefixLayout = myView.getPrefixLayout();
      if (line == 0 && prefixLayout != null) {
        x = paintLineLayoutWithEffect(g, prefixLayout, x, y, 
                                      myView.getPrefixAttributes().getEffectColor(), myView.getPrefixAttributes().getEffectType());
      }
      if (line >= lineCount) break;
      
      whitespacePaintingStrategy.update(text, myDocument.getLineStartOffset(line), myDocument.getLineEndOffset(line));
      
      final int theLine = line;
      paintLineFragments(g, clip, line, (int)x, y, new LineFragmentPainter() {
        @Override
        public void paint(Graphics2D g, LineLayout.Fragment fragment, int fragmentStartOffset, int start, int end, 
                          TextAttributes attributes, float xStart, float xEnd, int y, boolean isRtl) {
          g.setColor(attributes.getForegroundColor());
          fragment.draw(g, xStart, y, start, end);
          paintWhitespace(g, text, xStart, y, start, end, whitespacePaintingStrategy, fragment, fragmentStartOffset, isRtl);
          if (hasTextEffect(attributes.getEffectColor(), attributes.getEffectType())) {
            paintTextEffect(g, xStart, xEnd, y, attributes.getEffectColor(), attributes.getEffectType());
          }
        }

        @Override
        public void paintAfterLineEnd(Graphics2D g, Rectangle clip, IterationState2 iterationState, float x, int y) {
          paintLineExtensions(g, theLine, x, y);
        }
      });
    }
  }

  private float paintLineLayoutWithEffect(Graphics2D g, LineLayout layout, float x, int y, Color effectColor, EffectType effectType) {
    float initialX = x;
    for (LineLayout.Fragment fragment : layout.getFragmentsInVisualOrder()) {
      fragment.draw(g, x, y, 0, fragment.getLength());
      x = fragment.advance(x);

    }
    if (hasTextEffect(effectColor, effectType)) {
      paintTextEffect(g, initialX, x, y, effectColor, effectType);
    }
    return x;
  }

  private static boolean hasTextEffect(Color effectColor, EffectType effectType) {
    return effectColor != null && (effectType == EffectType.LINE_UNDERSCORE ||
                                   effectType == EffectType.BOLD_LINE_UNDERSCORE ||
                                   effectType == EffectType.BOLD_DOTTED_LINE ||
                                   effectType == EffectType.WAVE_UNDERSCORE ||
                                   effectType == EffectType.STRIKEOUT);
  }

  private void paintTextEffect(Graphics2D g, float xFrom, float xTo, int y, Color effectColor, EffectType effectType) {
    int xStart = (int)xFrom;
    int xEnd = (int)xTo;
    g.setColor(effectColor);
    if (effectType == EffectType.LINE_UNDERSCORE) {
      UIUtil.drawLine(g, xStart, y + 1, xEnd, y + 1);
    }
    else if (effectType == EffectType.BOLD_LINE_UNDERSCORE) {
      UIUtil.drawLine(g, xStart, y, xEnd, y);
      UIUtil.drawLine(g, xStart, y + 1, xEnd, y + 1);
    }
    else if (effectType == EffectType.STRIKEOUT) {
      int y1 = y - myView.getCharHeight() / 2;
      UIUtil.drawLine(g, xStart, y1, xEnd, y1);
    }
    else if (effectType == EffectType.WAVE_UNDERSCORE) {
      UIUtil.drawWave(g, new Rectangle(xStart, y + 1, xEnd - xStart, myView.getDescent() - 1));
    }
    else if (effectType == EffectType.BOLD_DOTTED_LINE) {
      UIUtil.drawBoldDottedLine(g, xStart, xEnd, SystemInfo.isMac ? y : y + 1, myEditor.getBackgroundColor(), g.getColor(), false);
    }
  }

  private void paintWhitespace(Graphics2D g, CharSequence text, float x, int y, int start, int end,
                               EditorImpl.LineWhitespacePaintingStrategy whitespacePaintingStrategy,
                               LineLayout.Fragment fragment, int fragmentStart, boolean isRtl) {
    g.setColor(myEditor.getColorsScheme().getColor(EditorColors.WHITESPACES_COLOR));
    for (int i = start; i < end; i++) {
      char c = text.charAt(isRtl ? fragmentStart + fragment.getLength() - i - 1 : i + fragmentStart);
      if (" \t\u3000".indexOf(c) >= 0 && whitespacePaintingStrategy.showWhitespaceAtOffset(i + fragmentStart)) {
        int startX = (int)fragment.offsetToX(x, start, i);
        int endX = (int)fragment.offsetToX(x, start, i + 1);

        if (c == ' ') {
          g.fillRect((startX + endX) / 2, y, 1, 1);
        }
        else if (c == '\t') {
          endX -= myView.getPlainSpaceWidth() / 4;
          int height = myView.getCharHeight();
          int halfHeight = height / 2;
          int mid = y - halfHeight;
          int top = y - height;
          UIUtil.drawLine(g, startX, mid, endX, mid);
          UIUtil.drawLine(g, endX, y, endX, top);
          g.fillPolygon(new int[]{endX - halfHeight, endX - halfHeight, endX}, new int[]{y, y - height, y - halfHeight}, 3);
        }
        else if (c == '\u3000') { // ideographic space
          final int charHeight = myView.getCharHeight();
          g.drawRect(startX + 2, y - charHeight, endX - startX - 4, charHeight);
        }
      }
    }
  }

  private void paintLineExtensions(Graphics2D g, int line, float x, int y) {
    Project project = myEditor.getProject();
    VirtualFile virtualFile = myEditor.getVirtualFile();
    if (project == null || virtualFile == null) return;
    for (EditorLinePainter painter : EditorLinePainter.EP_NAME.getExtensions()) {
      Collection<LineExtensionInfo> extensions = painter.getLineExtensions(project, virtualFile, line);
      if (extensions != null) {
        for (LineExtensionInfo info : extensions) {
          LineLayout layout = new LineLayout(myView, info.getText(), info.getFontType(), g.getFontRenderContext(), x);
          x = paintLineLayoutWithEffect(g, layout, x, y, info.getEffectColor(), info.getEffectType());
          int currentLineWidth = (int)x;
          EditorSizeManager sizeManager = myView.getSizeManager();
          if (currentLineWidth > sizeManager.getMaxLineWithExtensionWidth()) {
            sizeManager.setMaxLineWithExtensionWidth(line, currentLineWidth);
          }
        }
      }
    }
  }

  private void paintHighlightersAfterEndOfLine(final Graphics2D g,
                                               MarkupModelEx markupModel,
                                               final int startOffset,
                                               int endOffset) {
    markupModel.processRangeHighlightersOverlappingWith(startOffset, endOffset, new Processor<RangeHighlighterEx>() {
      @Override
      public boolean process(RangeHighlighterEx highlighter) {
        if (highlighter.getEditorFilter().avaliableIn(myEditor) && highlighter.getStartOffset() >= startOffset) {
          paintHighlighterAfterEndOfLine(g, highlighter);
        }
        return true;
      }
    });
  }

  private void paintHighlighterAfterEndOfLine(Graphics2D g, RangeHighlighterEx highlighter) {
    if (!highlighter.isAfterEndOfLine()) {
      return;
    }
    int offset = highlighter.getStartOffset();
    int line = myDocument.getLineNumber(offset);

    float x = myView.getLineLayout(line).getMaxX();
    int y = myView.visualLineToY(line);
    TextAttributes attributes = highlighter.getTextAttributes();
    paintBackground(g, attributes, x, y, myView.getPlainSpaceWidth());
    if (attributes != null && hasTextEffect(attributes.getEffectColor(), attributes.getEffectType())) {
      paintTextEffect(g, x, x + myView.getPlainSpaceWidth() - 1, y + myView.getAscent(), 
                      attributes.getEffectColor(), attributes.getEffectType());
    }
  }

  private void paintBorderEffect(Graphics2D g, EditorHighlighter highlighter, int clipStartOffset, int clipEndOffset) {
    HighlighterIterator it = highlighter.createIterator(clipStartOffset);
    while (!it.atEnd() && it.getStart() < clipEndOffset) {
      TextAttributes attributes = it.getTextAttributes();
      if (isBorder(attributes)) {
        paintBorderEffect(g, it.getStart(), it.getEnd(), attributes);
      }
      it.advance();
    }
  }

  private void paintBorderEffect(final Graphics2D g, MarkupModelEx markupModel, int clipStartOffset, int clipEndOffset) {
    markupModel.processRangeHighlightersOverlappingWith(clipStartOffset, clipEndOffset, new Processor<RangeHighlighterEx>() {
      @Override
      public boolean process(RangeHighlighterEx rangeHighlighter) {
        if (rangeHighlighter.getEditorFilter().avaliableIn(myEditor)) {
          TextAttributes attributes = rangeHighlighter.getTextAttributes();
          if (isBorder(attributes)) {
            paintBorderEffect(g, rangeHighlighter.getAffectedAreaStartOffset(), rangeHighlighter.getAffectedAreaEndOffset(), attributes);
          }
        }
        return true;
      }
    });
  }

  private static boolean isBorder(TextAttributes attributes) {
    return attributes != null &&
           (attributes.getEffectType() == EffectType.BOXED || attributes.getEffectType() == EffectType.ROUNDED_BOX) &&
           attributes.getEffectColor() != null;
  }

  private void paintBorderEffect(Graphics2D g, int startOffset, int endOffset, TextAttributes attributes) {
    int startLine = myDocument.getLineNumber(startOffset);
    int endLine = myDocument.getLineNumber(endOffset);
    if (startLine + 1 == endLine &&
        startOffset == myDocument.getLineStartOffset(startLine) &&
        endOffset == myDocument.getLineStartOffset(endLine)) {
      // special case of line highlighters
      endLine--;
      endOffset = myDocument.getLineEndOffset(endLine);
    }
  
    boolean rounded = attributes.getEffectType() == EffectType.ROUNDED_BOX;
    int lineHeight = myView.getLineHeight() - 1;
    g.setColor(attributes.getEffectColor());
    if (startLine == endLine) {
      int y = myView.visualLineToY(startLine);
      TFloatArrayList ranges = adjustedLogicalRangeToVisualRanges(startLine, startOffset, endOffset);
      for (int i = 0; i < ranges.size() - 1; i+= 2) {
        int startX = (int)ranges.get(i);
        int endX = (int)ranges.get(i + 1);
        if (rounded) {
          UIUtil.drawRectPickedOut(g, startX, y, endX - startX, lineHeight);
        } 
        else {
          g.drawRect(startX, y, endX - startX, lineHeight);
        }
      }
    }
    else {
      int maxWidth = myView.getMaxWidthInLineRange(startLine, endLine) - 1;
      TFloatArrayList leadingRanges = adjustedLogicalRangeToVisualRanges(startLine, startOffset, myDocument.getLineEndOffset(startLine));
      TFloatArrayList trailingRanges = adjustedLogicalRangeToVisualRanges(endLine, myDocument.getLineStartOffset(endLine), endOffset);
      if (!leadingRanges.isEmpty() && !trailingRanges.isEmpty()) {
        boolean containsInnerLines = endLine > startLine + 1;
        int leadingTopY = myView.visualLineToY(startLine);
        int leadingBottomY = leadingTopY + lineHeight;
        int trailingTopY = myView.visualLineToY(endLine);
        int trailingBottomY = trailingTopY + lineHeight;
        float start = 0;
        float end = 0;
        float leftGap = leadingRanges.get(0) - (containsInnerLines ? 0 : trailingRanges.get(0));
        int adjustY = leftGap == 0 ? 2 : leftGap > 0 ? 1 : 0; // avoiding 1-pixel gap between aligned lines
        for (int i = 0; i < leadingRanges.size() - 1; i += 2) {
          start = leadingRanges.get(i);
          end = leadingRanges.get(i + 1);
          if (i > 0) {
            drawLine(g, leadingRanges.get(i - 1), leadingBottomY, start, leadingBottomY, rounded);
          }
          drawLine(g, start, leadingBottomY + (i == 0 ? adjustY : 0), start, leadingTopY, rounded);
          if ((i + 2) < leadingRanges.size()) {
            drawLine(g, start, leadingTopY, end, leadingTopY, rounded);
            drawLine(g, end, leadingTopY, end, leadingBottomY, rounded);
          }
        }
        end = Math.max(end, maxWidth);
        drawLine(g, start, leadingTopY, end, leadingTopY, rounded);
        drawLine(g, end, leadingTopY, end, trailingTopY - 1, rounded);
        float targetX = trailingRanges.get(trailingRanges.size() - 1);
        drawLine(g, end, trailingTopY - 1, targetX, trailingTopY - 1, rounded);
        adjustY = end == targetX ? -2 : -1; // for lastX == targetX we need to avoid a gap when rounding is used
        for (int i = trailingRanges.size() - 2; i >= 0; i -= 2) {
          start = trailingRanges.get(i);
          end = trailingRanges.get(i + 1);

          drawLine(g, end, trailingTopY + (i == 0 ? adjustY : 0), end, trailingBottomY, rounded);
          drawLine(g, end, trailingBottomY, start, trailingBottomY, rounded);
          drawLine(g, start, trailingBottomY, start, trailingTopY, rounded);
          if (i > 0) {
            drawLine(g, start, trailingTopY, trailingRanges.get(i - 1), trailingTopY, rounded);
          }
        }
        float lastX = start;
        if (containsInnerLines) {
          if (start > 0) {
            drawLine(g, start, trailingTopY, start, trailingTopY - 1, rounded);
            drawLine(g, start, trailingTopY - 1, 0, trailingTopY - 1, rounded);
            drawLine(g, 0, trailingTopY - 1, 0, leadingBottomY + 1, rounded);
          }
          else {
            drawLine(g, start, trailingTopY, 0, leadingBottomY + 1, rounded);
          }
          lastX = 0;
        }
        targetX = leadingRanges.get(0);
        if (lastX < targetX) {
          drawLine(g, lastX, leadingBottomY + 1, targetX, leadingBottomY + 1, rounded);
        }
        else {
          drawLine(g, lastX, leadingBottomY + 1, lastX, leadingBottomY, rounded);
          drawLine(g, lastX, leadingBottomY, targetX, leadingBottomY, rounded);
        }
      }
    }
  }
  
  private static void drawLine(Graphics2D g, float x1, int y1, float x2, int y2, boolean rounded) {
    if (rounded) {
      UIUtil.drawLinePickedOut(g, (int) x1, y1, (int)x2, y2);
    } else {
      UIUtil.drawLine(g, (int)x1, y1, (int)x2, y2);
    }
  }

  /**
   * Returns ranges obtained from {@link #logicalRangeToVisualRanges(int, int, int)}, adjusted for painting range border - lines should
   * line inside target ranges (except for empty range).
   */
  private TFloatArrayList adjustedLogicalRangeToVisualRanges(int line, int startOffset, int endOffset) {
    TFloatArrayList ranges = logicalRangeToVisualRanges(line, startOffset, endOffset);
    for (int i = 0; i < ranges.size() - 1; i += 2) {
      float startX = ranges.get(i);
      float endX = ranges.get(i + 1);
      if (startX == endX) {
        endX++;
      }
      else {
        endX--;
      }
      ranges.set(i + 1, endX);
    }
    return ranges;
  }


    /**
     * Returns a list of pairs of x coordinates for visual ranges representing given logical range. If <code>startOffset == endOffset</code>,
     * a pair of equal numbers is returned, corresponding to target position.
     */
  private TFloatArrayList logicalRangeToVisualRanges(int line, int startOffset, int endOffset) {
    assert startOffset <= endOffset;
    int lineStartOffset = myDocument.getLineStartOffset(line);
    startOffset -= lineStartOffset;
    endOffset -= lineStartOffset;
    LineLayout lineLayout = myView.getLineLayout(line);
    float x = line == 0 ? myView.getPrefixTextWidthInPixels() : 0; 
    TFloatArrayList result = new TFloatArrayList();
    for (LineLayout.Fragment fragment : lineLayout.getFragmentsInVisualOrder()) {
      if (startOffset == endOffset) {
        if (startOffset >= fragment.getStartOffset() && startOffset <= fragment.getEndOffset()) {
          x = fragment.absoluteOffsetToX(x, startOffset);
          result.add(x);
          result.add(x);
          break;
        }
      }
      else if (startOffset < fragment.getEndOffset() && endOffset > fragment.getStartOffset()) {
        float x1 = fragment.absoluteOffsetToX(x, Math.max(fragment.getStartOffset(), startOffset));
        float x2 = fragment.absoluteOffsetToX(x, Math.min(fragment.getEndOffset(), endOffset));
        if (x1 > x2) {
          float tmp = x1;
          x1 = x2;
          x2 = tmp;
        }
        if (result.isEmpty() || x1 > result.get(result.size() - 1)) {
          result.add(x1);
          result.add(x2);
        }
        else {
          result.set(result.size() - 1, x2);
        }
      }

      x = fragment.advance(x);
    }
    return result;
  } 

  private void paintComposedTextDecoration(Graphics2D g) {
    TextRange composedTextRange = myEditor.getComposedTextRange();
    if (composedTextRange != null) {
      Point p1 = myView.offsetToXY(Math.min(composedTextRange.getStartOffset(), myDocument.getTextLength()), true);
      Point p2 = myView.offsetToXY(Math.min(composedTextRange.getEndOffset(), myDocument.getTextLength()), false);
  
      int y = p1.y + myView.getAscent() + 1;
     
      g.setStroke(IME_COMPOSED_TEXT_UNDERLINE_STROKE);
      g.setColor(myEditor.getColorsScheme().getDefaultForeground());
      UIUtil.drawLine(g, p1.x, y, p2.x, y);
    }
  }

  private void paintCaret(Graphics2D g) {
    EditorImpl.CaretRectangle[] locations = myEditor.getCaretLocations();
    if (locations == null) return;
    for (EditorImpl.CaretRectangle location : locations) {
      paintCaretAt(g, location.myPoint.x, location.myPoint.y, location.myWidth);
    }
  }
  
  private void paintCaretAt(Graphics2D g, int x, int y, int width) {
    Rectangle viewRectangle = myEditor.getScrollingModel().getVisibleArea();
    if (x - viewRectangle.x < 0) {
      return;
    }
    int lineHeight = myView.getLineHeight();
    EditorSettings settings = myEditor.getSettings();
    Color caretColor = myEditor.getColorsScheme().getColor(EditorColors.CARET_COLOR);
    if (caretColor == null) caretColor = new JBColor(Gray._0, Gray._255);
    g.setColor(caretColor);
  
    if (myEditor.isInsertMode() != settings.isBlockCursor()) {
      if (UIUtil.isRetina()) {
        g.fillRect(x, y, settings.getLineCursorWidth(), lineHeight);
      } else {
        for (int i = 0; i < settings.getLineCursorWidth(); i++) {
          UIUtil.drawLine(g, x + i, y, x + i, y + lineHeight - 1);
        }
      }
    }
    else {
      Color background = myEditor.getCaretModel().getTextAttributes().getBackgroundColor();
      if (background == null) background = myEditor.getBackgroundColor();
      g.setXORMode(background);
      g.fillRect(Math.min(x, x + width), y, Math.abs(width), lineHeight - 1);
      g.setPaintMode();
    }
  }

  private void paintLineFragments(Graphics2D g, Rectangle clip, int line, float x, int y, LineFragmentPainter painter) {
    LineLayout lineLayout = myView.getLineLayout(line);
    int lineStartOffset = myDocument.getLineStartOffset(line);
    int lineEndOffset = myDocument.getLineEndOffset(line);
    IterationState2 it = null;
    if (lineEndOffset > lineStartOffset) {
      int prevEndOffset = -1;
      for (LineLayout.Fragment fragment : lineLayout.getFragmentsInVisualOrder()) {
        if (fragment.getVisualStartOffset() != prevEndOffset) {
          it = new IterationState2(myEditor, lineStartOffset + fragment.getVisualStartOffset(), 
                                  fragment.isRtl() ? 0 : myDocument.getTextLength(), true, false, fragment.isRtl());
        }
        prevEndOffset = fragment.getVisualEndOffset();
        assert it != null;
        int fragmentAbsoluteStartOffset = lineStartOffset + fragment.getStartOffset();
        int start = lineStartOffset + fragment.getVisualStartOffset();
        int end = lineStartOffset + fragment.getVisualEndOffset();
        while (fragment.isRtl() ? start > end : start < end) {
          if (fragment.isRtl() ? it.getEndOffset() >= start : it.getEndOffset() <= start) {
            assert !it.atEnd();
            it.advance();
          }
          TextAttributes attributes = it.getMergedAttributes();
          int curEnd = fragment.isRtl() ? Math.max(it.getEndOffset(), end) : Math.min(it.getEndOffset(), end);
          int relStart = fragment.absoluteToRelativeOffset(start - lineStartOffset);
          int relEnd = fragment.absoluteToRelativeOffset(curEnd - lineStartOffset);
          float xNew = fragment.offsetToX(x, relStart, relEnd);
          painter.paint(g, fragment, fragmentAbsoluteStartOffset, relStart, relEnd, attributes, x, xNew, y, fragment.isRtl());
          x = xNew;
          start = curEnd;
        }
      }
      assert it != null && !it.atEnd();
      it.advance();
    }
    painter.paintAfterLineEnd(g, clip, it != null && it.getEndOffset() == lineEndOffset ? 
                                       it : new IterationState2(myEditor, lineEndOffset, lineEndOffset, true), 
                              x, y);
  }

  interface LineFragmentPainter {
    void paint(Graphics2D g, LineLayout.Fragment fragment, int fragmentStartOffset, int start, int end, TextAttributes attributes, 
               float xStart, float xEnd, int y, boolean isRtl); 
    void paintAfterLineEnd(Graphics2D g, Rectangle clip, IterationState2 iterationState, float x, int y);
  }
}
