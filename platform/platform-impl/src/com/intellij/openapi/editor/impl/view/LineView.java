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

import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.font.*;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.CharacterIterator;
import java.util.ArrayList;
import java.util.List;

public class LineView {
  private static final Stroke IME_COMPOSED_TEXT_UNDERLINE_STROKE = new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0,
                                                                                   new float[]{0, 2, 0, 2}, 0);
  
  private final TextLayout myLayout;
  private final TabSpacer[] myTabs;
  
  public LineView(EditorImpl editor, int startOffset, int endOffset, FontRenderContext fontRenderContext) {
    if (startOffset == endOffset) {
      myLayout = null;
      myTabs = null;
    }
    else {
      Ref<TabSpacer[]> tabs = new Ref<TabSpacer[]>();
      AttributedCharacterIterator iterator = createTextWithAttributes(editor, startOffset, endOffset, fontRenderContext, tabs);
      myLayout = new TextLayout(iterator, fontRenderContext);
      myTabs = tabs.get();
    }
  }

  public int getWidthInPixels() {
    return myLayout == null ? 0 : (int)myLayout.getAdvance();
  }

  public static int spacePixelsToColumns(int pixels, int plainSpaceWidth) {
    return pixels < 0 ? 0 : (pixels + plainSpaceWidth / 2) / plainSpaceWidth;
  }

  public static int spaceColumnsToPixels(int columns, int plainSpaceWidth) {
    return columns < 0 ? 0 : columns * plainSpaceWidth;
  }

  public int xToColumn(int x, int plainSpaceWidth, boolean canBeInsideTab) {
    if (myLayout == null) {
      return spacePixelsToColumns(x, plainSpaceWidth);
    }
    if (x <= 0) {
      return 0;
    }
    int width = getWidthInPixels();
    if (x >= width) {
      return offsetToColumn(myLayout.getCharacterCount(), plainSpaceWidth) + spacePixelsToColumns(x - width, plainSpaceWidth);
    }
    TextHitInfo hit = myLayout.hitTestChar(x, 0);
    if (myTabs != null && canBeInsideTab) {
      int charIndex = hit.getCharIndex();
      for (TabSpacer tab : myTabs) {
        int tabOffset = tab.getOffset();
        if (tabOffset == charIndex) {
          int tabColumn = offsetToColumn(charIndex, plainSpaceWidth);
          return tabColumn + spacePixelsToColumns(x - columnToX(tabColumn, plainSpaceWidth), plainSpaceWidth);
        }
        else if (tabOffset > charIndex) {
          break;
        }        
      }
    }
    return offsetToColumn(hit.getInsertionIndex(), plainSpaceWidth);
  }

  public int columnToX(int column, int plainSpaceWidth) {
    if (myLayout == null) {
      return spaceColumnsToPixels(column, plainSpaceWidth);
    }
    if (column < 0) {
      return 0;
    }
    int[] result = columnToOffset(column, plainSpaceWidth);
    int spacePixels = spaceColumnsToPixels(result[1], plainSpaceWidth);
    int charCount = myLayout.getCharacterCount();
    if (result[0] >= charCount) {
      return getWidthInPixels() + spacePixels;
    }
    Point point = new Point();
    myLayout.hitToPoint(TextHitInfo.beforeOffset(result[0]), point);
    return point.x + spacePixels;
  }

  public int offsetToColumn(int offset, int plainSpaceWidth) {
    if (myTabs == null) return offset;
    int column = offset;
    for (TabSpacer tab : myTabs) {
      if (tab.getOffset() < offset) {
        column += (tab.getWidthInColumns(plainSpaceWidth) - 1);
      }
      else {
        break;
      }
    }
    return column;
  }

  // first element is offset, second element - unaccounted shift in columns beyond line end or tab character start
  public int[] columnToOffset(int column, int plainSpaceWidth) {
    if (myLayout == null) {
      return new int[] {0, column};
    }
    if (column <= 0) return new int[]  {0, 0};
    int columnDiff = 0;
    if (myTabs != null) {
      for (TabSpacer tab : myTabs) {
        int tabStartColumn = tab.getOffset() + columnDiff;
        if (column <= tabStartColumn) {
          return new int[] {column - columnDiff, 0};
        }
        int tabEndColumn = tabStartColumn + tab.getWidthInColumns(plainSpaceWidth);
        if (column < tabEndColumn) {
          
          return new int[] {tab.getOffset(), column - tabStartColumn};
        }
        columnDiff += tab.getWidthInColumns(plainSpaceWidth) - 1;
      }
    }
    int offset = column - columnDiff;
    int offsetOverflow = offset - myLayout.getCharacterCount();
    if (offsetOverflow > 0) {
      return new int[] {myLayout.getCharacterCount(), offsetOverflow};
    }
    else {
      return new int[] {offset, 0};
    }
  }

  public void paintTextAndEffects(Graphics2D g, EditorImpl editor, int startOffset, int endOffset,
                                  EditorImpl.WhitespacePaintingStrategy whitespacePaintingStrategy, int plainSpaceWidth) {
    if (myLayout != null) {
      myLayout.draw(g, 0, editor.getAscent());
      paintWhitespace(g, editor, startOffset, endOffset, whitespacePaintingStrategy, plainSpaceWidth);
      paintTextEffects(g, editor, startOffset, endOffset);
      paintComposedTextDecoration(g, editor, startOffset, endOffset);
    }
  }

  private void paintWhitespace(Graphics2D g, EditorImpl editor, int startOffset, int endOffset,
                               EditorImpl.WhitespacePaintingStrategy whitespacePaintingStrategy, int plainSpaceWidth) {
    g.setColor(editor.getColorsScheme().getColor(EditorColors.WHITESPACES_COLOR));
    CharSequence text = editor.getDocument().getImmutableCharSequence();
    for (int i = startOffset; i < endOffset; i++) {
      char c = text.charAt(i);
      if (" \t\u3000".indexOf(c) >= 0 && whitespacePaintingStrategy.showWhitespaceAtOffset(i)) {
        Point start = new Point();
        myLayout.hitToPoint(TextHitInfo.leading(i - startOffset), start);
        Point end = new Point();
        myLayout.hitToPoint(TextHitInfo.trailing(i - startOffset), end);

        int y = editor.getAscent();
        if (c == ' ') {
          g.fillRect((start.x + end.x) / 2, y, 1, 1);
        }
        else if (c == '\t') {
          int startX = Math.min(start.x, end.x);
          int stopX = Math.max(start.x, end.x);
          stopX -= plainSpaceWidth / 4;
          int height = editor.getCharHeight();
          int halfHeight = height / 2;
          int mid = y - halfHeight;
          int top = y - height;
          UIUtil.drawLine(g, startX, mid, stopX, mid);
          UIUtil.drawLine(g, stopX, y, stopX, top);
          g.fillPolygon(new int[]{stopX - halfHeight, stopX - halfHeight, stopX}, new int[]{y, y - height, y - halfHeight}, 3);
        }
        else if (c == '\u3000') { // ideographic space
          final int charHeight = editor.getCharHeight();
          g.drawRect(Math.min(start.x, end.x) + 2, y - charHeight, Math.abs(start.x - end.x) - 4, charHeight);
        }
      }
    }
  }

  private void paintTextEffects(Graphics2D g, final EditorImpl editor, int startOffset, int endOffset) {
    IterationState it = new IterationState(editor, startOffset, endOffset, editor.isPaintSelection());
    while (!it.atEnd()) {
      TextAttributes attributes = it.getMergedAttributes();
      final Color color = attributes.getEffectColor();
      final EffectType type = attributes.getEffectType();
      if (hasTextEffect(color, type)) {
        paintInRange(g, editor, it.getStartOffset() - startOffset, it.getEndOffset() - startOffset, new Consumer<Graphics2D>() {
          @Override
          public void consume(Graphics2D window) {
            window.setColor(color);
            Rectangle clipBounds = window.getClipBounds();
            paintTextEffect(window, editor, (int)clipBounds.getMinX(), (int)clipBounds.getMaxX(), editor.getAscent(), type);
          }
        });
      }
      it.advance();
    }
  }
  
  public static boolean hasTextEffect(Color effectColor, EffectType effectType) {
    return effectColor != null && (effectType == EffectType.LINE_UNDERSCORE ||
                                   effectType == EffectType.BOLD_LINE_UNDERSCORE ||
                                   effectType == EffectType.BOLD_DOTTED_LINE ||
                                   effectType == EffectType.WAVE_UNDERSCORE ||
                                   effectType == EffectType.STRIKEOUT);
  }

  public static void paintTextEffect(Graphics2D g, EditorImpl editor, int xStart, int xEnd, int y, EffectType effectType) {
    if (effectType == EffectType.LINE_UNDERSCORE) {
      UIUtil.drawLine(g, xStart, y + 1, xEnd, y + 1);
    }
    else if (effectType == EffectType.BOLD_LINE_UNDERSCORE) {
      UIUtil.drawLine(g, xStart, y, xEnd, y);
      UIUtil.drawLine(g, xStart, y + 1, xEnd, y + 1);
    }
    else if (effectType == EffectType.STRIKEOUT) {
      int y1 = y - editor.getCharHeight() / 2;
      UIUtil.drawLine(g, xStart, y1, xEnd, y1);
    }
    else if (effectType == EffectType.WAVE_UNDERSCORE) {
      UIUtil.drawWave(g, new Rectangle(xStart, y + 1, xEnd - xStart, editor.getDescent() - 1));
    }
    else if (effectType == EffectType.BOLD_DOTTED_LINE) {
      UIUtil.drawBoldDottedLine(g, xStart, xEnd, SystemInfo.isMac ? y : y + 1, editor.getBackgroundColor(), g.getColor(), false);
    }
  }

  private void paintComposedTextDecoration(Graphics2D g, final EditorImpl editor, int startOffset, int endOffset) {
    TextRange composedTextRange = editor.getComposedTextRange();
    if (composedTextRange != null) {
      int start = trim(composedTextRange.getStartOffset(), startOffset, endOffset) - startOffset;
      int end = trim(composedTextRange.getEndOffset(), startOffset, endOffset) - startOffset;
      if (start < end) {
        paintInRange(g, editor, start, end, new Consumer<Graphics2D>() {
          @Override
          public void consume(Graphics2D window) {
            window.setColor(editor.getColorsScheme().getColor(EditorColors.CARET_COLOR));
            window.setStroke(IME_COMPOSED_TEXT_UNDERLINE_STROKE);
            Rectangle clipBounds = window.getClipBounds();
            UIUtil.drawLine(window, (int)clipBounds.getMinX(), 1, (int)clipBounds.getMaxX(), 1);
          }
        });
      }
    }
  }
  
  private void paintInRange(Graphics2D g, EditorImpl editor, int start, int end, Consumer<Graphics2D> paintRunnable) {
    TextLayoutHighlightShape shape = new TextLayoutHighlightShape(myLayout, start, end, editor.getAscent(), editor.getLineHeight());
    Graphics2D window = (Graphics2D)g.create();
    try {
      shape.setAsClip(window);
      paintRunnable.consume(window);
    }
    finally {
      window.dispose();
    }
  }
  

  private static int trim(int offset, int minimum, int maximum) {
    return Math.min(maximum, Math.max(minimum, offset));
  }

  public void paintBackground(Graphics2D g, EditorImpl editor, int startOffset, int endOffset, int plainSpaceWidth) {
    IterationState it = new IterationState(editor, startOffset, endOffset, editor.isPaintSelection());
    if (myLayout != null) {
      while (!it.atEnd()) {
        paintBackground(g, editor, myLayout, it.getStartOffset() - startOffset, it.getEndOffset() - startOffset, it.getMergedAttributes());
        it.advance();
      }
    }
    int lastSegmentX = paintAfterLineEndBackgroundSegments(g, editor, it, getWidthInPixels(), plainSpaceWidth);
    if (endOffset < editor.getDocument().getTextLength()) {
      Color color = EditorView.getBackgroundColor(editor, it.getPastLineEndBackgroundAttributes());
      paintBackgroundTillClipRightBoundary(g, editor, color, lastSegmentX);
    }
    else {
      if (it.hasPastFileEndBackgroundSegments()) {
        lastSegmentX = paintAfterLineEndBackgroundSegments(g, editor, it, lastSegmentX, plainSpaceWidth);
      }
      Color color = it.getPastFileEndBackground();
      paintBackgroundTillClipRightBoundary(g, editor, color, lastSegmentX);
    }
  }
  
  public static void paintBackground(Graphics2D g, EditorImpl editor, TextLayout textLayout, int start, int end, TextAttributes attributes) {
    Color color = EditorView.getBackgroundColor(editor, attributes);
    if (color != null) {
      TextLayoutHighlightShape shape = new TextLayoutHighlightShape(textLayout, start, end, editor.getAscent(), editor.getLineHeight());
      g.setColor(color);
      shape.fill(g);
    }
  }
  
  private static int paintAfterLineEndBackgroundSegments(Graphics g, EditorImpl editor, IterationState it, int x, int plainSpaceWidth) {
    while (it.hasPastLineEndBackgroundSegment()) {
      int width = plainSpaceWidth * it.getPastLineEndBackgroundSegmentWidth();
      Color color = EditorView.getBackgroundColor(editor, it.getPastLineEndBackgroundAttributes());
      if (color != null) {
        g.setColor(color);
        g.fillRect(x, 0, width, editor.getLineHeight());
      }
      x += width;
      it.advanceToNextPastLineEndBackgroundSegment();
    }
    return x;
  }

  private static void paintBackgroundTillClipRightBoundary(Graphics g, EditorImpl editor, Color color, int x) {
    if (color == null || color.equals(editor.getBackgroundColor())) return;
    Rectangle clipBounds = g.getClipBounds();
    g.setColor(color);
    g.fillRect(x, 0, clipBounds.x + clipBounds.width - x, editor.getLineHeight());
  }

  private static AttributedCharacterIterator createTextWithAttributes(EditorImpl editor,
                                                                      int startOffset,
                                                                      int endOffset,
                                                                      FontRenderContext fontRenderContext, Ref<TabSpacer[]> tabs) {
    String text = editor.getDocument().getImmutableCharSequence().subSequence(startOffset, endOffset).toString();
    AttributedString string = createAttributedString(text);
    
    FontPreferences fontPreferences = editor.getColorsScheme().getFontPreferences();

    List<Integer> tabRendererPositions = new ArrayList<Integer>();
    IterationState it = new IterationState(editor, startOffset, endOffset, true, false, false);
    while (!it.atEnd()) {
      TextAttributes attributes = it.getMergedAttributes();
      setFontAttributes(string, it.getStartOffset() - startOffset, it.getEndOffset() - startOffset, 
                        attributes.getFontType(), attributes.getForegroundColor(), fontPreferences, tabRendererPositions);
      it.advance();
    }    
    
    insertTabRenderers(editor, string, tabRendererPositions, fontRenderContext, tabs, startOffset == 0);
    
    return string.getIterator();
  }

  public static AttributedString createAttributedString(String text) {
    AttributedString string = new AttributedString(text);
    string.addAttribute(TextAttribute.RUN_DIRECTION, TextAttribute.RUN_DIRECTION_LTR);
    return string;
  }
  
  public static void setFontAttributes(AttributedString string, int start, int end, int fontStyle, Color fontColor, 
                                       FontPreferences fontPreferences, @Nullable List<Integer> tabRendererPositions) {
    AttributedCharacterIterator it = string.getIterator(new AttributedCharacterIterator.Attribute[0], start, end);
    Font currentFont = null;
    int currentIndex = start;
    for(char c = it.first(); c != CharacterIterator.DONE; c = it.next()) {
      int i = it.getIndex();
      Font font = ComplementaryFontsRegistry.getFontAbleToDisplay(c, fontStyle, fontPreferences).getFont();
      if (!font.equals(currentFont)) {
        if (i > currentIndex) {
          string.addAttribute(TextAttribute.FONT, currentFont, currentIndex, i);
        }
        currentFont = font;
        currentIndex = i;
      }
      if (tabRendererPositions != null && c == '\t') {
        tabRendererPositions.add(i);
      }
    }
    if (currentIndex < end) {
      string.addAttribute(TextAttribute.FONT, currentFont, currentIndex, end);
    }

    string.addAttribute(TextAttribute.FOREGROUND, fontColor, start, end);
  }

  private static void insertTabRenderers(EditorImpl editor, AttributedString string, List<Integer> tabRendererPositions,
                                         FontRenderContext fontRenderContext, Ref<TabSpacer[]> tabs, boolean firstLine) {
    int tabCount = tabRendererPositions.size();
    if (tabCount == 0) return;
    int[] tabWidths = new int[tabCount];
    int currentPos = 0;
    int currentX = firstLine ? editor.getPrefixTextWidthInPixels() : 0;
    TextMeasurer measurer = null;
    for (int i = 0; i < tabCount; i++) {
      int pos = tabRendererPositions.get(i);
      if (pos > currentPos) {
        if (measurer == null) {
          // most frequent case is tab characters at line start
          // in that case we don't need TextMeasurer to find out tab width
          // so we create TextMeasurer instance lazily
          measurer = new TextMeasurer(string.getIterator(), fontRenderContext);
        }
        currentX += measurer.getAdvanceBetween(currentPos, pos);
        currentPos = pos;
      }
      currentPos++;
      int nextX = EditorUtil.nextTabStop(currentX, editor);
      tabWidths[i] = nextX - currentX;
      currentX = nextX;
    }
    TabSpacer[] tabArray = new TabSpacer[tabCount];
    for (int i = 0; i < tabCount; i++) {
      int pos = tabRendererPositions.get(i);
      string.addAttribute(TextAttribute.CHAR_REPLACEMENT, tabArray[i] = new TabSpacer(editor, pos, tabWidths[i]), pos, pos + 1);
    }
    tabs.set(tabArray);
  }

  public TextLayoutHighlightShape getRangeShape(EditorImpl editor, int start, int end) {
    return new TextLayoutHighlightShape(myLayout, start, end, editor.getAscent(), editor.getLineHeight());
  }

  public int getVisualLineEndOffset() {
    return myLayout == null ? 0 : myLayout.hitTestChar(myLayout.getAdvance() - 1, 0).getInsertionIndex();
  }

  private static class TabSpacer extends GraphicAttribute {
    private final EditorImpl myEditor;
    private final int myOffset;
    private final int myWidthInPixels;
    
    public TabSpacer(EditorImpl editor, int offset, int widthInPixels) {
      super(GraphicAttribute.ROMAN_BASELINE);
      myEditor = editor;
      myOffset = offset;
      myWidthInPixels = widthInPixels;
    }
  
    @Override
    public float getAscent() {
      return myEditor.getAscent();
    }
  
    @Override
    public float getDescent() {
      return myEditor.getDescent();
    }
  
    @Override
    public float getAdvance() {
      return myWidthInPixels;
    }
  
    public int getOffset() {
      return myOffset;
    }
  
    public int getWidthInColumns(int plainSpaceWidth) {
      return EditorUtil.columnsNumber(myWidthInPixels, plainSpaceWidth);
    }
  
    @Override
    public void draw(Graphics2D graphics, float x, float y) {}
  }
}
