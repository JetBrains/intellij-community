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
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.*;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapDrawingType;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.IdeBackgroundUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.paint.EffectPainter;
import com.intellij.util.DocumentUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TFloatArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders editor contents.
 */
class EditorPainter implements TextDrawingCallback {
  private static final Color CARET_LIGHT = Gray._255;
  private static final Color CARET_DARK = Gray._0;
  private static final Stroke IME_COMPOSED_TEXT_UNDERLINE_STROKE = new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0,
                                                                                   new float[]{0, 2, 0, 2}, 0);
  private static final int CARET_DIRECTION_MARK_SIZE = 5;
  private static final char IDEOGRAPHIC_SPACE = '\u3000'; // http://www.marathon-studios.com/unicode/U3000/Ideographic_Space
  private static final String WHITESPACE_CHARS = " \t" + IDEOGRAPHIC_SPACE;


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
    
    int startLine = myView.yToVisualLine(clip.y);
    int endLine = myView.yToVisualLine(clip.y + clip.height);
    int startOffset = myView.visualLineToOffset(startLine);
    int endOffset = myView.visualLineToOffset(endLine + 1);
    ClipDetector clipDetector = new ClipDetector(myEditor, clip);
    IterationState.CaretData caretData = myEditor.isPaintSelection() ? IterationState.createCaretData(myEditor) : null;

    paintBackground(g, clip, startLine, endLine, caretData);
    paintRightMargin(g, clip);
    paintCustomRenderers(g, startOffset, endOffset);
    MarkupModelEx docMarkup = myEditor.getFilteredDocumentMarkupModel();
    paintLineMarkersSeparators(g, clip, docMarkup, startOffset, endOffset);
    paintLineMarkersSeparators(g, clip, myEditor.getMarkupModel(), startOffset, endOffset);
    paintTextWithEffects(g, clip, startLine, endLine, caretData);
    paintHighlightersAfterEndOfLine(g, docMarkup, startOffset, endOffset);
    paintHighlightersAfterEndOfLine(g, myEditor.getMarkupModel(), startOffset, endOffset);
    paintBorderEffect(g, clipDetector, myEditor.getHighlighter(), startOffset, endOffset);
    paintBorderEffect(g, clipDetector, docMarkup, startOffset, endOffset);
    paintBorderEffect(g, clipDetector, myEditor.getMarkupModel(), startOffset, endOffset);
    
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
                                                  SwingUtilities.calculateInnerArea(editorComponent, null), // account for insets
                                                  new Rectangle(), new Rectangle(), 0);
    EditorFontType fontType = EditorFontType.PLAIN;
    Color color = myEditor.getFoldingModel().getPlaceholderAttributes().getForegroundColor();
    TextAttributes attributes = myEditor.getPlaceholderAttributes();
    if (attributes != null) {
      int type = attributes.getFontType();
      if (type == Font.ITALIC) fontType = EditorFontType.ITALIC;
      else if (type == Font.BOLD) fontType = EditorFontType.BOLD;
      else if (type == (Font.ITALIC | Font.BOLD)) fontType = EditorFontType.BOLD_ITALIC;

      Color attColor = attributes.getForegroundColor();
      if (attColor != null) color = attColor;
    }
    g.setColor(color);
    g.setFont(myEditor.getColorsScheme().getFont(fontType));
    Insets insets = myView.getInsets();
    g.drawString(hintText.toString(), insets.left, insets.top + myView.getAscent());
    return true;
  }
  
  private void paintRightMargin(Graphics g, Rectangle clip) {
    if (!isRightMarginShown()) return;
    int x = getRightMarginX();
    g.setColor(myEditor.getColorsScheme().getColor(EditorColors.RIGHT_MARGIN_COLOR));
    UIUtil.drawLine(g, x, clip.y, x, clip.y + clip.height);
  }
  
  private boolean isRightMarginShown() {
    return myEditor.getSettings().isRightMarginShown() && myEditor.getColorsScheme().getColor(EditorColors.RIGHT_MARGIN_COLOR) != null;
  }

  private int getRightMarginX() {
    return getMinX() + myEditor.getSettings().getRightMargin(myEditor.getProject()) * myView.getPlainSpaceWidth();
  }
  
  private int getMinX() {
    return myView.getInsets().left;
  }

  private void paintBackground(Graphics2D g, Rectangle clip, int startVisualLine, int endVisualLine, IterationState.CaretData caretData) {
    int lineCount = myEditor.getVisibleLineCount();
    
    final Map<Integer, Couple<Integer>> virtualSelectionMap = createVirtualSelectionMap(startVisualLine, endVisualLine);
    final VisualPosition primarySelectionStart = myEditor.getSelectionModel().getSelectionStartPosition();
    final VisualPosition primarySelectionEnd = myEditor.getSelectionModel().getSelectionEndPosition();

    LineLayout prefixLayout = myView.getPrefixLayout();
    if (startVisualLine == 0 && prefixLayout != null) {
      final Insets insets = myView.getInsets();
      paintBackground(g, myView.getPrefixAttributes(), insets.left, insets.top, prefixLayout.getWidth());
    }

    VisualLinesIterator visLinesIterator = new VisualLinesIterator(myEditor, startVisualLine);
    while (!visLinesIterator.atEnd()) {
      int visualLine = visLinesIterator.getVisualLine();
      if (visualLine > endVisualLine || visualLine >= lineCount) break;
      int y = visLinesIterator.getY();
      paintLineFragments(g, clip, visLinesIterator, caretData, y, new LineFragmentPainter() {
        @Override
        public void paintBeforeLineStart(Graphics2D g, TextAttributes attributes, int columnEnd, float xEnd, int y) {
          paintBackground(g, attributes, getMinX(), y, xEnd);
          paintSelectionOnSecondSoftWrapLineIfNecessary(g, visualLine, columnEnd, xEnd, y, primarySelectionStart, primarySelectionEnd);
        }

        @Override
        public void paint(Graphics2D g, VisualLineFragmentsIterator.Fragment fragment, int start, int end, 
                          TextAttributes attributes, float xStart, float xEnd, int y) {
          paintBackground(g, attributes, xStart, y, xEnd - xStart);
        }

        @Override
        public void paintAfterLineEnd(Graphics2D g, Rectangle clip, IterationState it, int columnStart, float x, int y) {
          paintBackground(g, it.getPastLineEndBackgroundAttributes(), x, y, clip.x + clip.width - x);
          int offset = it.getEndOffset();
          SoftWrap softWrap = myEditor.getSoftWrapModel().getSoftWrap(offset);
          if (softWrap == null) {
            paintVirtualSelectionIfNecessary(g, visualLine, virtualSelectionMap, columnStart, x, clip.x + clip.width, y);
          }
          else {
            paintSelectionOnFirstSoftWrapLineIfNecessary(g, visualLine, columnStart, x, clip.x + clip.width, y,
                                                         primarySelectionStart, primarySelectionEnd);
          }
        }
      });
      visLinesIterator.advance();
    }
  }

  private Map<Integer, Couple<Integer>> createVirtualSelectionMap(int startVisualLine, int endVisualLine) {
    HashMap<Integer, Couple<Integer>> map = new HashMap<>();
    for (Caret caret : myEditor.getCaretModel().getAllCarets()) {
      if (caret.hasSelection()) {
        VisualPosition selectionStart = caret.getSelectionStartPosition();
        VisualPosition selectionEnd = caret.getSelectionEndPosition();
        if (selectionStart.line == selectionEnd.line) {
          int line = selectionStart.line;
          if (line >= startVisualLine && line <= endVisualLine) {
            map.put(line, Couple.of(selectionStart.column, selectionEnd.column));
          }
        }
      }
    }
    return map;
  }

  private void paintVirtualSelectionIfNecessary(Graphics2D g,
                                                int visualLine,
                                                Map<Integer, Couple<Integer>> virtualSelectionMap,
                                                int columnStart,
                                                float xStart,
                                                float xEnd,
                                                int y) {
    Couple<Integer> selectionRange = virtualSelectionMap.get(visualLine);
    if (selectionRange == null || selectionRange.second <= columnStart) return;
    float startX = selectionRange.first <= columnStart ? xStart :
                   (float)myView.visualPositionToXY(new VisualPosition(visualLine, selectionRange.first)).getX();
    float endX = (float)Math.min(xEnd, myView.visualPositionToXY(new VisualPosition(visualLine, selectionRange.second)).getX());
    paintBackground(g, myEditor.getColorsScheme().getColor(EditorColors.SELECTION_BACKGROUND_COLOR), startX, y, endX - startX);
  }

  private void paintSelectionOnSecondSoftWrapLineIfNecessary(Graphics2D g, int visualLine, int columnEnd, float xEnd, int y,
                                                             VisualPosition selectionStartPosition, VisualPosition selectionEndPosition) {
    if (selectionStartPosition.equals(selectionEndPosition) ||
        visualLine < selectionStartPosition.line || 
        visualLine > selectionEndPosition.line || 
        visualLine == selectionStartPosition.line && selectionStartPosition.column >= columnEnd) {
      return;
    }

    float startX = (selectionStartPosition.line == visualLine && selectionStartPosition.column > 0) ?
                   (float)myView.visualPositionToXY(selectionStartPosition).getX() : getMinX();
    float endX = (selectionEndPosition.line == visualLine && selectionEndPosition.column < columnEnd) ?
                 (float)myView.visualPositionToXY(selectionEndPosition).getX() : xEnd;
    
    paintBackground(g, myEditor.getColorsScheme().getColor(EditorColors.SELECTION_BACKGROUND_COLOR), startX, y, endX - startX);
  }

  private void paintSelectionOnFirstSoftWrapLineIfNecessary(Graphics2D g, int visualLine, int columnStart, float xStart, float xEnd, int y,
                                                            VisualPosition selectionStartPosition, VisualPosition selectionEndPosition) {
    if (selectionStartPosition.equals(selectionEndPosition) ||
        visualLine < selectionStartPosition.line || 
        visualLine > selectionEndPosition.line || 
        visualLine == selectionEndPosition.line && selectionEndPosition.column <= columnStart) {
      return;
    }

    float startX = selectionStartPosition.line == visualLine && selectionStartPosition.column > columnStart ?
                   (float)myView.visualPositionToXY(selectionStartPosition).getX() : xStart;
    float endX = selectionEndPosition.line == visualLine ?
                 (float)myView.visualPositionToXY(selectionEndPosition).getX() : xEnd;

    paintBackground(g, myEditor.getColorsScheme().getColor(EditorColors.SELECTION_BACKGROUND_COLOR), startX, y, endX - startX);  
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
    int xStartRounded = (int)x;
    int xEndRounded = (int)(x + width);
    g.fillRect(xStartRounded, y, xEndRounded - xStartRounded, myView.getLineHeight());
  }

  private void paintCustomRenderers(final Graphics2D g, final int startOffset, final int endOffset) {
    myEditor.getMarkupModel().processRangeHighlightersOverlappingWith(startOffset, endOffset, highlighter -> {
      CustomHighlighterRenderer customRenderer = highlighter.getCustomRenderer();
      if (customRenderer != null && startOffset < highlighter.getEndOffset() && highlighter.getStartOffset() < endOffset) {
        customRenderer.paint(myEditor, highlighter, g);
      }
      return true;
    });
  }

  private void paintLineMarkersSeparators(final Graphics g,
                                          final Rectangle clip,
                                          MarkupModelEx markupModel,
                                          int startOffset,
                                          int endOffset) {
    markupModel.processRangeHighlightersOverlappingWith(startOffset, endOffset, highlighter -> {
      paintLineMarkerSeparator(highlighter, clip, g);
      return true;
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
    int visualLine = myView.logicalToVisualPosition(new LogicalPosition(line + (marker.getLineSeparatorPlacement() == 
                                                                                SeparatorPlacement.TOP ? 0 : 1), 0), false).line;
    int y = myView.visualLineToY(visualLine) - 1;
    int startX = getMinX();
    int endX = clip.x + clip.width;
    if (isRightMarginShown()) {
      endX = Math.min(endX, getRightMarginX());
    }

    g.setColor(separatorColor);
    if (lineSeparatorRenderer != null) {
      lineSeparatorRenderer.drawLine(g, startX, endX, y);
    }
    else {
      UIUtil.drawLine(g, startX, y, endX, y);
    }
  }


  private void paintTextWithEffects(Graphics2D g, Rectangle clip, int startVisualLine, int endVisualLine,
                                    IterationState.CaretData caretData) {
    final CharSequence text = myDocument.getImmutableCharSequence();
    final LineWhitespacePaintingStrategy whitespacePaintingStrategy = new LineWhitespacePaintingStrategy(myEditor.getSettings());
    boolean paintAllSoftWraps = myEditor.getSettings().isAllSoftWrapsShown();
    int lineCount = myEditor.getVisibleLineCount();
    final int whiteSpaceStrokeWidth = JBUI.scale(1);
    final Stroke whiteSpaceStroke = new BasicStroke(whiteSpaceStrokeWidth);

    LineLayout prefixLayout = myView.getPrefixLayout();
    if (startVisualLine == 0 && prefixLayout != null) {
      g.setColor(myView.getPrefixAttributes().getForegroundColor());
      paintLineLayoutWithEffect(g, prefixLayout, getMinX(), myView.getAscent(),
                                myView.getPrefixAttributes().getEffectColor(), myView.getPrefixAttributes().getEffectType());
    }

    VisualLinesIterator visLinesIterator = new VisualLinesIterator(myEditor, startVisualLine);
    while (!visLinesIterator.atEnd()) {
      int visualLine = visLinesIterator.getVisualLine();
      if (visualLine > endVisualLine || visualLine >= lineCount) break;

      int y = visLinesIterator.getY();
      final boolean paintSoftWraps = paintAllSoftWraps ||
                                     myEditor.getCaretModel().getLogicalPosition().line == visLinesIterator.getStartLogicalLine();
      final int[] currentLogicalLine = new int[] {-1}; 
      
      paintLineFragments(g, clip, visLinesIterator, caretData, y + myView.getAscent(), new LineFragmentPainter() {
        @Override
        public void paintBeforeLineStart(Graphics2D g, TextAttributes attributes, int columnEnd, float xEnd, int y) {
          if (paintSoftWraps) {
            SoftWrapModelImpl softWrapModel = myEditor.getSoftWrapModel();
            int symbolWidth = softWrapModel.getMinDrawingWidthInPixels(SoftWrapDrawingType.AFTER_SOFT_WRAP);
            softWrapModel.doPaint(g, SoftWrapDrawingType.AFTER_SOFT_WRAP, 
                                  (int)xEnd - symbolWidth, y - myView.getAscent(), myView.getLineHeight());
          }
        }

        @Override
        public void paint(Graphics2D g, VisualLineFragmentsIterator.Fragment fragment, int start, int end, 
                          TextAttributes attributes, float xStart, float xEnd, int y) {
          int lineHeight = myView.getLineHeight();
          Inlay inlay = fragment.getCurrentInlay();
          if (inlay != null) {
            inlay.getRenderer().paint(myEditor, g, 
                                      new Rectangle((int) xStart, y - myView.getAscent(), inlay.getWidthInPixels(), lineHeight),
                                      attributes);
            return;
          }
          boolean allowBorder = fragment.getCurrentFoldRegion() != null;
          if (attributes != null && hasTextEffect(attributes.getEffectColor(), attributes.getEffectType(), allowBorder)) {
            paintTextEffect(g, xStart, xEnd, y, attributes.getEffectColor(), attributes.getEffectType(), allowBorder);
          }
          if (attributes != null && attributes.getForegroundColor() != null) {
            g.setColor(attributes.getForegroundColor());
            fragment.draw(g, xStart, y, start, end);
          }
          if (fragment.getCurrentFoldRegion() == null) {
            int logicalLine = fragment.getStartLogicalLine();
            if (logicalLine != currentLogicalLine[0]) {
              whitespacePaintingStrategy.update(text, myDocument.getLineStartOffset(logicalLine), myDocument.getLineEndOffset(logicalLine));
              currentLogicalLine[0] = logicalLine;
            }
            paintWhitespace(g, text, xStart, y, start, end, whitespacePaintingStrategy, fragment, whiteSpaceStroke, whiteSpaceStrokeWidth);
          }
        }

        @Override
        public void paintAfterLineEnd(Graphics2D g, Rectangle clip, IterationState iterationState, int columnStart, float x, int y) {
          int offset = iterationState.getEndOffset();
          SoftWrapModelImpl softWrapModel = myEditor.getSoftWrapModel();
          if (softWrapModel.getSoftWrap(offset) == null) {
            int logicalLine = myDocument.getLineNumber(offset);
            paintLineExtensions(g, logicalLine, x, y);
          }
          else if (paintSoftWraps) {
            softWrapModel.doPaint(g, SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED, 
                                  (int)x, y - myView.getAscent(), myView.getLineHeight());
          }
        }
      });
      visLinesIterator.advance();
    }
    ComplexTextFragment.flushDrawingCache(g);
  }

  private float paintLineLayoutWithEffect(Graphics2D g, LineLayout layout, float x, float y, 
                                  @Nullable Color effectColor, @Nullable EffectType effectType) {
    if (hasTextEffect(effectColor, effectType, false)) {
      paintTextEffect(g, x, x + layout.getWidth(), (int)y, effectColor, effectType, false);
    }
    for (LineLayout.VisualFragment fragment : layout.getFragmentsInVisualOrder(x)) {
      fragment.draw(g, x, y);
      x = fragment.getEndX();
    }
    return x;
  }

  private static boolean hasTextEffect(@Nullable Color effectColor, @Nullable EffectType effectType, boolean allowBorder) {
    return effectColor != null && (effectType == EffectType.LINE_UNDERSCORE ||
                                   effectType == EffectType.BOLD_LINE_UNDERSCORE ||
                                   effectType == EffectType.BOLD_DOTTED_LINE ||
                                   effectType == EffectType.WAVE_UNDERSCORE ||
                                   effectType == EffectType.STRIKEOUT ||
                                   allowBorder && (effectType == EffectType.BOXED || effectType == EffectType.ROUNDED_BOX));
  }

  private void paintTextEffect(Graphics2D g, float xFrom, float xTo, int y, Color effectColor, EffectType effectType, boolean allowBorder) {
    g.setColor(effectColor);
    int xStart = (int)xFrom;
    int xEnd = (int)xTo;
    if (effectType == EffectType.LINE_UNDERSCORE) {
      EffectPainter.LINE_UNDERSCORE.paint(g, xStart, y, xEnd - xStart, myView.getDescent(),
                                          myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));
    }
    else if (effectType == EffectType.BOLD_LINE_UNDERSCORE) {
      EffectPainter.BOLD_LINE_UNDERSCORE.paint(g, xStart, y, xEnd - xStart, myView.getDescent(),
                                               myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));
    }
    else if (effectType == EffectType.STRIKEOUT) {
      EffectPainter.STRIKE_THROUGH.paint(g, xStart, y, xEnd - xStart, myView.getCharHeight(),
                                         myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));
    }
    else if (effectType == EffectType.WAVE_UNDERSCORE) {
      EffectPainter.WAVE_UNDERSCORE.paint(g, xStart, y, xEnd - xStart, myView.getDescent(),
                                          myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));
    }
    else if (effectType == EffectType.BOLD_DOTTED_LINE) {
      EffectPainter.BOLD_DOTTED_UNDERSCORE.paint(g, xStart, y, xEnd - xStart, myView.getDescent(),
                                                 myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));
    }
    else if (allowBorder && (effectType == EffectType.BOXED || effectType == EffectType.ROUNDED_BOX)) {
      drawSimpleBorder(g, xFrom, xTo, y - myView.getAscent(), effectType == EffectType.ROUNDED_BOX);
    }
  }

  private void paintWhitespace(Graphics2D g, CharSequence text, float x, int y, int start, int end,
                               LineWhitespacePaintingStrategy whitespacePaintingStrategy,
                               VisualLineFragmentsIterator.Fragment fragment, Stroke stroke, int strokeWidth) {
    Stroke oldStroke = g.getStroke();
    try {
      g.setColor(myEditor.getColorsScheme().getColor(EditorColors.WHITESPACES_COLOR));
      g.setStroke(stroke); // applied for tab & ideographic space

      boolean isRtl = fragment.isRtl();
      int baseStartOffset = fragment.getStartOffset();
      int startOffset = isRtl ? baseStartOffset - start : baseStartOffset + start;
      y -= 1;

      for (int i = start; i < end; i++) {
        int charOffset = isRtl ? baseStartOffset - i - 1 : baseStartOffset + i;
        char c = text.charAt(charOffset);
        if (" \t\u3000".indexOf(c) >= 0 && whitespacePaintingStrategy.showWhitespaceAtOffset(charOffset)) {
          int startX = (int)fragment.offsetToX(x, startOffset, isRtl ? baseStartOffset - i : baseStartOffset + i);
          int endX = (int)fragment.offsetToX(x, startOffset, isRtl ? baseStartOffset - i - 1 : baseStartOffset + i + 1);

          if (c == ' ') {
            //noinspection SuspiciousNameCombination
            g.fillRect((startX + endX - strokeWidth) / 2, y - strokeWidth + 1, strokeWidth, strokeWidth);
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
            int charHeight = myView.getCharHeight();
            g.drawRect(startX + JBUI.scale(2) + strokeWidth/2, y - charHeight + strokeWidth/2,
                       endX - startX - JBUI.scale(4) - (strokeWidth - 1), charHeight - (strokeWidth - 1));
          }
        }
      }
    } finally {
      g.setStroke(oldStroke);
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
          LineLayout layout = LineLayout.create(myView, info.getText(), info.getFontType());
          g.setColor(info.getColor());
          x = paintLineLayoutWithEffect(g, layout, x, y, info.getEffectColor(), info.getEffectType());
          int currentLineWidth = (int)x - getMinX();
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
    markupModel.processRangeHighlightersOverlappingWith(startOffset, endOffset, highlighter -> {
      if (highlighter.getStartOffset() >= startOffset) {
        paintHighlighterAfterEndOfLine(g, highlighter);
      }
      return true;
    });
  }

  private void paintHighlighterAfterEndOfLine(Graphics2D g, RangeHighlighterEx highlighter) {
    if (!highlighter.isAfterEndOfLine()) {
      return;
    }
    int startOffset = highlighter.getStartOffset();
    int lineEndOffset = myDocument.getLineEndOffset(myDocument.getLineNumber(startOffset));
    if (myEditor.getFoldingModel().isOffsetCollapsed(lineEndOffset)) return;
    Point2D lineEnd = myView.offsetToXY(lineEndOffset, true, false);
    float x = (float)lineEnd.getX();
    int y = (int)lineEnd.getY();
    TextAttributes attributes = highlighter.getTextAttributes();
    paintBackground(g, attributes, x, y, myView.getPlainSpaceWidth());
    if (attributes != null && hasTextEffect(attributes.getEffectColor(), attributes.getEffectType(), false)) {
      paintTextEffect(g, x, x + myView.getPlainSpaceWidth() - 1, y + myView.getAscent(), 
                      attributes.getEffectColor(), attributes.getEffectType(), false);
    }
  }

  private void paintBorderEffect(Graphics2D g,
                                 ClipDetector clipDetector,
                                 EditorHighlighter highlighter,
                                 int clipStartOffset,
                                 int clipEndOffset) {
    HighlighterIterator it = highlighter.createIterator(clipStartOffset);
    while (!it.atEnd() && it.getStart() < clipEndOffset) {
      TextAttributes attributes = it.getTextAttributes();
      if (isBorder(attributes)) {
        paintBorderEffect(g, clipDetector, it.getStart(), it.getEnd(), attributes);
      }
      it.advance();
    }
  }

  private void paintBorderEffect(final Graphics2D g,
                                 final ClipDetector clipDetector,
                                 MarkupModelEx markupModel,
                                 int clipStartOffset,
                                 int clipEndOffset) {
    markupModel.processRangeHighlightersOverlappingWith(clipStartOffset, clipEndOffset, rangeHighlighter -> {
      TextAttributes attributes = rangeHighlighter.getTextAttributes();
      if (isBorder(attributes)) {
        paintBorderEffect(g, clipDetector, rangeHighlighter.getAffectedAreaStartOffset(), rangeHighlighter.getAffectedAreaEndOffset(),
                          attributes);
      }
      return true;
    });
  }

  private static boolean isBorder(TextAttributes attributes) {
    return attributes != null &&
           (attributes.getEffectType() == EffectType.BOXED || attributes.getEffectType() == EffectType.ROUNDED_BOX) &&
           attributes.getEffectColor() != null;
  }

  private void paintBorderEffect(Graphics2D g, ClipDetector clipDetector, int startOffset, int endOffset, TextAttributes attributes) {
    startOffset = DocumentUtil.alignToCodePointBoundary(myDocument, startOffset);
    endOffset = DocumentUtil.alignToCodePointBoundary(myDocument, endOffset);
    if (!clipDetector.rangeCanBeVisible(startOffset, endOffset)) return;
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
    g.setColor(attributes.getEffectColor());
    VisualPosition startPosition = myView.offsetToVisualPosition(startOffset, true, false);
    VisualPosition endPosition = myView.offsetToVisualPosition(endOffset, false, true);
    if (startPosition.line == endPosition.line) {
      int y = myView.visualLineToY(startPosition.line);
      TFloatArrayList ranges = adjustedLogicalRangeToVisualRanges(startOffset, endOffset);
      for (int i = 0; i < ranges.size() - 1; i+= 2) {
        float startX = ranges.get(i);
        float endX = ranges.get(i + 1);
        drawSimpleBorder(g, startX, endX + 1, y, rounded);
      }
    }
    else {
      TFloatArrayList leadingRanges = adjustedLogicalRangeToVisualRanges(
        startOffset, myView.visualPositionToOffset(new VisualPosition(startPosition.line, Integer.MAX_VALUE, true)));
      TFloatArrayList trailingRanges = adjustedLogicalRangeToVisualRanges(
        myView.visualPositionToOffset(new VisualPosition(endPosition.line, 0)), endOffset);
      if (!leadingRanges.isEmpty() && !trailingRanges.isEmpty()) {
        int minX = getMinX();
        int maxX = Math.max(minX + myView.getMaxWidthInLineRange(startPosition.line, endPosition.line - 1) - 1,
                            (int)trailingRanges.get(trailingRanges.size() - 1));
        boolean containsInnerLines = endPosition.line > startPosition.line + 1;
        int lineHeight = myView.getLineHeight() - 1;
        int leadingTopY = myView.visualLineToY(startPosition.line);
        int leadingBottomY = leadingTopY + lineHeight;
        int trailingTopY = myView.visualLineToY(endPosition.line);
        int trailingBottomY = trailingTopY + lineHeight;
        float start = 0;
        float end = 0;
        float leftGap = leadingRanges.get(0) - (containsInnerLines ? minX : trailingRanges.get(0));
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
        end = Math.max(end, maxX);
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
          if (start > minX) {
            drawLine(g, start, trailingTopY, start, trailingTopY - 1, rounded);
            drawLine(g, start, trailingTopY - 1, minX, trailingTopY - 1, rounded);
            drawLine(g, minX, trailingTopY - 1, minX, leadingBottomY + 1, rounded);
          }
          else {
            drawLine(g, minX, trailingTopY, minX, leadingBottomY + 1, rounded);
          }
          lastX = minX;
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

  private void drawSimpleBorder(Graphics2D g, float xStart, float xEnd, float y, boolean rounded) {
    Shape border = getBorderShape(xStart, y, xEnd - xStart, myView.getLineHeight(), rounded);
    if (border != null) {
      Object old = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.fill(border);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, old);
    }
  }

  private static Shape getBorderShape(float x, float y, float width, int height, boolean rounded) {
    if (width <= 0 || height <= 0) return null;
    Shape outer = rounded
                  ? new RoundRectangle2D.Float(x, y, width, height, 2, 2)
                  : new Rectangle2D.Float(x, y, width, height);

    if (width <= 2 || height <= 2) return outer;
    Shape inner = new Rectangle2D.Float(x + 1, y + 1, width - 2, height - 2);

    Path2D path = new Path2D.Float(Path2D.WIND_EVEN_ODD);
    path.append(outer, false);
    path.append(inner, false);
    return path;
  }

  private static void drawLine(Graphics2D g, float x1, int y1, float x2, int y2, boolean rounded) {
    if (rounded) {
      UIUtil.drawLinePickedOut(g, (int) x1, y1, (int)x2, y2);
    } else {
      UIUtil.drawLine(g, (int)x1, y1, (int)x2, y2);
    }
  }

  /**
   * Returns ranges obtained from {@link #logicalRangeToVisualRanges(int, int)}, adjusted for painting range border - lines should
   * line inside target ranges (except for empty range). Target offsets are supposed to be located on the same visual line.
   */
  private TFloatArrayList adjustedLogicalRangeToVisualRanges(int startOffset, int endOffset) {
    TFloatArrayList ranges = logicalRangeToVisualRanges(startOffset, endOffset);
    for (int i = 0; i < ranges.size() - 1; i += 2) {
      float startX = ranges.get(i);
      float endX = ranges.get(i + 1);
      if (startX == endX) {
        if (startX > 0) {
          startX--;
        }
        else {
          endX++;
        }
      }
      else {
        endX--;
      }
      ranges.set(i, startX);
      ranges.set(i + 1, endX);
    }
    return ranges;
  }


    /**
     * Returns a list of pairs of x coordinates for visual ranges representing given logical range. If 
     * {@code startOffset == endOffset}, a pair of equal numbers is returned, corresponding to target position. Target offsets are
     * supposed to be located on the same visual line.
     */
  private TFloatArrayList logicalRangeToVisualRanges(int startOffset, int endOffset) {
    assert startOffset <= endOffset;
    TFloatArrayList result = new TFloatArrayList();
    if (myDocument.getTextLength() == 0) {
      int minX = getMinX();
      result.add(minX);
      result.add(minX);
    }
    else {
      float lastX = -1;
      for (VisualLineFragmentsIterator.Fragment fragment : VisualLineFragmentsIterator.create(myView, startOffset, false)) {
        int minOffset = fragment.getMinOffset();
        int maxOffset = fragment.getMaxOffset();
        if (startOffset == endOffset) {
          lastX = fragment.getEndX();
          Inlay inlay = fragment.getCurrentInlay();
          if (inlay != null && !inlay.isRelatedToPrecedingText()) continue;
          if (startOffset >= minOffset && startOffset < maxOffset) {
            float x = fragment.offsetToX(startOffset);
            result.add(x);
            result.add(x);
            break;
          }
        }
        else if (startOffset < maxOffset && endOffset > minOffset) {
          float x1 = minOffset == maxOffset ? fragment.getStartX() : fragment.offsetToX(Math.max(minOffset, startOffset));
          float x2 = minOffset == maxOffset ? fragment.getEndX() : fragment.offsetToX(Math.min(maxOffset, endOffset));
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
      }
      if (startOffset == endOffset && result.isEmpty() && lastX >= 0) {
        result.add(lastX);
        result.add(lastX);
      }
    }
    return result;
  } 

  private void paintComposedTextDecoration(Graphics2D g) {
    TextRange composedTextRange = myEditor.getComposedTextRange();
    if (composedTextRange != null) {
      Point2D p1 = myView.offsetToXY(Math.min(composedTextRange.getStartOffset(), myDocument.getTextLength()), true, false);
      Point2D p2 = myView.offsetToXY(Math.min(composedTextRange.getEndOffset(), myDocument.getTextLength()), false, true);
  
      int y = (int)p1.getY() + myView.getAscent() + 1;
     
      g.setStroke(IME_COMPOSED_TEXT_UNDERLINE_STROKE);
      g.setColor(myEditor.getColorsScheme().getDefaultForeground());
      UIUtil.drawLine(g, (int)p1.getX(), y, (int)p2.getX(), y);
    }
  }

  private void paintCaret(Graphics2D g_) {
    EditorImpl.CaretRectangle[] locations = myEditor.getCaretLocations(true);
    if (locations == null) return;

    Graphics2D g = IdeBackgroundUtil.getOriginalGraphics(g_);
    int nominalLineHeight = myView.getNominalLineHeight();
    int topOverhang = myView.getTopOverhang();
    EditorSettings settings = myEditor.getSettings();
    Color caretColor = myEditor.getColorsScheme().getColor(EditorColors.CARET_COLOR);
    if (caretColor == null) caretColor = new JBColor(CARET_DARK, CARET_LIGHT);
    int minX = getMinX();
    for (EditorImpl.CaretRectangle location : locations) {
      float x = location.myPoint.x;
      int y = location.myPoint.y - topOverhang;
      Caret caret = location.myCaret;
      CaretVisualAttributes attr = caret == null ? CaretVisualAttributes.DEFAULT : caret.getVisualAttributes();
      g.setColor(attr.getColor() != null ? attr.getColor() : caretColor);
      boolean isRtl = location.myIsRtl;
      if (myEditor.isInsertMode() != settings.isBlockCursor()) {
        int lineWidth = JBUI.scale(attr.getWidth(settings.getLineCursorWidth()));
        // fully cover extra character's pixel which can appear due to antialiasing
        // see IDEA-148843 for more details
        if (x > minX && lineWidth > 1) x -= 1 / JBUI.sysScale(g);
        g.fill(new Rectangle2D.Float(x, y, lineWidth, nominalLineHeight));
        if (myDocument.getTextLength() > 0 && caret != null &&
            !myView.getTextLayoutCache().getLineLayout(caret.getLogicalPosition().line).isLtr()) {
          GeneralPath triangle = new GeneralPath(Path2D.WIND_NON_ZERO, 3);
          triangle.moveTo(isRtl ? x + lineWidth : x, y);
          triangle.lineTo(isRtl ? x + lineWidth - CARET_DIRECTION_MARK_SIZE : x + CARET_DIRECTION_MARK_SIZE, y);
          triangle.lineTo(isRtl ? x + lineWidth : x, y + CARET_DIRECTION_MARK_SIZE);
          triangle.closePath();
          g.fill(triangle);
        }
      }
      else {
        int width = location.myWidth;
        float startX = Math.max(minX, isRtl ? x - width : x);
        g.fill(new Rectangle2D.Float(startX, y, width, nominalLineHeight - 1));
        if (myDocument.getTextLength() > 0 && caret != null) {
          int charCount = DocumentUtil.isSurrogatePair(myDocument, caret.getOffset()) ? 2 : 1;
          int targetVisualColumn = caret.getVisualPosition().column;
          for (VisualLineFragmentsIterator.Fragment fragment : VisualLineFragmentsIterator.create(myView,
                                                                                                  caret.getVisualLineStart(), 
                                                                                                  false)) {
            if (fragment.getCurrentInlay() != null) continue;
            int startVisualColumn = fragment.getStartVisualColumn();
            int endVisualColumn = fragment.getEndVisualColumn();
            if (startVisualColumn < targetVisualColumn && endVisualColumn > targetVisualColumn ||
                startVisualColumn == targetVisualColumn && !isRtl ||
                endVisualColumn == targetVisualColumn && isRtl) {
              g.setColor(ColorUtil.isDark(caretColor) ? CARET_LIGHT : CARET_DARK);
              fragment.draw(g, startX, y + topOverhang + myView.getAscent(),
                            targetVisualColumn - startVisualColumn - (isRtl ? charCount : 0),
                            targetVisualColumn - startVisualColumn + (isRtl ? 0 : charCount));
              break;
            }
          }
          ComplexTextFragment.flushDrawingCache(g);
        }
      }
    }
  }
  
  void repaintCarets() {
    EditorImpl.CaretRectangle[] locations = myEditor.getCaretLocations(false);
    if (locations == null) return;
    int nominalLineHeight = myView.getNominalLineHeight();
    int topOverhang = myView.getTopOverhang();
    for (EditorImpl.CaretRectangle location : locations) {
      int x = location.myPoint.x;
      int y = location.myPoint.y - topOverhang;
      int width = Math.max(location.myWidth, CARET_DIRECTION_MARK_SIZE);
      myEditor.getContentComponent().repaintEditorComponent(x - width, y, width * 2, nominalLineHeight);
    }
  }

  private void paintLineFragments(Graphics2D g, Rectangle clip, VisualLinesIterator visLineIterator, IterationState.CaretData caretData,
                                  int y, LineFragmentPainter painter) {
    int visualLine = visLineIterator.getVisualLine();
    float x = getMinX() + (visualLine == 0 ? myView.getPrefixTextWidthInPixels() : 0);
    int offset = visLineIterator.getVisualLineStartOffset();
    int visualLineEndOffset = visLineIterator.getVisualLineEndOffset();
    IterationState it = null;
    int prevEndOffset = -1;
    boolean firstFragment = true;
    int maxColumn = 0;
    for (VisualLineFragmentsIterator.Fragment fragment : VisualLineFragmentsIterator.create(myView, visLineIterator, null)) {
      int fragmentStartOffset = fragment.getStartOffset();
      int start = fragmentStartOffset;
      int end = fragment.getEndOffset();
      x = fragment.getStartX();
      if (firstFragment) {
        firstFragment = false;
        SoftWrap softWrap = myEditor.getSoftWrapModel().getSoftWrap(offset);
        if (softWrap != null) {
          prevEndOffset = offset;
          it = new IterationState(myEditor, offset == 0 ? 0 : DocumentUtil.getPreviousCodePointOffset(myDocument, offset), visualLineEndOffset,
                                  caretData, false, false, false, false);
          if (it.getEndOffset() <= offset) {
            it.advance();
          }
          if (x >= clip.getMinX()) {
            painter.paintBeforeLineStart(g, it.getStartOffset() == offset ? it.getBeforeLineStartBackgroundAttributes() :
                                            it.getMergedAttributes(), fragment.getStartVisualColumn(), x, y);
          }
        }
      }
      FoldRegion foldRegion = fragment.getCurrentFoldRegion();
      if (foldRegion == null) {
        if (start != prevEndOffset) {
          it = new IterationState(myEditor, start, fragment.isRtl() ? offset : visualLineEndOffset, 
                                  caretData, false, false, false, fragment.isRtl());
        }
        prevEndOffset = end;
        assert it != null;
        if (start == end) { // special case of inlays
          if (start == it.getEndOffset() && !it.atEnd()) {
            it.advance();
          }
          TextAttributes attributes = it.getStartOffset() == start ? it.getBreakAttributes() : it.getMergedAttributes();
          float xNew = fragment.getEndX();
          if (xNew >= clip.getMinX()) {
            painter.paint(g, fragment, 0, 0, attributes, x, xNew, y);
          }
          x = xNew;
        }
        else {
          while (fragment.isRtl() ? start > end : start < end) {
            if (fragment.isRtl() ? it.getEndOffset() >= start : it.getEndOffset() <= start) {
              assert !it.atEnd();
              it.advance();
            }
            TextAttributes attributes = it.getMergedAttributes();
            int curEnd = fragment.isRtl() ? Math.max(it.getEndOffset(), end) : Math.min(it.getEndOffset(), end);
            float xNew = fragment.offsetToX(x, start, curEnd);
            if (xNew >= clip.getMinX()) {
              painter.paint(g, fragment,
                            fragment.isRtl() ? fragmentStartOffset - start : start - fragmentStartOffset,
                            fragment.isRtl() ? fragmentStartOffset - curEnd : curEnd - fragmentStartOffset,
                            attributes, x, xNew, y);
            }
            x = xNew;
            start = curEnd;
          }
        }
      }
      else {
        float xNew = fragment.getEndX();
        if (xNew >= clip.getMinX()) {
          painter.paint(g, fragment, 0, fragment.getEndVisualColumn() - fragment.getStartVisualColumn(), 
                        getFoldRegionAttributes(foldRegion), x, xNew, y);
        }
        x = xNew;
        prevEndOffset = -1;
        it = null;
      }
      if (x > clip.getMaxX()) return;
      maxColumn = fragment.getEndVisualColumn();
    }
    if (it == null || it.getEndOffset() != visualLineEndOffset) {
      it = new IterationState(myEditor, visualLineEndOffset == offset ? visualLineEndOffset
                                                                      : DocumentUtil.getPreviousCodePointOffset(myDocument, visualLineEndOffset),
                              visualLineEndOffset, caretData, false, false, false, false);
    }
    if (!it.atEnd()) {
      it.advance();
    }
    assert it.atEnd();
    painter.paintAfterLineEnd(g, clip, it, maxColumn, x, y);
  }

  private TextAttributes getFoldRegionAttributes(FoldRegion foldRegion) {
    TextAttributes selectionAttributes = isSelected(foldRegion) ? myEditor.getSelectionModel().getTextAttributes() : null;
    TextAttributes foldAttributes = myEditor.getFoldingModel().getPlaceholderAttributes();
    TextAttributes defaultAttributes = getDefaultAttributes();
    return mergeAttributes(mergeAttributes(selectionAttributes, foldAttributes), defaultAttributes);
  }

  @SuppressWarnings("UseJBColor")
  private TextAttributes getDefaultAttributes() {
    TextAttributes attributes = myEditor.getColorsScheme().getAttributes(HighlighterColors.TEXT);
    if (attributes.getForegroundColor() == null) attributes.setForegroundColor(Color.black);
    if (attributes.getBackgroundColor() == null) attributes.setBackgroundColor(Color.white);
    return attributes;
  }

  private static boolean isSelected(FoldRegion foldRegion) {
    int regionStart = foldRegion.getStartOffset();
    int regionEnd = foldRegion.getEndOffset();
    int[] selectionStarts = foldRegion.getEditor().getSelectionModel().getBlockSelectionStarts();
    int[] selectionEnds = foldRegion.getEditor().getSelectionModel().getBlockSelectionEnds();
    for (int i = 0; i < selectionStarts.length; i++) {
      int start = selectionStarts[i];
      int end = selectionEnds[i];
      if (regionStart >= start && regionEnd <= end) return true;
    }
    return false;
  }

  private static TextAttributes mergeAttributes(TextAttributes primary, TextAttributes secondary) {
    if (primary == null) return secondary;
    if (secondary == null) return primary;
    return new TextAttributes(primary.getForegroundColor() == null ? secondary.getForegroundColor() : primary.getForegroundColor(),
                              primary.getBackgroundColor() == null ? secondary.getBackgroundColor() : primary.getBackgroundColor(),
                              primary.getEffectColor() == null ? secondary.getEffectColor() : primary.getEffectColor(),
                              primary.getEffectType() == null ? secondary.getEffectType() : primary.getEffectType(),
                              primary.getFontType() == Font.PLAIN ? secondary.getFontType() : primary.getFontType());
  }

  @Override
  public void drawChars(@NotNull Graphics g, @NotNull char[] data, int start, int end, int x, int y, Color color, FontInfo fontInfo) {
    g.setFont(fontInfo.getFont());
    g.setColor(color);
    g.drawChars(data, start, end - start, x, y);
  }

  interface LineFragmentPainter {
    void paintBeforeLineStart(Graphics2D g, TextAttributes attributes, int columnEnd, float xEnd, int y);
    void paint(Graphics2D g, VisualLineFragmentsIterator.Fragment fragment, int start, int end, TextAttributes attributes,
               float xStart, float xEnd, int y);
    void paintAfterLineEnd(Graphics2D g, Rectangle clip, IterationState iterationState, int columnStart, float x, int y);
  }

  private static class LineWhitespacePaintingStrategy {
    private final boolean myWhitespaceShown;
    private final boolean myLeadingWhitespaceShown;
    private final boolean myInnerWhitespaceShown;
    private final boolean myTrailingWhitespaceShown;

    // Offsets on current line where leading whitespace ends and trailing whitespace starts correspondingly.
    private int currentLeadingEdge;
    private int currentTrailingEdge;

    public LineWhitespacePaintingStrategy(EditorSettings settings) {
      myWhitespaceShown = settings.isWhitespacesShown();
      myLeadingWhitespaceShown = settings.isLeadingWhitespaceShown();
      myInnerWhitespaceShown = settings.isInnerWhitespaceShown();
      myTrailingWhitespaceShown = settings.isTrailingWhitespaceShown();
    }

    private void update(CharSequence chars, int lineStart, int lineEnd) {
      if (myWhitespaceShown
          && (myLeadingWhitespaceShown || myInnerWhitespaceShown || myTrailingWhitespaceShown)
          && !(myLeadingWhitespaceShown && myInnerWhitespaceShown && myTrailingWhitespaceShown)) {
        currentTrailingEdge = CharArrayUtil.shiftBackward(chars, lineStart, lineEnd - 1, WHITESPACE_CHARS) + 1;
        currentLeadingEdge = CharArrayUtil.shiftForward(chars, lineStart, currentTrailingEdge, WHITESPACE_CHARS);
      }
    }

    private boolean showWhitespaceAtOffset(int offset) {
      return myWhitespaceShown
             && (offset < currentLeadingEdge ? myLeadingWhitespaceShown :
                 offset >= currentTrailingEdge ? myTrailingWhitespaceShown :
                 myInnerWhitespaceShown);
    }
  }
}
