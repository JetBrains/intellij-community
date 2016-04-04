/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.diagnostic.Dumpable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.openapi.editor.impl.TextDrawingCallback;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.font.FontRenderContext;

/**
 * A facade for components responsible for drawing editor contents, managing editor size 
 * and coordinate conversions (offset <-> logical position <-> visual position <-> x,y).
 * 
 * Also contains a cache of several font-related quantities (line height, space width, etc).
 */
public class EditorView implements TextDrawingCallback, Disposable, Dumpable {
  private static Key<LineLayout> FOLD_REGION_TEXT_LAYOUT = Key.create("text.layout");

  private final EditorImpl myEditor;
  private final DocumentEx myDocument;
  private final FontRenderContext myFontRenderContext;
  private final EditorPainter myPainter;
  private final EditorCoordinateMapper myMapper;
  private final EditorSizeManager mySizeManager;
  private final TextLayoutCache myTextLayoutCache;
  private final LogicalPositionCache myLogicalPositionCache;
  private final TabFragment myTabFragment;
  
  private String myPrefixText; // accessed only in EDT
  private LineLayout myPrefixLayout; // guarded by myLock
  private TextAttributes myPrefixAttributes; // accessed only in EDT
  
  private int myPlainSpaceWidth; // accessed only in EDT
  private int myLineHeight; // guarded by myLock
  private int myAscent; // guarded by myLock
  private int myCharHeight; // guarded by myLock
  private int myMaxCharWidth; // guarded by myLock
  private int myTabSize; // guarded by myLock
  
  private final Object myLock = new Object();
  
