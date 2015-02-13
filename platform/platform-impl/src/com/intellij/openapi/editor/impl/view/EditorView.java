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

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.*;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.Processor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class EditorView implements PrioritizedDocumentListener, Disposable {
  private final EditorImpl myEditor;
  private final DocumentEx myDocument;
  private final FontRenderContext myFontRenderContext;
  
  private final ArrayList<LineView> myLines = new ArrayList<LineView>();
  private int myWidthInPixels;
  
  private int myMaxLineWithExtensionWidth;
  private int myWidestLineWithExtension;
    
  private int myPlainSpaceWidth;
  
  private int myDocumentChangeOldEndLine;
  
  private TextLayout myPrefixLayout;
  private TextAttributes myPrefixAttributes;
  
  public EditorView(EditorImpl editor) {
    myFontRenderContext = createFontRenderContext();
    myEditor = editor;
    myDocument = editor.getDocument();
    myDocument.addDocumentListener(this, this);
    myPlainSpaceWidth = EditorUtil.getSpaceWidth(Font.PLAIN, editor);
    invalidateCachedWidth();
    invalidateLines(0, -1, getDocumentLineCount() - 1);
  }

  private static FontRenderContext createFontRenderContext() {
    Graphics2D g = (Graphics2D)UIUtil.createImage(1, 1, BufferedImage.TYPE_INT_RGB).getGraphics();
    try {
      UISettings.setupAntialiasing(g);
      return g.getFontRenderContext();
    }
    finally {
      g.dispose();
    }
  }

  @Override
  public void dispose() {
    myLines.clear();
  }

  @NotNull
  public LogicalPosition offsetToLogicalPosition(int offset) {
    int textLength = myDocument.getTextLength();
    if (offset < 0 || textLength == 0) {
      return new LogicalPosition(0, 0);
    }
    offset = Math.min(offset, textLength);
    int line = myDocument.getLineNumber(offset);
    int offsetInsideLine = offset - myDocument.getLineStartOffset(line);
    int column = line < myLines.size() ? getLineRenderer(line).offsetToColumn(offsetInsideLine, myPlainSpaceWidth) : offsetInsideLine;
    return new LogicalPosition(line, column);
  }

  public int logicalPositionToOffset(@NotNull LogicalPosition pos) {
    int line = pos.line;
    if (line >= getDocumentLineCount()) {
      return myDocument.getTextLength();
    }
    else {
      return myDocument.getLineStartOffset(line) + 
             (line < myLines.size() ? getLineRenderer(line).columnToOffset(pos.column, myPlainSpaceWidth)[0] : pos.column);
    }
  }

  @NotNull
  public VisualPosition logicalToVisualPosition(@NotNull LogicalPosition pos) {
    return new VisualPosition(pos.line, pos.column);
  }

  @NotNull
  public LogicalPosition visualToLogicalPosition(@NotNull VisualPosition pos) {
    return new LogicalPosition(pos.line, pos.column);
  }

  @NotNull
  public VisualPosition offsetToVisualPosition(int offset) {
    return logicalToVisualPosition(offsetToLogicalPosition(offset));
  }

  public int offsetToVisualLine(int offset) {
    if (offset <= 0) {
      return 0;
    }
    if (offset >= myDocument.getTextLength()) {
      return Math.max(0, getDocumentLineCount() - 1);
    }
    return myDocument.getLineNumber(offset);
  }

  @NotNull
  public VisualPosition xyToVisualPosition(@NotNull Point p) {
    int line = myEditor.yPositionToVisibleLine(Math.max(p.y, 0));
    int prefixShift = line == 0 ? getPrefixTextWidthInPixels() : 0;
    if (line < myLines.size()) {
      LineView lineView = getLineRenderer(line);
      return new VisualPosition(line, lineView.xToColumn(p.x - prefixShift, myPlainSpaceWidth, myEditor.getSettings().isCaretInsideTabs()));
    }
    return new VisualPosition(line, LineView.spacePixelsToColumns(p.x - prefixShift, myPlainSpaceWidth));
  }

  @NotNull
  public Point visualPositionToXY(@NotNull VisualPosition pos) {
    int y = myEditor.visibleLineToY(pos.line);
    int prefixShift = pos.line == 0 ? getPrefixTextWidthInPixels() : 0;
    if (pos.line < myLines.size()) {
      LineView lineView = getLineRenderer(pos.line);
      return new Point(lineView.columnToX(pos.column, myPlainSpaceWidth) + prefixShift, y);
    }
    else {
      return new Point(LineView.spaceColumnsToPixels(pos.column, myPlainSpaceWidth) + prefixShift, y);
    }
  }

  public Dimension getPreferredSize() {
    int width = getPreferredWidth();
    if (!myDocument.isInBulkUpdate()) {
      for (Caret caret : myEditor.getCaretModel().getAllCarets()) {
        if (caret.isUpToDate()) {
          int caretX = visualPositionToXY(caret.getVisualPosition()).x;
          width = Math.max(caretX, width);
        }
      }
    }
    width += myEditor.getSettings().getAdditionalColumnsCount() * myPlainSpaceWidth;
    return new Dimension(width, myEditor.getPreferredHeight());
  }

  public Dimension getContentSize() {
    return getPreferredSize();
  }
  
  private int getPreferredWidth() {
    if (myWidthInPixels < 0) {
      myWidthInPixels = calculatePreferredWidth();
    }
    validateMaxLineWithExtension();
    return Math.max(myWidthInPixels, myMaxLineWithExtensionWidth);
  }

  private void validateMaxLineWithExtension() {
    if (myMaxLineWithExtensionWidth > 0) {
      Project project = myEditor.getProject();
      VirtualFile virtualFile = myEditor.getVirtualFile();
      if (project != null && virtualFile != null) {
        for (EditorLinePainter painter : EditorLinePainter.EP_NAME.getExtensions()) {
          Collection<LineExtensionInfo> extensions = painter.getLineExtensions(project, virtualFile, myWidestLineWithExtension);
          if (extensions != null && !extensions.isEmpty()) {
            return;
          }
        }
      }    
      myMaxLineWithExtensionWidth = 0;
    }
  }

  private int calculatePreferredWidth() {
    int maxWidth = getPrefixTextWidthInPixels() + (myLines.isEmpty() ? 0 : getLineRenderer(0).getWidthInPixels());
    for (int line = 1; line < myLines.size(); line++) {
      LineView lineView = myLines.get(line);
      if (lineView != null) {
        maxWidth = Math.max(maxWidth, lineView.getWidthInPixels());
      }
    }
    int longestLineNumber = guessLongestLineNumber();
    if (longestLineNumber > 0 && longestLineNumber < myLines.size()) {
      maxWidth = Math.max(maxWidth, getLineRenderer(longestLineNumber).getWidthInPixels());
    }
    return maxWidth;
  }
  
  private int guessLongestLineNumber() {
    int lineCount = getDocumentLineCount();
    int longestLineNumber = -1;
    int longestLine = -1;
    for (int line = 0; line < lineCount; line++) {
      int lineChars = myDocument.getLineEndOffset(line) - myDocument.getLineStartOffset(line);
      if (lineChars > longestLine) {
        longestLine = lineChars;
        longestLineNumber = line;
      }
    }
    return longestLineNumber;
  }
  
  public int getMaxWidthInRange(int startOffset, int endOffset) {
    return getMaxWidthInLineRange(offsetToLogicalPosition(startOffset).line, offsetToLogicalPosition(endOffset).line);
  }
  
  private int getMaxWidthInLineRange(int startLine, int endLine) {
    int maxWidth = 0;
    for (int line = startLine; line <= endLine && line < myLines.size(); line++) {
      maxWidth = Math.max(maxWidth, getLineWidth(line));
    }
    return maxWidth;
  }

  private int getLineWidth(int line) {
    int length = getLineRenderer(line).getWidthInPixels();
    if (line == 0) length += getPrefixTextWidthInPixels();
    return length;
  }
  
  public void paint(Graphics2D g) {
    Rectangle clip = g.getClipBounds();

    if (myEditor.getContentComponent().isOpaque()) {
      g.setColor(myEditor.getBackgroundColor());
      g.fillRect(clip.x, clip.y, clip.width, clip.height);
    }
    
    if (paintPlaceholderText(g)) return;

    int startLine = myEditor.yPositionToVisibleLine(Math.max(clip.y,0));
    int endLine = myEditor.yPositionToVisibleLine(Math.max(clip.y + clip.height,0));
    int lineCount = getDocumentLineCount();
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
    
    paintCaret(g, clip);
  }

  private void paintCaret(Graphics2D g, Rectangle clip) {
    EditorImpl.CaretRectangle[] locations = myEditor.getCaretLocations();
    if (locations == null) return;
    for (EditorImpl.CaretRectangle location : locations) {
      paintCaretAt(g, clip, location.myPoint.x, location.myPoint.y, location.myCaret);
    }
  }

  private void paintCaretAt(Graphics2D g, Rectangle clip, int x, int y, Caret caret) {
    Rectangle viewRectangle = myEditor.getScrollingModel().getVisibleArea();
    if (x - viewRectangle.x < 0) {
      return;
    }
    int lineHeight = myEditor.getLineHeight();
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
      int offset = caret.getOffset();
      int line = myDocument.getLineNumber(offset);
      TextLayoutHighlightShape shape = null;
      if (offset < myDocument.getLineEndOffset(line)) {
        LineView lineView = myLines.get(line);
        int lineStartOffset = myDocument.getLineStartOffset(line);
        shape = lineView.getRangeShape(myEditor, offset - lineStartOffset, offset - lineStartOffset + 1);
      }
      Graphics2D window = (Graphics2D)g.create(0, myEditor.visibleLineToY(line), clip.x + clip.width, lineHeight);
      try {
        Color background = myEditor.getCaretModel().getTextAttributes().getBackgroundColor();
        if (background == null) background = myEditor.getBackgroundColor();
        window.setXORMode(background);
        if (shape == null) {
          window.fillRect(x, 0, myPlainSpaceWidth, lineHeight);
        }
        else {
          shape.fill(window);          
        }
        window.setPaintMode();
      }
      finally {
        window.dispose();
      }
    }
  }

  private void paintBackground(Graphics2D g, Rectangle clip, int startLine, int endLine) {
    for (int line = startLine; line <= endLine; line++) {
      Graphics2D window = (Graphics2D)g.create(0, myEditor.visibleLineToY(line), clip.x + clip.width, myEditor.getLineHeight());
      try {
        if (line == 0 && myPrefixLayout != null) {
          LineView.paintBackground(window, myEditor, myPrefixLayout, 0, myPrefixLayout.getCharacterCount(), myPrefixAttributes);
          window.translate(getPrefixTextWidthInPixels(), 0);
        }
        if (line >= myLines.size()) break;
        LineView lineView = getLineRenderer(line);
        lineView.paintBackground(window, myEditor, myDocument.getLineStartOffset(line), myDocument.getLineEndOffset(line),
                                 myPlainSpaceWidth);
      }
      finally {
        window.dispose();
      }
    }
  }
  
  private void paintTextWithEffects(Graphics2D g, Rectangle clip, int startLine, int endLine) {
    CharSequence text = myDocument.getImmutableCharSequence();
    EditorImpl.LineWhitespacePaintingStrategy whitespacePaintingStrategy = myEditor.new LineWhitespacePaintingStrategy();
    for (int line = startLine; line <= endLine; line++) {
      Graphics2D window = (Graphics2D)g.create(0, myEditor.visibleLineToY(line), clip.x + clip.width, myEditor.getLineHeight());
      try {
        if (line == 0 && myPrefixLayout != null) {
          paintTextLayoutWithEffect(window, myPrefixLayout, myPrefixAttributes.getEffectColor(), myPrefixAttributes.getEffectType());
          window.translate(getPrefixTextWidthInPixels(), 0);
        }
        if (line >= myLines.size()) break;
        LineView lineView = getLineRenderer(line);
        int lineStartOffset = myDocument.getLineStartOffset(line);
        int lineEndOffset = myDocument.getLineEndOffset(line);
        whitespacePaintingStrategy.update(text, lineStartOffset, lineEndOffset);
        lineView.paintTextAndEffects(window, myEditor, lineStartOffset, lineEndOffset, whitespacePaintingStrategy, myPlainSpaceWidth);
        window.translate(lineView.getWidthInPixels(), 0);
        paintLineExtensions(window, line);
      }
      finally {
        window.dispose();
      }
    }
  }

  public void setPrefix(String prefixText, TextAttributes attributes) {
    myPrefixLayout = prefixText == null || prefixText.isEmpty() ? null : 
                     createTextLayout(prefixText, attributes.getFontType(), attributes.getForegroundColor());
    myPrefixAttributes = attributes;
  }
  
  private TextLayout createTextLayout(String text, int fontStyle, Color fontColor) {
    AttributedString string = LineView.createAttributedString(text);
    LineView.setFontAttributes(string, 0, text.length(), fontStyle, fontColor, myEditor.getColorsScheme().getFontPreferences(), null);
    return new TextLayout(string.getIterator(), myFontRenderContext);
  }

  private void paintTextLayoutWithEffect(Graphics2D g, TextLayout textLayout, Color effectColor, EffectType effectType) {
    textLayout.draw(g, 0, myEditor.getAscent());
    if (LineView.hasTextEffect(effectColor, effectType)) {
      TextLayoutHighlightShape shape = new TextLayoutHighlightShape(textLayout,
                                                                    0,
                                                                    textLayout.getCharacterCount(),
                                                                    myEditor.getAscent(),
                                                                    myEditor.getLineHeight());
      Graphics2D window = (Graphics2D)g.create();
      try {
        shape.setAsClip(window);
        Rectangle clipBounds = window.getClipBounds();
        window.setColor(effectColor);
        LineView.paintTextEffect(window, myEditor, (int)clipBounds.getMinX(), (int)clipBounds.getMaxX(), myEditor.getAscent(), effectType);
      }
      finally {
        window.dispose();
      }
    }
  }

  public int getPrefixTextWidthInPixels() {
    return myPrefixLayout == null ? 0 : (int)myPrefixLayout.getAdvance();
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

  private void paintLineMarkerSeparator(RangeHighlighter marker, Rectangle clip, Graphics g) {
    Color separatorColor = marker.getLineSeparatorColor();
    LineSeparatorRenderer lineSeparatorRenderer = marker.getLineSeparatorRenderer();
    if (separatorColor == null && lineSeparatorRenderer == null) {
      return;
    }
    int line = myDocument.getLineNumber(marker.getLineSeparatorPlacement() == SeparatorPlacement.TOP
                                        ? marker.getStartOffset()
                                        : marker.getEndOffset());
    int y = myEditor.visibleLineToY(line + (marker.getLineSeparatorPlacement() == SeparatorPlacement.TOP ? 0 : 1));

    y -= 1;
    if (y + myEditor.getLineHeight() < clip.y || y > clip.y + clip.height) return;

    int endShift = clip.x + clip.width;
    EditorSettings settings = myEditor.getSettings();
    if (settings.isRightMarginShown() && myEditor.getColorsScheme().getColor(EditorColors.RIGHT_MARGIN_COLOR) != null) {
      endShift = Math.min(endShift, settings.getRightMargin(myEditor.getProject()) * myPlainSpaceWidth);
    }

    g.setColor(separatorColor);
    if (lineSeparatorRenderer != null) {
      lineSeparatorRenderer.drawLine(g, 0, endShift, y);
    }
    else {
      UIUtil.drawLine(g, 0, y, endShift, y);
    }
  }

  private void paintHighlighterAfterEndOfLine(Graphics2D g, RangeHighlighterEx highlighter) {
    if (!highlighter.isAfterEndOfLine()) {
      return;
    }
    int offset = highlighter.getStartOffset();
    int line = myDocument.getLineNumber(offset);
    if (line >= myLines.size()) return;

    int x = getLineWidth(line);
    int y = myEditor.visibleLineToY(line);
    TextAttributes attributes = highlighter.getTextAttributes();
    Color backgroundColor = getBackgroundColor(myEditor, attributes);
    if (backgroundColor != null) {
      g.setColor(backgroundColor);
      g.fillRect(x, y, myPlainSpaceWidth, myEditor.getLineHeight());
    }
    if (attributes != null && attributes.getEffectColor() != null) {
      LineView.paintTextEffect(g, myEditor, x, x + myPlainSpaceWidth - 1, myEditor.getAscent(), attributes.getEffectType());
    }
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
    g.drawString(hintText.toString(), 0, myEditor.getAscent());
    return true;
  }

  private void paintRightMargin(Graphics g, Rectangle clip) {
    EditorSettings settings = myEditor.getSettings();
    Color rightMargin = myEditor.getColorsScheme().getColor(EditorColors.RIGHT_MARGIN_COLOR);
    if (!settings.isRightMarginShown() || rightMargin == null) return;
    
    int x = settings.getRightMargin(myEditor.getProject()) * myPlainSpaceWidth;
    g.setColor(rightMargin);
    UIUtil.drawLine(g, x, clip.y, x, clip.y + clip.height);
  }

  static Color getBackgroundColor(EditorImpl editor, TextAttributes attributes) {
    if (attributes == null) return null;
    final Color attrColor = attributes.getBackgroundColor();
    return attrColor == null ||
           attrColor.equals(editor.getColorsScheme().getDefaultBackground()) ||
           attrColor.equals(editor.getBackgroundColor()) ?
           null : attrColor;
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
    if (startLine >= myLines.size() || endLine >= myLines.size()) return;

    int startLineStartOffset = myDocument.getLineStartOffset(startLine);
    int start = startOffset - startLineStartOffset;
    int end = endOffset - myDocument.getLineStartOffset(endLine);
    LineView startLineView = getLineRenderer(startLine);
    LineView endLineView = getLineRenderer(endLine);
    int maxWidth = getMaxWidthInLineRange(startLine, endLine);
    Graphics2D window = (Graphics2D)g.create(0, 
                                             myEditor.visibleLineToY(startLine), 
                                             maxWidth + 2, // to fit empty box at line end
                                             (endLine - startLine + 1) * myEditor.getLineHeight());
    try {
      window.setColor(attributes.getEffectColor());
      boolean rounded = attributes.getEffectType() == EffectType.ROUNDED_BOX;
      if (startLine == endLine) {
        startLineView.getRangeShape(myEditor, start, end).draw(window, rounded);
      }
      else {
        TextLayoutHighlightShape leading = startLineView.getRangeShape(myEditor, start,
                                                                           myDocument.getLineEndOffset(startLine) - startLineStartOffset);
        TextLayoutHighlightShape trailing = endLineView.getRangeShape(myEditor, 0, end);
        TextLayoutHighlightShape.drawCombined(window, leading, trailing,
                                              (endLine - startLine) * myEditor.getLineHeight(), maxWidth,
                                              startLineView.getVisualLineEndOffset() >= start, rounded);
      }
    }
    finally {
      window.dispose();
    }
  }

  private void paintLineExtensions(Graphics2D g, int line) {
    Project project = myEditor.getProject();
    VirtualFile virtualFile = myEditor.getVirtualFile();
    if (project == null || virtualFile == null) return;
    for (EditorLinePainter painter : EditorLinePainter.EP_NAME.getExtensions()) {
      Collection<LineExtensionInfo> extensions = painter.getLineExtensions(project, virtualFile, line);
      if (extensions != null) {
        for (LineExtensionInfo info : extensions) {
          TextLayout textLayout = createTextLayout(info.getText(), info.getFontType(), info.getColor());
          Graphics2D gCopy = (Graphics2D)g.create();
          try {
            paintTextLayoutWithEffect(gCopy, textLayout, info.getEffectColor(), info.getEffectType());
          }
          finally {
            gCopy.dispose();
          }
          g.translate(textLayout.getAdvance(), 0);
          int currentLineWidth = (int)g.getTransform().getTranslateX();
          if (currentLineWidth > myMaxLineWithExtensionWidth) {
            myMaxLineWithExtensionWidth = currentLineWidth;
            myWidestLineWithExtension = line;
          }
        }
      }
    }
  }

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.EDITOR_TEXT_LAYOUT_CACHE;
  }

  @Override
  public void beforeDocumentChange(DocumentEvent event) {
    myDocumentChangeOldEndLine = getAdjustedLineNumber(event.getOffset() + event.getOldLength());
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    invalidateCachedWidth();
    int startLine = myDocument.getLineNumber(event.getOffset());
    int newEndLine = getAdjustedLineNumber(event.getOffset() + event.getNewLength());
    invalidateLines(startLine, myDocumentChangeOldEndLine, newEndLine);
  }

  private int getAdjustedLineNumber(int offset) {
    return myDocument.getTextLength() == 0 ? -1 : myDocument.getLineNumber(offset);
  }

  private int getDocumentLineCount() {
    return myDocument.getTextLength() == 0 ? 0 : myDocument.getLineCount(); // temporary workaround for a DocumentImpl bug
  }
  
  public void reinitSettings() {
    myPlainSpaceWidth = EditorUtil.getSpaceWidth(Font.PLAIN, myEditor);
    invalidateCachedWidth();
    invalidateAll();
  }
  
  public void invalidateCachedWidth() {
    myWidthInPixels = -1;
  }

  public void invalidateAll() {
    int maxLine = myLines.size() - 1;
    invalidateLines(0, maxLine, maxLine);
  }

  public void invalidateLines(int startLine, int endLine) {
    if (startLine > endLine || startLine >= myLines.size() || endLine < 0) {
      return;
    }
    startLine = Math.max(0, startLine);
    endLine = Math.min(myLines.size() - 1, endLine);
    invalidateLines(startLine, endLine, endLine);
  }

  private void invalidateLines(int startLine, int oldEndLine, int newEndLine) {
    int endLine = Math.min(oldEndLine, newEndLine);
    for (int line = startLine; line <= endLine; line++) {
      myLines.set(line, null);
    }
    if (oldEndLine < newEndLine) {
      myLines.addAll(oldEndLine + 1, Collections.nCopies(newEndLine - oldEndLine, (LineView)null));
    } else if (oldEndLine > newEndLine) {
      myLines.subList(newEndLine + 1, oldEndLine + 1).clear();
    }
  }

  public void reinitAllForEditorTextFieldCellRenderer() {
    invalidateCachedWidth();
    invalidateLines(0, myLines.size() - 1, myDocument.getLineCount() - 1);
  }

  private LineView getLineRenderer(int line) {
    LineView renderer = myLines.get(line);
    if (renderer == null) {
      int lineStart = myDocument.getLineStartOffset(line);
      int lineEnd = myDocument.getLineEndOffset(line);
      renderer = new LineView(myEditor, lineStart, lineEnd, myFontRenderContext);
      myLines.set(line, renderer);
      int width = renderer.getWidthInPixels();
      if (myWidthInPixels >= 0 && width > myWidthInPixels) {
        myWidthInPixels = width;
        myEditor.getContentComponent().revalidate();
      }
    }
    return renderer;
  }
}
