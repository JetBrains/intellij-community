// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.diagnostic.Dumpable;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.impl.*;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.text.Bidi;

/**
 * A facade for components responsible for drawing editor contents, managing editor size 
 * and coordinate conversions (offset <-> logical position <-> visual position <-> x,y).
 * <p>
 * Also contains a cache of several font-related quantities (line height, space width, etc).
 */
@ApiStatus.Internal
public final class EditorView implements TextDrawingCallback, Disposable, Dumpable, HierarchyListener, VisibleAreaListener {
  private static final Logger LOG = Logger.getInstance(EditorView.class);
  private static final Key<LineLayout> FOLD_REGION_TEXT_LAYOUT = Key.create("text.layout");

  private final EditorImpl myEditor;
  private final EditorModel myEditorModel;
  private final DocumentEx myDocument;
  private final EditorPainter myPainter;
  private final EditorCoordinateMapper myMapper;
  private final EditorSizeManager mySizeManager;
  private final TextLayoutCache myTextLayoutCache;
  private final LogicalPositionCache myLogicalPositionCache;
  private final CharWidthCache myCharWidthCache;
  private final TabFragment myTabFragment;

  private FontRenderContext myFontRenderContext; // guarded by myLock
  private String myPrefixText; // accessed only in EDT
  private LineLayout myPrefixLayout; // guarded by myLock
  private TextAttributes myPrefixAttributes; // accessed only in EDT
  private int myBidiFlags; // accessed only in EDT
  
  private float myPlainSpaceWidth; // guarded by myLock
  private int myLineHeight; // guarded by myLock
  private int myDescent; // guarded by myLock
  private int myCharHeight; // guarded by myLock
  private float myMaxCharWidth; // guarded by myLock
  private int myCapHeight; // guarded by myLock
  private int myTabSize; // guarded by myLock
  private int myTopOverhang; //guarded by myLock
  private int myBottomOverhang; //guarded by myLock

  private final Object myLock = new Object();

  private @Nullable Runnable myPaintCallback;

  public EditorView(@NotNull EditorImpl editor) {
    this(editor, editor.getEditorModel());
  }

  public EditorView(@NotNull EditorImpl editor, @NotNull EditorModel editorModel) {
    myEditor = editor;
    myEditorModel = editorModel;
    myDocument = myEditorModel.getDocument();
    myPainter = new EditorPainter(this);
    myMapper = new EditorCoordinateMapper(this);
    mySizeManager = new EditorSizeManager(this);
    myTextLayoutCache = new TextLayoutCache(this);
    myLogicalPositionCache = new LogicalPositionCache(this);
    myCharWidthCache = new CharWidthCache(this);
    myTabFragment = new TabFragment(this);

    myEditor.getContentComponent().addHierarchyListener(this);
    getScrollingModel().addVisibleAreaListener(this);

    Disposer.register(this, myLogicalPositionCache);
    Disposer.register(this, myTextLayoutCache);
    Disposer.register(this, mySizeManager);
  }

  /**
   * @see EditorImpl#setPaintCallback(Runnable)
   */
  @ApiStatus.Internal
  public void setPaintCallback(@Nullable Runnable paintCallback) {
    myPaintCallback = paintCallback;
  }

  @RequiresEdt
  public int yToVisualLine(int y) {
    assertNotInBulkMode();
    return myMapper.yToVisualLine(y);
  }

  @RequiresEdt
  public int visualLineToY(int line) {
    assertNotInBulkMode();
    return myMapper.visualLineToY(line);
  }

  @RequiresEdt
  public int[] visualLineToYRange(int line) {
    assertNotInBulkMode();
    return myMapper.visualLineToYRange(line);
  }

  public @NotNull LogicalPosition offsetToLogicalPosition(int offset) {
    assertReadAccess();
    return myMapper.offsetToLogicalPosition(offset);
  }

  public int logicalPositionToOffset(@NotNull LogicalPosition pos) {
    assertReadAccess();
    return myMapper.logicalPositionToOffset(pos);
  }