  public EditorView(EditorImpl editor) {
    myFontRenderContext = createFontRenderContext();
    myEditor = editor;
    myDocument = editor.getDocument();
    
    myPainter = new EditorPainter(this);
    myMapper = new EditorCoordinateMapper(this);
    mySizeManager = new EditorSizeManager(this);
    myTextLayoutCache = new TextLayoutCache(this);
    myLogicalPositionCache = new LogicalPositionCache(this);
    myTabFragment = new TabFragment(this);
    
    Disposer.register(this, myLogicalPositionCache);
    Disposer.register(this, myTextLayoutCache);
    Disposer.register(this, mySizeManager);
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
  
  TextLayoutCache getTextLayoutCache() {
    return myTextLayoutCache;
  }
  
  EditorPainter getPainter() {
    return myPainter;
  }
  
  TabFragment getTabFragment() {
    return myTabFragment;
  }
  
  LogicalPositionCache getLogicalPositionCache() {
    return myLogicalPositionCache;
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
    assertIsReadAccess();
    return myMapper.offsetToLogicalPosition(offset);
  }

  public int logicalPositionToOffset(@NotNull LogicalPosition pos) {
    assertIsReadAccess();
    return myMapper.logicalPositionToOffset(pos);
  }

  @NotNull
  public VisualPosition logicalToVisualPosition(@NotNull LogicalPosition pos, boolean beforeSoftWrap) {
    assertIsDispatchThread();
    myEditor.getSoftWrapModel().prepareToMapping();
    return myMapper.logicalToVisualPosition(pos, beforeSoftWrap);
  }

  @NotNull
  public LogicalPosition visualToLogicalPosition(@NotNull VisualPosition pos) {
    assertIsDispatchThread();
    myEditor.getSoftWrapModel().prepareToMapping();
    return myMapper.visualToLogicalPosition(pos);
  }

  @NotNull
  public VisualPosition offsetToVisualPosition(int offset, boolean leanTowardsLargerOffsets, boolean beforeSoftWrap) {
    assertIsDispatchThread();
    myEditor.getSoftWrapModel().prepareToMapping();
    return myMapper.offsetToVisualPosition(offset, leanTowardsLargerOffsets, beforeSoftWrap);
  }

  public int visualPositionToOffset(VisualPosition visualPosition) {
    assertIsDispatchThread();
    myEditor.getSoftWrapModel().prepareToMapping();
    return myMapper.visualPositionToOffset(visualPosition);
  }

  public int offsetToVisualLine(int offset, boolean beforeSoftWrap) {
    assertIsDispatchThread();
    myEditor.getSoftWrapModel().prepareToMapping();
    return myMapper.offsetToVisualLine(offset, beforeSoftWrap);
  }
  
  public int visualLineToOffset(int visualLine) {
    assertIsDispatchThread();
    myEditor.getSoftWrapModel().prepareToMapping();
    return myMapper.visualLineToOffset(visualLine);
  }
  
  @NotNull
  public VisualPosition xyToVisualPosition(@NotNull Point p) {
    assertIsDispatchThread();
    myEditor.getSoftWrapModel().prepareToMapping();
    return myMapper.xyToVisualPosition(p);
  }

  @NotNull
  public Point visualPositionToXY(@NotNull VisualPosition pos) {
    assertIsDispatchThread();
    myEditor.getSoftWrapModel().prepareToMapping();
    return myMapper.visualPositionToXY(pos);
  }

  @NotNull
  public Point offsetToXY(int offset, boolean leanTowardsLargerOffsets, boolean beforeSoftWrap) {
    assertIsDispatchThread();
    myEditor.getSoftWrapModel().prepareToMapping();
    return myMapper.offsetToXY(offset, leanTowardsLargerOffsets, beforeSoftWrap);
  }

  public void setPrefix(String prefixText, TextAttributes attributes) {
    assertIsDispatchThread();
    myPrefixText = prefixText;
    synchronized (myLock) {
      myPrefixLayout = prefixText == null || prefixText.isEmpty() ? null :
                       LineLayout.create(this, prefixText, attributes.getFontType());
    }
    myPrefixAttributes = attributes;
    mySizeManager.invalidateRange(0, 0);
  }

  public float getPrefixTextWidthInPixels() {
    synchronized (myLock) {
      return myPrefixLayout == null ? 0 : myPrefixLayout.getWidth();
    }
  }

  LineLayout getPrefixLayout() {
    synchronized (myLock) {
      return myPrefixLayout;
    }
  }

  TextAttributes getPrefixAttributes() {
    return myPrefixAttributes;
  }

  public void paint(Graphics2D g) {
    assertIsDispatchThread();
    myEditor.getSoftWrapModel().prepareToMapping();
    myPainter.paint(g);
  }

  public void repaintCarets() {
    assertIsDispatchThread();
    myPainter.repaintCarets();
  }

  public Dimension getPreferredSize() {
    assertIsDispatchThread();
    assert !myEditor.isPurePaintingMode();
    myEditor.getSoftWrapModel().prepareToMapping();
    return mySizeManager.getPreferredSize();
  }

  public int getPreferredHeight() {
    assertIsDispatchThread();
    assert !myEditor.isPurePaintingMode();
    myEditor.getSoftWrapModel().prepareToMapping();
    return mySizeManager.getPreferredHeight();
  }

  public int getMaxWidthInRange(int startOffset, int endOffset) {
    assertIsDispatchThread();
    return getMaxWidthInLineRange(offsetToVisualLine(startOffset, false), offsetToVisualLine(endOffset, true));
  }

  /**
   * If <code>quickEvaluationListener</code> is provided, quick approximate size evaluation becomes enabled, listener will be invoked
   * if approximation will in fact be used during width calculation.
   */
  int getMaxWidthInLineRange(int startVisualLine, int endVisualLine) {
    myEditor.getSoftWrapModel().prepareToMapping();
    int maxWidth = 0;
    VisualLinesIterator iterator = new VisualLinesIterator(this, startVisualLine);
    while (!iterator.atEnd() && iterator.getVisualLine() <= endVisualLine) {
      int width = mySizeManager.getVisualLineWidth(iterator, null);
      maxWidth = Math.max(maxWidth, width);
      iterator.advance();
    }
    return maxWidth;
  }

  public void reinitSettings() {
    assertIsDispatchThread();
    myPlainSpaceWidth = -1;
    synchronized (myLock) {
      myLineHeight = -1;
      myAscent = -1;
      myCharHeight = -1;
      myMaxCharWidth = -1;
      myTabSize = -1;
    }
    myLogicalPositionCache.reset(false);
    myTextLayoutCache.resetToDocumentSize(false);
    invalidateFoldRegionLayouts();
    setPrefix(myPrefixText, myPrefixAttributes); // recreate prefix layout
    mySizeManager.reset();
  }
  
  public void invalidateRange(int startOffset, int endOffset) {
    assertIsDispatchThread();
    int textLength = myDocument.getTextLength();
    if (startOffset > endOffset || startOffset >= textLength || endOffset < 0) {
      return;
    }
    int startLine = myDocument.getLineNumber(Math.max(0, startOffset));
    int endLine = myDocument.getLineNumber(Math.min(textLength, endOffset));
    myTextLayoutCache.invalidateLines(startLine, endLine);
    mySizeManager.invalidateRange(startOffset, endOffset);
  }

  /**
   * Invoked when document might have changed, but no notifications were sent (for a hacky document in EditorTextFieldCellRenderer)
   */
  public void reset() {
    assertIsDispatchThread();
    myLogicalPositionCache.reset(true);
    myTextLayoutCache.resetToDocumentSize(true);
    mySizeManager.reset();
  }
  
  public boolean isRtlLocation(@NotNull VisualPosition visualPosition) {
    assertIsDispatchThread();
    if (myDocument.getTextLength() == 0) return false;
    LogicalPosition logicalPosition = visualToLogicalPosition(visualPosition);
    int offset = logicalPositionToOffset(logicalPosition);
    if (myEditor.getSoftWrapModel().getSoftWrap(offset) != null) {
      VisualPosition beforeWrapPosition = offsetToVisualPosition(offset, true, true);
      if (visualPosition.line == beforeWrapPosition.line && 
          (visualPosition.column > beforeWrapPosition.column || 
           visualPosition.column == beforeWrapPosition.column && visualPosition.leansRight)) {
        return false;
      }
      VisualPosition afterWrapPosition = offsetToVisualPosition(offset, false, false);
      if (visualPosition.line == afterWrapPosition.line &&
          (visualPosition.column < afterWrapPosition.column ||
           visualPosition.column == afterWrapPosition.column && !visualPosition.leansRight)) {
        return false;
      }
    } 
    int line = myDocument.getLineNumber(offset);
    LineLayout layout = myTextLayoutCache.getLineLayout(line);
    return layout.isRtlLocation(offset - myDocument.getLineStartOffset(line), logicalPosition.leansForward);
  }

  public boolean isAtBidiRunBoundary(@NotNull VisualPosition visualPosition) {
    assertIsDispatchThread();
    int offset = visualPositionToOffset(visualPosition);
    int otherSideOffset = visualPositionToOffset(visualPosition.leanRight(!visualPosition.leansRight));
    return offset != otherSideOffset;
  }

  /**
   * Offset of nearest boundary (not equal to <code>offset</code>) on the same line is returned. <code>-1</code> is returned if 
   * corresponding boundary is not found.
   */
  public int findNearestDirectionBoundary(int offset, boolean lookForward) {
    assertIsDispatchThread();
    int textLength = myDocument.getTextLength();
    if (textLength == 0 || offset < 0 || offset > textLength) return -1;
    int line = myDocument.getLineNumber(offset);
    LineLayout layout = myTextLayoutCache.getLineLayout(line);
    int lineStartOffset = myDocument.getLineStartOffset(line);
    int relativeOffset = layout.findNearestDirectionBoundary(offset - lineStartOffset, lookForward);
    return relativeOffset < 0 ? -1 : lineStartOffset + relativeOffset;
  }

  int getPlainSpaceWidth() {
    if (myPlainSpaceWidth < 0) {
      FontMetrics fm = myEditor.getContentComponent().getFontMetrics(myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));
      int width = FontLayoutService.getInstance().charWidth(fm, ' ');
      myPlainSpaceWidth = width > 0 ? width : 1;
    }
    return myPlainSpaceWidth;
  }

