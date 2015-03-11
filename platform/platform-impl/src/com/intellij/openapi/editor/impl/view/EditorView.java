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
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;

/**
 * A facade for components responsible for drawing editor contents, managing editor size 
 * and coordinate conversions (offset <-> logical position <-> visual position <-> x,y).
 * 
 * Also contains a cache of several font-related quantities (line height, space width, etc).
 */
public class EditorView implements Disposable {
  private final EditorImpl myEditor;
  private final DocumentEx myDocument;
  private final FontRenderContext myFontRenderContext;
  private final EditorPainter myPainter;
  private final EditorCoordinateMapper myMapper;
  private final EditorSizeManager mySizeManager;
  private final TextLayoutCache myTextLayoutCache;
  private final TabFragment myTabFragment;
    
  private LineLayout myPrefixLayout;
  private TextAttributes myPrefixAttributes;
  
  private int myPlainSpaceWidth;
  private int myLineHeight;
  private int myDescent;
  private int myCharHeight;
  private int myTabSize;

  public EditorView(EditorImpl editor) {
    myFontRenderContext = createFontRenderContext();
    myEditor = editor;
    myDocument = editor.getDocument();
    
    myPainter = new EditorPainter(this);
    myMapper = new EditorCoordinateMapper(this);
    mySizeManager = new EditorSizeManager(this);
    myTextLayoutCache = new TextLayoutCache(this);
    myTabFragment = new TabFragment(this);
    Disposer.register(this, myTextLayoutCache);
    Disposer.register(this, mySizeManager);
    
    reinitSettings();
  }
  
  EditorImpl getEditor() {
    return myEditor;
  }

  FontRenderContext getFontRenderContext() {
    return myFontRenderContext;
  }

  EditorSizeManager getSizeManager() {
    return mySizeManager;
  }
  
  TabFragment getTabFragment() {
    return myTabFragment;
  }

  @Override
  public void dispose() {
  }

  public int yToVisualLine(int y) {
    return myMapper.yToVisualLine(y);
  }

  public int visualLineToY(int line) {
    return myMapper.visualLineToY(line);
  }

  @NotNull
  public LogicalPosition offsetToLogicalPosition(int offset) {
    return myMapper.offsetToLogicalPosition(offset);
  }

  public int logicalPositionToOffset(@NotNull LogicalPosition pos) {
    return myMapper.logicalPositionToOffset(pos);
  }

  @NotNull
  public VisualPosition logicalToVisualPosition(@NotNull LogicalPosition pos) {
    return myMapper.logicalToVisualPosition(pos);
  }

  @NotNull
  public LogicalPosition visualToLogicalPosition(@NotNull VisualPosition pos) {
    return myMapper.visualToLogicalPosition(pos);
  }

  @NotNull
  public VisualPosition offsetToVisualPosition(int offset) {
    return myMapper.offsetToVisualPosition(offset);
  }

  public int offsetToVisualLine(int offset) {
    return myMapper.offsetToVisualLine(offset);
  }

  @NotNull
  public VisualPosition xyToVisualPosition(@NotNull Point p) {
    return myMapper.xyToVisualPosition(p);
  }

  @NotNull
  public Point visualPositionToXY(@NotNull VisualPosition pos, boolean leanTowardsLargerColumns) {
    return myMapper.visualPositionToXY(pos, leanTowardsLargerColumns);
  }

  @NotNull
  public Point offsetToXY(int offset, boolean leanTowardsLargerOffsets) {
    return myMapper.offsetToXY(offset, leanTowardsLargerOffsets);
  }

  public void setPrefix(String prefixText, TextAttributes attributes) {
    myPrefixLayout = prefixText == null || prefixText.isEmpty() ? null :
                     new LineLayout(this, prefixText, attributes.getFontType(), myFontRenderContext, 0);
    myPrefixAttributes = attributes;
    mySizeManager.invalidateCachedWidth();
    invalidateLines(0, 0);
  }

  public float getPrefixTextWidthInPixels() {
    return myPrefixLayout == null ? 0 : myPrefixLayout.getMaxX();
  }