  @RequiresEdt
  public @NotNull VisualPosition logicalToVisualPosition(@NotNull LogicalPosition pos, boolean beforeSoftWrap) {
    assertNotInBulkMode();
    getSoftWrapModel().prepareToMapping();
    return myMapper.logicalToVisualPosition(pos, beforeSoftWrap);
  }

  @RequiresEdt
  public @NotNull LogicalPosition visualToLogicalPosition(@NotNull VisualPosition pos) {
    assertNotInBulkMode();
    getSoftWrapModel().prepareToMapping();
    return myMapper.visualToLogicalPosition(pos);
  }

  @RequiresEdt
  public @NotNull VisualPosition offsetToVisualPosition(int offset, boolean leanTowardsLargerOffsets, boolean beforeSoftWrap) {
    assertNotInBulkMode();
    getSoftWrapModel().prepareToMapping();
    return myMapper.offsetToVisualPosition(offset, leanTowardsLargerOffsets, beforeSoftWrap);
  }

  @RequiresEdt
  public int visualPositionToOffset(VisualPosition visualPosition) {
    assertNotInBulkMode();
    getSoftWrapModel().prepareToMapping();
    return myMapper.visualPositionToOffset(visualPosition);
  }

  @RequiresEdt
  public int offsetToVisualLine(int offset, boolean beforeSoftWrap) {
    assertNotInBulkMode();
    getSoftWrapModel().prepareToMapping();
    return myMapper.offsetToVisualLine(offset, beforeSoftWrap);
  }

  @RequiresEdt
  public int visualLineToOffset(int visualLine) {
    assertNotInBulkMode();
    getSoftWrapModel().prepareToMapping();
    return myMapper.visualLineToOffset(visualLine);
  }

  @RequiresEdt
  public @NotNull VisualPosition xyToVisualPosition(@NotNull Point2D p) {
    assertNotInBulkMode();
    getSoftWrapModel().prepareToMapping();
    return myMapper.xyToVisualPosition(p);
  }

  @RequiresEdt
  public @NotNull Point2D visualPositionToXY(@NotNull VisualPosition pos) {
    assertNotInBulkMode();
    getSoftWrapModel().prepareToMapping();
    return myMapper.visualPositionToXY(pos);
  }

  @RequiresEdt
  public @NotNull Point2D offsetToXY(int offset, boolean leanTowardsLargerOffsets, boolean beforeSoftWrap) {
    assertNotInBulkMode();
    getSoftWrapModel().prepareToMapping();
    return myMapper.offsetToXY(offset, leanTowardsLargerOffsets, beforeSoftWrap);
  }

  @RequiresEdt
  public void setPrefix(String prefixText, TextAttributes attributes) {
    myPrefixText = prefixText;
    synchronized (myLock) {
      myPrefixLayout = prefixText == null || prefixText.isEmpty() ? null :
                       LineLayout.create(this, prefixText, attributes.getFontType());
    }
    myPrefixAttributes = attributes;
    mySizeManager.invalidateRange(0, 0);
  }

  public float getPrefixTextWidthInPixels() {
    LineLayout layout = getPrefixLayout();
    return layout == null ? 0 : layout.getWidth();
  }

  @RequiresEdt
  public void paint(Graphics2D g) {
    getSoftWrapModel().prepareToMapping();
    checkFontRenderContext(g.getFontRenderContext());
    myPainter.paint(g);
    if (myPaintCallback != null) {
      myPaintCallback.run();
    }
  }

  @RequiresEdt
  public void repaintCarets() {
    myPainter.repaintCarets();
  }

  @RequiresEdt
  public @NotNull Dimension getPreferredSize() {
    assert !myEditor.isPurePaintingMode();
    return ReadAction.compute(() -> {
      getSoftWrapModel().prepareToMapping();
      return mySizeManager.getPreferredSize();
    });
  }

