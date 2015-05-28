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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.util.EditorUIUtil;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

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
  private static Key<LineLayout> FOLD_REGION_TEXT_LAYOUT = Key.create("text.layout");

  private final EditorImpl myEditor;
  private final DocumentEx myDocument;
  private final FontRenderContext myFontRenderContext;
  private final EditorPainter myPainter;
  private final EditorCoordinateMapper myMapper;
  private final EditorSizeManager mySizeManager;
  private final TextLayoutCache myTextLayoutCache;
  private final TabFragment myTabFragment;
    
  private String myPrefixText; // accessed only in EDT
  private LineLayout myPrefixLayout; // guarded by myLock
  private TextAttributes myPrefixAttributes; // accessed only in EDT
  
  private int myPlainSpaceWidth; // accessed only in EDT
  private int myLineHeight; // guarded by myLock
  private int myDescent; // guarded by myLock
  private int myCharHeight; // guarded by myLock
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
  
  EditorPainter getPainter() {
    return myPainter;
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
    assertIsReadAccess();
    return myMapper.offsetToLogicalPosition(offset);
  }

  public int logicalPositionToOffset(@NotNull LogicalPosition pos) {
    assertIsReadAccess();
    return myMapper.logicalPositionToOffset(pos);
  }

  @NotNull
  public VisualPosition logicalToVisualPosition(@NotNull LogicalPosition pos) {
    assertIsDispatchThread();
    return myMapper.logicalToVisualPosition(pos);
  }

  @NotNull
  public LogicalPosition visualToLogicalPosition(@NotNull VisualPosition pos) {
    assertIsDispatchThread();
    return myMapper.visualToLogicalPosition(pos);
  }

  @NotNull
  public VisualPosition offsetToVisualPosition(int offset, boolean leanTowardsLargerOffsets) {
    assertIsDispatchThread();
    return myMapper.offsetToVisualPosition(offset, leanTowardsLargerOffsets);
  }

  public int offsetToVisualLine(int offset) {
    assertIsDispatchThread();
    return myMapper.offsetToVisualLine(offset);
  }

  @NotNull
  public VisualPosition xyToVisualPosition(@NotNull Point p) {
    assertIsDispatchThread();
    return myMapper.xyToVisualPosition(p);
  }

  @NotNull
  public Point visualPositionToXY(@NotNull VisualPosition pos) {
    assertIsDispatchThread();
    return myMapper.visualPositionToXY(pos);
  }

  @NotNull
  public Point offsetToXY(int offset, boolean leanTowardsLargerOffsets) {
    assertIsDispatchThread();
    return myMapper.offsetToXY(offset, leanTowardsLargerOffsets);
  }

  public void setPrefix(String prefixText, TextAttributes attributes) {
    assertIsDispatchThread();
    myPrefixText = prefixText;
    synchronized (myLock) {
      myPrefixLayout = prefixText == null || prefixText.isEmpty() ? null :
                       new LineLayout(this, prefixText, attributes.getFontType(), myFontRenderContext);
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
    myPainter.paint(g);
  }

  public void repaintCarets() {
    assertIsDispatchThread();
    myPainter.repaintCarets();
  }

  public Dimension getPreferredSize() {
    assertIsDispatchThread();
    return mySizeManager.getPreferredSize();
  }

  public int getMaxWidthInRange(int startOffset, int endOffset) {
    assertIsDispatchThread();
    return getMaxWidthInLineRange(offsetToVisualLine(startOffset), offsetToVisualLine(endOffset));
  }
  
  int getMaxWidthInLineRange(int startVisualLine, int endVisualLine) {
    int maxWidth = 0;
    for (int i = startVisualLine; i <= endVisualLine; i++) {
      int logicalLine = visualToLogicalPosition(new VisualPosition(i, 0)).line;
      if (logicalLine >= myDocument.getLineCount()) break;
      int startOffset = myDocument.getLineStartOffset(logicalLine);
      float x = 0;
      for (VisualLineFragmentsIterator.Fragment fragment : VisualLineFragmentsIterator.create(this, startOffset)) {
        x = fragment.getEndX();
      }
      maxWidth = Math.max(maxWidth, (int) x);
    }
    return maxWidth;
  }

  public void reinitSettings() {
    assertIsDispatchThread();
    myPlainSpaceWidth = -1;
    synchronized (myLock) {
      myLineHeight = -1;
      myDescent = -1;
      myCharHeight = -1;
      myTabSize = -1;
    }
    reset();
    setPrefix(myPrefixText, myPrefixAttributes); // recreate prefix layout
    invalidateFoldRegionLayouts();
  }
  
  public void invalidateRange(int startOffset, int endOffset) {
    assertIsDispatchThread();
    int textLength = myDocument.getTextLength();
    if (startOffset > endOffset || startOffset >= textLength || endOffset < 0) {
      return;
    }
    int startLine = myDocument.getLineNumber(Math.max(0, startOffset));
    int endLine = myDocument.getLineNumber(Math.min(textLength, endOffset));
    myTextLayoutCache.invalidateLines(startLine, endLine, endLine);
    mySizeManager.invalidateRange(startOffset, endOffset);
  }

  public void reset() {
    assertIsDispatchThread();
    mySizeManager.reset();
    myTextLayoutCache.resetToDocumentSize();
  }
  
  public boolean isRtlLocation(int offset, boolean leanForward) {
    assertIsDispatchThread();
    if (myDocument.getTextLength() == 0) return false;
    int line = myDocument.getLineNumber(offset);
    LineLayout layout = getLineLayout(line);
    return layout.isRtlLocation(offset - myDocument.getLineStartOffset(line), leanForward);
  }

  public boolean isDirectionBoundary(int offset) {
    assertIsDispatchThread();
    if (myDocument.getTextLength() == 0) return false;
    int line = myDocument.getLineNumber(offset);
    LineLayout layout = getLineLayout(line);
    return layout.isDirectionBoundary(offset - myDocument.getLineStartOffset(line));
  }

  @NotNull
  LineLayout getLineLayout(int line) {
    return myTextLayoutCache.getLineLayout(line);
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
    synchronized (myLock) {
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
  }

  public int getAscent() {
    synchronized (myLock) {
      return getLineHeight() - getDescent();
    }
  }

  public int getCharHeight() {
    synchronized (myLock) {
      if (myCharHeight < 0) {
        FontMetrics fontMetrics = myEditor.getContentComponent().getFontMetrics(myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));
        myCharHeight = FontLayoutService.getInstance().charWidth(fontMetrics, 'a');
      }
      return myCharHeight;
    }
  }

  public int getDescent() {
    synchronized (myLock) {
      if (myDescent < 0) {
        FontMetrics fontMetrics = myEditor.getContentComponent().getFontMetrics(myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));
        myDescent = FontLayoutService.getInstance().getDescent(fontMetrics);
      }
      return myDescent;
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
    Graphics2D g = (Graphics2D)UIUtil.createImage(1, 1, BufferedImage.TYPE_INT_RGB).getGraphics();
    try {
      EditorUIUtil.setupAntialiasing(g);
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
      layout = new LineLayout(this, foldRegion.getPlaceholderText(), 
                              placeholderAttributes == null ? Font.PLAIN : placeholderAttributes.getFontType(), 
                              myFontRenderContext);
      foldRegion.putUserData(FOLD_REGION_TEXT_LAYOUT, layout);
    }
    return layout;
  }

  void invalidateFoldRegionLayouts() {
    for (FoldRegion region : myEditor.getFoldingModel().getAllFoldRegions()) {
      region.putUserData(FOLD_REGION_TEXT_LAYOUT, null);
    }
  }
  
  private static void assertIsDispatchThread() {
    ApplicationManager.getApplication().assertIsDispatchThread();
  }
  
  private static void assertIsReadAccess() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
  }
}