  LineLayout getPrefixLayout() {
    return myPrefixLayout;
  }

  TextAttributes getPrefixAttributes() {
    return myPrefixAttributes;
  }

  public void paint(Graphics2D g) {
    myPainter.paint(g);
  }

  public Dimension getPreferredSize() {
    return mySizeManager.getPreferredSize();
  }

  int getLineWidth(int line) {
    return (int)getLineLayout(line).getMaxX();
  }

  public int getMaxWidthInRange(int startOffset, int endOffset) {
    return getMaxWidthInLineRange(offsetToLogicalPosition(startOffset).line, offsetToLogicalPosition(endOffset).line);
  }
  
  int getMaxWidthInLineRange(int startLine, int endLine) {
    int maxWidth = 0;
    int lineCount = myDocument.getLineCount();
    for (int line = startLine; line <= endLine && line < lineCount; line++) {
      maxWidth = Math.max(maxWidth, getLineWidth(line));
    }
    return maxWidth;
  }

  public void reinitSettings() {
    myPlainSpaceWidth = -1;
    myLineHeight = -1;
    myDescent = -1;
    myCharHeight = -1;
    myTabSize = -1;
    reset();
  }
  
  public void invalidateLines(int startLine, int endLine) {
    int lineCount = myDocument.getLineCount();
    if (startLine > endLine || startLine >= lineCount || endLine < 0) {
      return;
    }
    startLine = Math.max(0, startLine);
    endLine = Math.min(lineCount - 1, endLine);
    myTextLayoutCache.invalidateLines(startLine, endLine, endLine);
  }

  public void reset() {
    mySizeManager.invalidateCachedWidth();
    myTextLayoutCache.resetToDocumentSize();
  }

  @NotNull
  LineLayout getLineLayout(int line) {
    LineLayout lineLayout = myTextLayoutCache.getLineLayout(line);
    mySizeManager.validateCurrentWidth(lineLayout);
    return lineLayout;
  }
  
  @Nullable
  LineLayout getCachedLineLayout(int line) {
    return myTextLayoutCache.getCachedLineLayout(line);
  }

  int getPlainSpaceWidth() {
    if (myPlainSpaceWidth < 0) {
      FontMetrics fontMetrics = myEditor.getContentComponent().getFontMetrics(myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));
      int width = FontLayoutService.getInstance().charWidth(fontMetrics, ' ');
      myPlainSpaceWidth = width > 0 ? width : 1;
    }
    return myPlainSpaceWidth;
  }

  public int getLineHeight() {
    if (myLineHeight < 0) {
      EditorColorsScheme colorsScheme = myEditor.getColorsScheme();
      FontMetrics fontMetrics = myEditor.getContentComponent().getFontMetrics(colorsScheme.getFont(EditorFontType.PLAIN));
      int fontMetricsHeight = FontLayoutService.getInstance().getHeight(fontMetrics);
      myLineHeight = (int)(fontMetricsHeight * (myEditor.isOneLineMode() ? 1 : colorsScheme.getLineSpacing()));
      if (myLineHeight <= 0) {
        myLineHeight = fontMetricsHeight;
        if (myLineHeight <= 0) {
          myLineHeight = 12;
        }
      }
    }
    return myLineHeight;
  }

  public int getAscent() {
    return getLineHeight() - getDescent();
  }

  public int getCharHeight() {
    if (myCharHeight < 0) {
      FontMetrics fontMetrics = myEditor.getContentComponent().getFontMetrics(myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));
      myCharHeight = FontLayoutService.getInstance().charWidth(fontMetrics, 'a');
    }
    return myCharHeight;
  }

  public int getDescent() {
    if (myDescent < 0) {
      FontMetrics fontMetrics = myEditor.getContentComponent().getFontMetrics(myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));
      myDescent = FontLayoutService.getInstance().getDescent(fontMetrics);
    }
    return myDescent;
  }
  
  public int getTabSize() {
    if (myTabSize < 0) {
      myTabSize = EditorUtil.getTabSize(myEditor);
    }
    return myTabSize;
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
}