  /**
   * Returns preferred pixel width of the lines in range.
   * <p>
   * This method is currently used only with "idea.true.smooth.scrolling" experimental option.
   *
   * @param beginLine begin visual line (inclusive)
   * @param endLine   end visual line (exclusive), may be greater than the actual number of lines
   * @return preferred pixel width
   */
  @RequiresEdt
  public int getPreferredWidth(int beginLine, int endLine) {
    assert !myEditor.isPurePaintingMode();
    return ReadAction.compute(() -> {
      getSoftWrapModel().prepareToMapping();
      return mySizeManager.getPreferredWidth(beginLine, endLine);
    });
  }

  @RequiresEdt
  public int getPreferredHeight() {
    assert !myEditor.isPurePaintingMode();
    return ReadAction.compute(() -> {
      getSoftWrapModel().prepareToMapping();
      return mySizeManager.getPreferredHeight();
    });
  }

  @RequiresEdt
  public int getMaxWidthInRange(int startOffset, int endOffset) {
    int startVisualLine = offsetToVisualLine(startOffset, false);
    int endVisualLine = offsetToVisualLine(endOffset, true);
    return getMaxTextWidthInLineRange(startVisualLine, endVisualLine) + getInsets().left;
  }

  @RequiresEdt
  public void reinitSettings() {
    synchronized (myLock) {
      myPlainSpaceWidth = -1;
      myTabSize = -1;
      setFontRenderContext(null);
    }
    myBidiFlags = switch (EditorSettingsExternalizable.getInstance().getBidiTextDirection()) {
      case LTR -> Bidi.DIRECTION_LEFT_TO_RIGHT;
      case RTL -> Bidi.DIRECTION_RIGHT_TO_LEFT;
      default -> Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT;
    };
    myLogicalPositionCache.reset(false);
    myTextLayoutCache.resetToDocumentSize(false);
    invalidateFoldRegionLayouts();
    myCharWidthCache.clear();
    setPrefix(myPrefixText, myPrefixAttributes); // recreate prefix layout
    mySizeManager.reset();
  }

  @RequiresEdt
  public void invalidateRange(int startOffset, int endOffset, boolean invalidateSize) {
    int textLength = myDocument.getTextLength();
    if (startOffset > endOffset || startOffset >= textLength || endOffset < 0) {
      return;
    }
    int startLine = myDocument.getLineNumber(Math.max(0, startOffset));
    int endLine = myDocument.getLineNumber(Math.min(textLength, endOffset));
    myTextLayoutCache.invalidateLines(startLine, endLine);
    if (invalidateSize) {
      mySizeManager.invalidateRange(startOffset, endOffset);
    }
  }

  /**
   * Invoked when a document might have changed, but no notifications were sent (for a hacky document in EditorTextFieldCellRenderer)
   */
  @RequiresEdt
  public void reset() {
    myLogicalPositionCache.reset(true);
    myTextLayoutCache.resetToDocumentSize(true);
    mySizeManager.reset();
  }