  public int getLineHeight() {
    synchronized (myLock) {
      if (myLineHeight < 0) {
        FontMetrics fm = myEditor.getContentComponent().getFontMetrics(myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));
        int fontMetricsHeight = FontLayoutService.getInstance().getHeight(fm);
        myLineHeight = (int)Math.ceil(fontMetricsHeight * getVerticalScalingFactor());
      }
      return myLineHeight;
    }
  }

  private float getVerticalScalingFactor() {
    if (myEditor.isOneLineMode()) return 1;
    float lineSpacing = myEditor.getColorsScheme().getLineSpacing();
    return lineSpacing > 0 ? lineSpacing : 1;
  }

  public int getDescent() {
    synchronized (myLock) {
      return getLineHeight() - getAscent();
    }
  }

  public int getCharHeight() {
    synchronized (myLock) {
      if (myCharHeight < 0) {
        FontMetrics fm = myEditor.getContentComponent().getFontMetrics(myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));
        myCharHeight = FontLayoutService.getInstance().charWidth(fm, 'a');
      }
      return myCharHeight;
    }
  }

  int getMaxCharWidth() {
    synchronized (myLock) {
      if (myMaxCharWidth < 0) {
        // assuming that bold italic 'W' gives a good approximation of font's widest character
        FontMetrics fm = myEditor.getContentComponent().getFontMetrics(myEditor.getColorsScheme().getFont(EditorFontType.BOLD_ITALIC));
        myMaxCharWidth = FontLayoutService.getInstance().charWidth(fm, 'W'); 
      }
      return myMaxCharWidth;
    }
  }

  public int getAscent() {
    synchronized (myLock) {
      if (myAscent < 0) {
        FontMetrics fm = myEditor.getContentComponent().getFontMetrics(myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));
        int ascent = FontLayoutService.getInstance().getAscent(fm);
        myAscent = (int)Math.ceil(ascent * getVerticalScalingFactor());
      }
      return myAscent;
    }
  }
  
  public int getTabSize() {
    synchronized (myLock) {
      if (myTabSize < 0) {
        myTabSize = EditorUtil.getTabSize(myEditor);
      }
      return myTabSize;
    }
  }

  private static FontRenderContext createFontRenderContext() {
    Graphics2D g = FontInfo.createReferenceGraphics();
    try {
      return g.getFontRenderContext();
    }
    finally {
      g.dispose();
    }
  }

  LineLayout getFoldRegionLayout(FoldRegion foldRegion) {
    LineLayout layout = foldRegion.getUserData(FOLD_REGION_TEXT_LAYOUT);
    if (layout == null) {
      TextAttributes placeholderAttributes = myEditor.getFoldingModel().getPlaceholderAttributes();
      layout = LineLayout.create(this, foldRegion.getPlaceholderText(), 
                              placeholderAttributes == null ? Font.PLAIN : placeholderAttributes.getFontType());
      foldRegion.putUserData(FOLD_REGION_TEXT_LAYOUT, layout);
    }
    return layout;
  }

  void invalidateFoldRegionLayouts() {
    for (FoldRegion region : myEditor.getFoldingModel().getAllFoldRegions()) {
      region.putUserData(FOLD_REGION_TEXT_LAYOUT, null);
    }
  }
  
  Insets getInsets() {
    return myEditor.getContentComponent().getInsets();
  }
  
  private static void assertIsDispatchThread() {
    ApplicationManager.getApplication().assertIsDispatchThread();
  }
  
  private static void assertIsReadAccess() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
  }

  @Override
  public void drawChars(@NotNull Graphics g, @NotNull char[] data, int start, int end, int x, int y, Color color, FontInfo fontInfo) {
    myPainter.drawChars(g, data, start, end, x, y, color, fontInfo);
  }

  @NotNull
  @Override
  public String dumpState() {
    return "[prefix text: " + myPrefixText + 
           ", prefix attributes: " + myPrefixAttributes + 
           ", space width: " + myPlainSpaceWidth +
           ", line height: " + myLineHeight +
           ", ascent: " + myAscent +
           ", char height: " + myCharHeight +
           ", max char width: " + myMaxCharWidth +
           ", tab size: " + myTabSize + 
           " ,size manager: " + mySizeManager.dumpState() + 
           " ,logical position cache: " + myLogicalPositionCache.dumpState() +
           "]";
  }
}