  @RequiresEdt
  public boolean isRtlLocation(@NotNull VisualPosition visualPosition) {
    if (myDocument.getTextLength() == 0) return false;
    LogicalPosition logicalPosition = visualToLogicalPosition(visualPosition);
    int offset = logicalPositionToOffset(logicalPosition);
    if (!logicalPosition.equals(offsetToLogicalPosition(offset))) return false; // virtual space
    if (getSoftWrapModel().getSoftWrap(offset) != null) {
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

  @RequiresEdt
  public boolean isAtBidiRunBoundary(@NotNull VisualPosition visualPosition) {
    int offset = visualPositionToOffset(visualPosition);
    int otherSideOffset = visualPositionToOffset(visualPosition.leanRight(!visualPosition.leansRight));
    return offset != otherSideOffset;
  }

  /**
   * Offset of the nearest boundary (not equal to {@code offset}) on the same line is returned. {@code -1} is returned if
   * the corresponding boundary is not found.
   */
  @RequiresEdt
  public int findNearestDirectionBoundary(int offset, boolean lookForward) {
    int textLength = myDocument.getTextLength();
    if (textLength == 0 || offset < 0 || offset > textLength) return -1;
    int line = myDocument.getLineNumber(offset);
    LineLayout layout = myTextLayoutCache.getLineLayout(line);
    int lineStartOffset = myDocument.getLineStartOffset(line);
    int relativeOffset = layout.findNearestDirectionBoundary(offset - lineStartOffset, lookForward);
    return relativeOffset < 0 ? -1 : lineStartOffset + relativeOffset;
  }

  public float getPlainSpaceWidth() {
    synchronized (myLock) {
      initMetricsIfNeeded();
      return myPlainSpaceWidth;
    }
  }

  public int getCaretHeight() {
    synchronized (myLock) {
      initMetricsIfNeeded();
      return myEditor.getSettings().isFullLineHeightCursor()
        ? myLineHeight
        : myLineHeight + myTopOverhang + myBottomOverhang;
    }
  }

  public int getLineHeight() {
    synchronized (myLock) {
      initMetricsIfNeeded();
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
      return myDescent;
    }
  }

  public int getCharHeight() {
    synchronized (myLock) {
      initMetricsIfNeeded();
      return myCharHeight;
    }
  }

  public int getAscent() {
    synchronized (myLock) {
      initMetricsIfNeeded();
      return myLineHeight - myDescent;
    }
  }

  public int getTopOverhang() {
    synchronized (myLock) {
      initMetricsIfNeeded();
      return myTopOverhang;
    }
  }

  public int getBottomOverhang() {
    synchronized (myLock) {
      initMetricsIfNeeded();
      return myBottomOverhang;
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

  public int offsetToVisualColumnInFoldRegion(@NotNull FoldRegion region, int offset, boolean leanTowardsLargerOffsets) {
    if (offset < 0 || offset == 0 && !leanTowardsLargerOffsets) return 0;
    String text = region.getPlaceholderText();
    if (offset > text.length()) {
      offset = text.length();
      leanTowardsLargerOffsets = true;
    }
    int logicalColumn = LogicalPositionCache.calcColumn(text, 0, 0, offset, getTabSize());
    int maxColumn = 0;
    for (LineLayout.VisualFragment fragment : getFoldRegionLayout(region).getFragmentsInVisualOrder(0)) {
      int startLC = fragment.getStartLogicalColumn();
      int endLC = fragment.getEndLogicalColumn();
      if (logicalColumn > startLC && logicalColumn < endLC ||
          logicalColumn == startLC && leanTowardsLargerOffsets ||
          logicalColumn == endLC && !leanTowardsLargerOffsets) {
        return fragment.logicalToVisualColumn(logicalColumn);
      }
      maxColumn = fragment.getEndVisualColumn();
    }
    return maxColumn;
  }

  public int visualColumnToOffsetInFoldRegion(@NotNull FoldRegion region, int visualColumn, boolean leansRight) {
    if (visualColumn < 0 || visualColumn == 0 && !leansRight) return 0;
    String text = region.getPlaceholderText();
    for (LineLayout.VisualFragment fragment : getFoldRegionLayout(region).getFragmentsInVisualOrder(0)) {
      int startVC = fragment.getStartVisualColumn();
      int endVC = fragment.getEndVisualColumn();
      if (visualColumn > startVC && visualColumn < endVC ||
          visualColumn == startVC && leansRight ||
          visualColumn == endVC && !leansRight) {
        int logicalColumn = fragment.visualToLogicalColumn(visualColumn);
        return LogicalPositionCache.calcOffset(text, logicalColumn, 0, 0, text.length(), getTabSize());
      }
    }
    return text.length();
  }

  public void invalidateFoldRegionLayout(FoldRegion region) {
    region.putUserData(FOLD_REGION_TEXT_LAYOUT, null);
  }

  public int getVisibleLineCount() {
    return Math.max(1, getVisibleLogicalLinesCount() + getSoftWrapModel().getSoftWrapsIntroducedLinesNumber());
  }

  public @NotNull LogicalPosition xyToLogicalPosition(@NotNull Point p) {
    Point pp = p.x >= 0 && p.y >= 0 ? p : new Point(Math.max(p.x, 0), Math.max(p.y, 0));
    return visualToLogicalPosition(xyToVisualPosition(pp));
  }

  @Override
  public void drawChars(@NotNull Graphics g, char @NotNull [] data, int start, int end, int x, int y, @NotNull Color color, @NotNull FontInfo fontInfo) {
    myPainter.drawChars(g, data, start, end, x, y, color, fontInfo);
  }

  @Override
  public void dispose() {
    getScrollingModel().removeVisibleAreaListener(this);
    myEditor.getContentComponent().removeHierarchyListener(this);
  }

  @Override
  public void hierarchyChanged(HierarchyEvent e) {
    if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && e.getComponent().isShowing()) {
      checkFontRenderContext(null);
    }
  }

  @Override
  public void visibleAreaChanged(@NotNull VisibleAreaEvent e) {
    checkFontRenderContext(null);
  }

  @Override
  public @NotNull String dumpState() {
    String prefixText = myPrefixText;
    TextAttributes prefixAttributes = myPrefixAttributes;
    synchronized (myLock) {
      return "[prefix text: " + prefixText +
             ", prefix attributes: " + prefixAttributes +
             ", space width: " + myPlainSpaceWidth +
             ", line height: " + myLineHeight +
             ", descent: " + myDescent +
             ", char height: " + myCharHeight +
             ", max char width: " + myMaxCharWidth +
             ", tab size: " + myTabSize +
             " ,size manager: " + mySizeManager.dumpState() +
             " ,logical position cache: " + myLogicalPositionCache.dumpState() +
             "]";
    }
  }

  @TestOnly
  public void validateState() {
    myLogicalPositionCache.validateState();
    mySizeManager.validateState();
  }

  float getMaxCharWidth() {
    synchronized (myLock) {
      initMetricsIfNeeded();
      return myMaxCharWidth;
    }
  }

  int getCapHeight() {
    synchronized (myLock) {
      initMetricsIfNeeded();
      return myCapHeight;
    }
  }

  /**
   * If {@code quickEvaluationListener} is provided, quick approximate size evaluation becomes enabled, listener will be invoked
   * if approximation will in fact be used during width calculation.
   */
  int getMaxTextWidthInLineRange(int startVisualLine, int endVisualLine) {
    getSoftWrapModel().prepareToMapping();
    int maxWidth = 0;
    VisualLinesIterator iterator = new VisualLinesIterator(this, startVisualLine);
    while (!iterator.atEnd() && iterator.getVisualLine() <= endVisualLine) {
      int width = mySizeManager.getVisualLineWidth(iterator, false);
      maxWidth = Math.max(maxWidth, width);
      iterator.advance();
    }
    return maxWidth;
  }

  LineLayout getPrefixLayout() {
    synchronized (myLock) {
      FoldRegion[] topLevelRegions = getFoldingModel().fetchTopLevel();
      if (topLevelRegions != null && topLevelRegions.length > 0) {
        FoldRegion firstRegion = topLevelRegions[0];
        if (firstRegion instanceof CustomFoldRegion && firstRegion.getStartOffset() == 0) {
          return null; // prefix is hidden
        }
      }
      return myPrefixLayout;
    }
  }

  TextAttributes getPrefixAttributes() {
    return myPrefixAttributes;
  }

  boolean isAd() {
    return myEditorModel.isAd();
  }

  EditorImpl getEditor() {
    return myEditor;
  }

  DocumentEx getDocument() {
    return myDocument;
  }

  FoldingModelInternal getFoldingModel() {
    return myEditorModel.getFoldingModel();
  }

  InlayModelEx getInlayModel() {
    return myEditorModel.getInlayModel();
  }

  SoftWrapModelImpl getSoftWrapModel() {
    return (SoftWrapModelImpl)myEditorModel.getSoftWrapModel();
  }

  MarkupModelEx getFilteredDocumentMarkupModel() {
    return myEditorModel.getDocumentMarkupModel();
  }

  MarkupModelEx getMarkupModel() {
    return myEditorModel.getEditorMarkupModel();
  }

  CaretModelImpl getCaretModel() {
    return (CaretModelImpl)myEditorModel.getCaretModel();
  }

  SelectionModel getSelectionModel() {
    return myEditorModel.getSelectionModel();
  }

  EditorHighlighter getHighlighter() {
    return myEditorModel.getHighlighter();
  }

  FocusModeModel getFocusModel() {
    return myEditorModel.getFocusModel();
  }

  ScrollingModel getScrollingModel() {
    return myEditorModel.getScrollingModel();
  }

  FontRenderContext getFontRenderContext() {
    synchronized (myLock) {
      return myFontRenderContext;
    }
  }

  EditorSizeManager getSizeManager() {
    return mySizeManager;
  }

  TextLayoutCache getTextLayoutCache() {
    return myTextLayoutCache;
  }

  TabFragment getTabFragment() {
    return myTabFragment;
  }

  LogicalPositionCache getLogicalPositionCache() {
    return myLogicalPositionCache;
  }

  float getRightAlignmentLineStartX(int visualLine) {
    return myMapper.getRightAlignmentLineStartX(visualLine);
  }

  int getRightAlignmentMarginX() {
    return myMapper.getRightAlignmentMarginX();
  }

  LineLayout getFoldRegionLayout(FoldRegion foldRegion) {
    LineLayout layout = foldRegion.getUserData(FOLD_REGION_TEXT_LAYOUT);
    if (layout == null) {
      TextAttributes placeholderAttributes = getFoldingModel().getPlaceholderAttributes();
      layout = LineLayout.create(this, StringUtil.replace(foldRegion.getPlaceholderText(), "\n", " "),
                              placeholderAttributes == null ? Font.PLAIN : placeholderAttributes.getFontType());
      foldRegion.putUserData(FOLD_REGION_TEXT_LAYOUT, layout);
    }
    return layout;
  }

  float getCodePointWidth(int codePoint, @JdkConstants.FontStyle int fontStyle) {
    var grid = myEditor.getCharacterGrid();
    if (grid != null) {
      return grid.codePointWidth(codePoint);
    }
    else {
      return myCharWidthCache.getCodePointWidth(codePoint, fontStyle);
    }
  }

  Insets getInsets() {
    return myEditor.getContentComponent().getInsets();
  }

  int getBidiFlags() {
    return myBidiFlags;
  }

  private void invalidateFoldRegionLayouts() {
    ReadAction.run(() -> {
      for (FoldRegion region : getFoldingModel().getAllFoldRegions()) {
        invalidateFoldRegionLayout(region);
      }
    });
  }

  /**
   * @return the number of visible logical lines, which is the number of total logical lines minus the number of folded lines
   */
  private int getVisibleLogicalLinesCount() {
    return getDocument().getLineCount() - getFoldingModel().getTotalNumberOfFoldedLines();
  }

  // guarded by myLock
  private void initMetricsIfNeeded() {
    if (myPlainSpaceWidth >= 0) return;

    Font font = myEditor.getColorsScheme().getFont(EditorFontType.PLAIN);
    FontMetrics fm = FontInfo.getFontMetrics(font, myFontRenderContext);

    float width = FontLayoutService.getInstance().charWidth2D(fm, ' ');
    myPlainSpaceWidth = width > 0 ? width : 1;

    myCharHeight = FontLayoutService.getInstance().charWidth(fm, 'a');

    float verticalScalingFactor = getVerticalScalingFactor();

    int fontMetricsHeight = FontLayoutService.getInstance().getHeight(fm);
    int lineHeight;
    if (Registry.is("editor.text.xcode.vertical.spacing")) {
      //Here we approximate line calculation to the variant used in Xcode 9 editor
      LineMetrics metrics = font.getLineMetrics("", myFontRenderContext);

      double height = Math.ceil(metrics.getHeight()) + metrics.getLeading();
      double delta = verticalScalingFactor - 1;
      int spacing;
      if (Math.round((height * delta) / 2) <= 1) {
        spacing = delta > 0 ? 2 : 0;
      }
      else {
        spacing = ((int)Math.ceil((height * delta) / 2)) * 2;
      }
      lineHeight = (int)Math.ceil(height) + spacing;
    }
    else if (Registry.is("editor.text.vertical.spacing.correct.rounding")) {
      if (verticalScalingFactor == 1f) {
        lineHeight = fontMetricsHeight;
      }
      else {
        Font scaledFont = font.deriveFont(font.getSize() * verticalScalingFactor);
        FontMetrics scaledMetrics = FontInfo.getFontMetrics(scaledFont, myFontRenderContext);
        lineHeight = FontLayoutService.getInstance().getHeight(scaledMetrics);
      }
    }
    else {
      lineHeight = (int)Math.ceil(fontMetricsHeight * verticalScalingFactor);
    }
    myLineHeight = Math.max(1, lineHeight);
    int descent = FontLayoutService.getInstance().getDescent(fm);
    myDescent = descent + (myLineHeight - fontMetricsHeight) / 2;
    myTopOverhang = fontMetricsHeight - myLineHeight + myDescent - descent;
    myBottomOverhang = descent - myDescent;

    // assuming that bold italic 'W' gives a good approximation of font's widest character
    FontMetrics fmBI = FontInfo.getFontMetrics(myEditor.getColorsScheme().getFont(EditorFontType.BOLD_ITALIC), myFontRenderContext);
    myMaxCharWidth = FontLayoutService.getInstance().charWidth2D(fmBI, 'W');

    myCapHeight = (int)font.createGlyphVector(myFontRenderContext, "H").getVisualBounds().getHeight();
  }

  // guarded by myLock
  private boolean setFontRenderContext(FontRenderContext context) {
    FontRenderContext contextToSet = context == null ? FontInfo.getFontRenderContext(myEditor.getContentComponent()) : context;
    if (areEqualContexts(myFontRenderContext, contextToSet)) return false;

    AffineTransform transform = contextToSet.getTransform();
    if (transform.getDeterminant() == 0) {
      LOG.error("Incorrect transform in FontRenderContext" + (context == null ? " obtained from component" : "") + ": " + transform);
      contextToSet = new FontRenderContext(new AffineTransform(),
                                           contextToSet.getAntiAliasingHint(), contextToSet.getFractionalMetricsHint());
    }

    Object fmHint = UISettings.getEditorFractionalMetricsHint();
    myFontRenderContext = contextToSet.getFractionalMetricsHint() == fmHint
                          ? contextToSet
                          : new FontRenderContext(contextToSet.getTransform(),
                                                  contextToSet.getAntiAliasingHint(),
                                                  fmHint);
    return true;
  }

  private void checkFontRenderContext(FontRenderContext context) {
    boolean contextUpdated = false;
    synchronized (myLock) {
      if (setFontRenderContext(context)) {
        myPlainSpaceWidth = -1;
        contextUpdated = true;
      }
    }
    if (contextUpdated) {
      myTextLayoutCache.resetToDocumentSize(false);
      invalidateFoldRegionLayouts();
      myCharWidthCache.clear();
      getFoldingModel().updateCachedOffsets();
    }
  }

  private void assertReadAccess() {
    if (!myEditorModel.isAd()) {
      ThreadingAssertions.assertReadAccess();
    }
  }

  private void assertNotInBulkMode() {
    if (myDocument instanceof DocumentImpl) {
      ((DocumentImpl)myDocument).assertNotInBulkUpdate();
    }
    else if (myDocument.isInBulkUpdate()) {
      throw new IllegalStateException("Current operation is not permitted in bulk mode");
    }
    if (getInlayModel().isInBatchMode()) {
      throw new IllegalStateException("Current operation is not permitted during batch inlay update");
    }
  }

  private static boolean areEqualContexts(FontRenderContext c1, FontRenderContext c2) {
    if (c1 == c2) return true;
    if (c1 == null || c2 == null) return false;
    // We ignore fractional metrics aspect of contexts, because we it's not changing during editor's lifecycle.
    // And it has different values for component graphics (ON/OFF) and component's font metrics (DEFAULT), causing
    // unnecessary layout cache resets.
    return c1.getTransform().equals(c2.getTransform()) && c1.getAntiAliasingHint().equals(c2.getAntiAliasingHint());
  }
}
