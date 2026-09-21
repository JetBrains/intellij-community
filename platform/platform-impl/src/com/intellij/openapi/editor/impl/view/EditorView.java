// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.diagnostic.Dumpable;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CustomFoldRegion;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.EditorThreading;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorModel;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.InlayModelEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.impl.CaretModelImpl;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.FocusModeModel;
import com.intellij.openapi.editor.impl.FoldingModelInternal;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.openapi.editor.impl.SoftWrapModelImpl;
import com.intellij.openapi.editor.impl.caret.model.CaretCursor;
import com.intellij.openapi.editor.impl.caret.model.CaretRectangle;
import com.intellij.openapi.editor.impl.caret.model.CaretRepaintMetrics;
import com.intellij.openapi.editor.impl.TextDrawingCallback;
import com.intellij.openapi.editor.impl.view.animation.EditorPainterCache;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.DocumentInternalUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.JdkConstants;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.text.Bidi;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

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
  private static final LineLayout NOT_INITIALIZED_PREFIX = new SingleChunkLayout(null);
  private static final AtomicReferenceFieldUpdater<EditorView, EditorViewSnapshot> SNAPSHOT_UPDATER
    = AtomicReferenceFieldUpdater.newUpdater(EditorView.class, EditorViewSnapshot.class, "mySnapshot");

  private volatile EditorViewSnapshot mySnapshot;
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
  private final SelectionVisualModel mySelectionVisualModel;

  public EditorView(@NotNull EditorImpl editor) {
    this(editor, editor.getEditorModel());
  }

  public EditorView(@NotNull EditorImpl editor, @NotNull EditorModel editorModel) {
    myEditor = editor;
    mySnapshot = new EditorViewSnapshot(normalizeFontRenderContext(readFontRenderContext(), true));
    myEditorModel = editorModel;
    myDocument = myEditorModel.getDocument();
    myPainter = new EditorPainter(this);
    myMapper = new EditorCoordinateMapper(this);
    mySizeManager = new EditorSizeManager(this);
    myTextLayoutCache = new TextLayoutCache(this, new ComponentVisibilityTracker(myEditor.getContentComponent()));
    myLogicalPositionCache = new LogicalPositionCache(myDocument, () -> myEditor.throwDisposalError("Editor is already disposed"));
    myCharWidthCache = new CharWidthCache(this);
    myTabFragment = new TabFragment(this);
    mySelectionVisualModel = new SelectionVisualModel(myEditor);

    myEditor.getContentComponent().addHierarchyListener(this);
    getScrollingModel().addVisibleAreaListener(this);

    Disposer.register(this, myLogicalPositionCache);
    Disposer.register(this, myTextLayoutCache);
    Disposer.register(this, mySizeManager);
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
  public int @NotNull [] visualLineToYRange(int line) {
    assertNotInBulkMode();
    return myMapper.visualLineToYRange(line);
  }

  public @NotNull LogicalPosition offsetToLogicalPosition(int offset) {
    return myMapper.offsetToLogicalPosition(offset);
  }

  public int offsetToLogicalColumn(int line, int intraLineOffset) {
    return myMapper.offsetToLogicalColumn(line, intraLineOffset);
  }

  public int logicalPositionToOffset(@NotNull LogicalPosition pos) {
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

  public float getPrefixTextWidthInPixels() {
    LineLayout layout = getPrefixLayout();
    return layout == null ? 0 : layout.getWidth();
  }

  public void paint(@NotNull Graphics2D g, @Nullable EditorPainterCache cache) {
    getSoftWrapModel().prepareToMapping();
    checkFontRenderContext(g.getFontRenderContext());
    Rectangle clip = g.getClipBounds();
    if (cache != null && clip != null && cache.paintFromCache(g, clip)) {
      paintCaretFrame(g);
      runPaintCallback();
      return;
    }
    myPainter.paint(g);
    runPaintCallback();
  }

  @RequiresEdt
  public @NotNull CaretRepaintMetrics getCaretRepaintMetrics() {
    EditorViewSnapshot snapshot = getSnapshotWithMetrics();
    return new CaretRepaintMetrics(snapshot.caretHeight(), snapshot.caretTopOverhang());
  }

  @RequiresEdt
  public @NotNull List<Rectangle> caretRectanglesForLocations(@NotNull List<CaretRectangle> locations) {
    return myPainter.caretRectanglesForLocations(locations);
  }

  private void clearContentAnimationCache() {
    myEditor.invalidateAnimationCaches(null);
  }

  public void repaintCarets(@NotNull CaretCursor caretCursor) {
    myPainter.repaintCarets(caretCursor);
  }

  @RequiresEdt
  public @NotNull Dimension getPreferredSize() {
    assert !myEditor.isPurePaintingMode();
    getSoftWrapModel().prepareToMapping();
    return mySizeManager.getPreferredSize();
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
    getSoftWrapModel().prepareToMapping();
    return mySizeManager.getPreferredWidth(beginLine, endLine);
  }

  @RequiresEdt
  public int getPreferredHeight() {
    assert !myEditor.isPurePaintingMode();
    getSoftWrapModel().prepareToMapping();
    return mySizeManager.getPreferredHeight();
  }

  @RequiresEdt
  public int getMaxWidthInRange(int startOffset, int endOffset) {
    int startVisualLine = offsetToVisualLine(startOffset, false);
    int endVisualLine = offsetToVisualLine(endOffset, true);
    return getMaxTextWidthInLineRange(startVisualLine, endVisualLine) + getInsets().left;
  }

  @RequiresEdt
  public boolean isRtlLocation(@NotNull VisualPosition visualPosition) {
    if (myDocument.getTextLength() == 0) {
      return false;
    }
    LogicalPosition logicalPosition = visualToLogicalPosition(visualPosition);
    int offset = logicalPositionToOffset(logicalPosition);
    if (!logicalPosition.equals(offsetToLogicalPosition(offset))) {
      return false; // virtual space
    }
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
    if (textLength == 0 || offset < 0 || offset > textLength) {
      return -1;
    }
    int line = myDocument.getLineNumber(offset);
    LineLayout layout = myTextLayoutCache.getLineLayout(line);
    int lineStartOffset = myDocument.getLineStartOffset(line);
    int relativeOffset = layout.findNearestDirectionBoundary(offset - lineStartOffset, lookForward);
    return relativeOffset < 0 ? -1 : lineStartOffset + relativeOffset;
  }

  public int offsetToVisualColumnInFoldRegion(@NotNull FoldRegion region, int offset, boolean leanTowardsLargerOffsets) {
    if (offset < 0 || offset == 0 && !leanTowardsLargerOffsets) return 0;
    String text = region.getPlaceholderText();
    if (offset > text.length()) {
      offset = text.length();
      leanTowardsLargerOffsets = true;
    }
    int maxColumn = 0;
    int logicalColumn = DocumentInternalUtil.calcLogicalColumn(text, 0, 0, offset, getTabSize());
    for (LineVisualFragment fragment : getFoldRegionLayout(region).getFragmentsInVisualOrder(0)) {
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
    for (LineVisualFragment fragment : getFoldRegionLayout(region).getFragmentsInVisualOrder(0)) {
      int startVC = fragment.getStartVisualColumn();
      int endVC = fragment.getEndVisualColumn();
      if (visualColumn > startVC && visualColumn < endVC ||
          visualColumn == startVC && leansRight ||
          visualColumn == endVC && !leansRight) {
        int logicalColumn = fragment.visualToLogicalColumn(visualColumn);
        return DocumentInternalUtil.calcLogicalOffset(text, logicalColumn, 0, 0, text.length(), getTabSize());
      }
    }
    return text.length();
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

  @RequiresEdt
  public void setPrefix(@Nullable String prefixText, @Nullable TextAttributes attributes) {
    checkPrefixAttributes(prefixText, attributes);
    var prefix = (prefixText == null || prefixText.isEmpty()) && attributes == null
                 ? null
                 : new EditorPrefix(prefixText, attributes, NOT_INITIALIZED_PREFIX);
    SNAPSHOT_UPDATER.updateAndGet(this, snapshot -> snapshot.withPrefix(prefix));
    mySizeManager.invalidateRange(0, 0);
  }

  /**
   * @see EditorImpl#setPaintCallback(Runnable)
   */
  public void setPaintCallback(@Nullable Runnable paintCallback) {
    SNAPSHOT_UPDATER.updateAndGet(this, snapshot -> snapshot.withPaintCallback(paintCallback));
  }

  @RequiresEdt
  public void reinitSettings() {
    clearContentAnimationCache();
    SNAPSHOT_UPDATER.updateAndGet(this, snapshot -> {
      FontRenderContext newFontRenderContext = readFontRenderContext(snapshot);
      int newBidiFlags = readBidiFlags();
      int newTabSize = readTabSize();
      return snapshot
        .withFontRenderContext(newFontRenderContext)
        .withMetrics(EditorViewMetrics.UNINITIALIZED)
        .withTabSize(newTabSize)
        .withBidiFlags(newBidiFlags)
        .withPrefixLayout(NOT_INITIALIZED_PREFIX);
    });
    myLogicalPositionCache.reset(false, getTabSize());
    myTextLayoutCache.resetToDocumentSize(false);
    invalidateFoldRegionLayouts();
    myCharWidthCache.clear();
    mySizeManager.reset();
  }

  @RequiresEdt
  public void invalidateRange(int startOffset, int endOffset, boolean invalidateSize) {
    clearContentAnimationCache();
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
    clearContentAnimationCache();
    myLogicalPositionCache.reset(true, getTabSize());
    myTextLayoutCache.resetToDocumentSize(true);
    mySizeManager.reset();
  }

  public void invalidateFoldRegionLayout(@NotNull FoldRegion region) {
    region.putUserData(FOLD_REGION_TEXT_LAYOUT, null);
  }

  @Override
  public void dispose() {
    getScrollingModel().removeVisibleAreaListener(this);
    myEditor.getContentComponent().removeHierarchyListener(this);
  }

  @Override
  public void hierarchyChanged(@NotNull HierarchyEvent e) {
    if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && e.getComponent().isShowing()) {
      checkFontRenderContext(null);
    }
  }

  @Override
  public void visibleAreaChanged(@NotNull VisibleAreaEvent e) {
    clearContentAnimationCache();
    checkFontRenderContext(null);
  }

  @Override
  public @NotNull String dumpState() {
    EditorViewSnapshot snapshot = mySnapshot;
    String prefixText = snapshot.prefixText();
    TextAttributes prefixAttributes = snapshot.prefixAttributes();
    EditorViewMetrics metrics = snapshot.metrics();
    float plainSpaceWidth = metrics.plainSpaceWidth();
    int lineHeight = metrics.lineHeight();
    int descent = metrics.descent();
    int charHeight = metrics.charHeight();
    float charWidth = metrics.maxCharWidth();
    return "[prefix text: " + prefixText +
           ", prefix attributes: " + prefixAttributes +
           ", space width: " + plainSpaceWidth +
           ", line height: " + lineHeight +
           ", descent: " + descent +
           ", char height: " + charHeight +
           ", max char width: " + charWidth +
           ", tab size: " + snapshot.tabSize() +
           " ,size manager: " + mySizeManager.dumpState() +
           " ,logical position cache: " + myLogicalPositionCache.dumpState() +
           "]";
  }

  @VisibleForTesting
  public LogicalPositionCache getLogicalPositionCache() {
    return myLogicalPositionCache;
  }

  @TestOnly
  public void validateState() {
    myLogicalPositionCache.validateState();
    mySizeManager.validateState();
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

  @Nullable LineLayout getPrefixLayout() {
    FoldRegion[] topLevelRegions = getFoldingModel().fetchTopLevel();
    if (topLevelRegions != null && topLevelRegions.length > 0) {
      FoldRegion firstRegion = topLevelRegions[0];
      if (firstRegion instanceof CustomFoldRegion && firstRegion.getStartOffset() == 0) {
        return null; // prefix is hidden
      }
    }
    return getOrComputePrefixLayout();
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

  @NotNull SelectionVisualModel getSelectionVisualModel() {
    return mySelectionVisualModel;
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

  float getRightAlignmentLineStartX(int visualLine) {
    return myMapper.getRightAlignmentLineStartX(visualLine);
  }

  int getRightAlignmentMarginX() {
    return myMapper.getRightAlignmentMarginX();
  }

  @NotNull LineLayout getFoldRegionLayout(@NotNull FoldRegion foldRegion) {
    LineLayout layout = foldRegion.getUserData(FOLD_REGION_TEXT_LAYOUT);
    if (layout == null) {
      TextAttributes placeholderAttributes = getFoldingModel().getPlaceholderAttributes();
      layout = LineLayout.createForStandaloneText(
        this,
        StringUtil.replace(foldRegion.getPlaceholderText(), "\n", " "),
        placeholderAttributes == null ? Font.PLAIN : placeholderAttributes.getFontType()
      );
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

  private void paintCaretFrame(Graphics2D graphics) {
    CaretCursor caretCursor = myEditor.getCaretCursor(true);
    if (caretCursor == null) {
      return;
    }
    Rectangle clip = graphics.getClipBounds();
    if (clip == null) {
      return;
    }
    myPainter.paintCaret(graphics, caretCursor, clip.y);
  }

  private void runPaintCallback() {
    if (myEditor.isCurrentlyBuildingCache()) {
      return;
    }
    Runnable callback = mySnapshot.paintCallback();
    if (callback != null) {
      callback.run();
    }
  }

  private void invalidateFoldRegionLayouts() {
    EditorThreading.run(() -> {
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

  @NotNull FontRenderContext getFontRenderContext() {
    return mySnapshot.fontRenderContext();
  }

  public float getPlainSpaceWidth() {
    return getSnapshotWithMetrics().plainSpaceWidth();
  }

  public int getCaretHeight() {
    return getSnapshotWithMetrics().caretHeight();
  }

  public int getLineHeight() {
    return getSnapshotWithMetrics().lineHeight();
  }

  public int getDescent() {
    return getSnapshotWithMetrics().descent();
  }

  public int getCharHeight() {
    return getSnapshotWithMetrics().charHeight();
  }

  public int getAscent() {
    return getSnapshotWithMetrics().ascent();
  }

  public int getTopOverhang() {
    return getSnapshotWithMetrics().topOverhang();
  }

  public int getBottomOverhang() {
    return getSnapshotWithMetrics().bottomOverhang();
  }

  public int getTabSize() {
    while (true) {
      EditorViewSnapshot snapshot = mySnapshot;
      int tabSize = snapshot.tabSize();
      if (tabSize != -1) {
        return tabSize;
      }
      int newTabSize = readTabSize();
      EditorViewSnapshot newSnapshot = snapshot.withTabSize(newTabSize);
      if (SNAPSHOT_UPDATER.compareAndSet(this, snapshot, newSnapshot)) {
        return newTabSize;
      }
    }
  }

  float getMaxCharWidth() {
    return getSnapshotWithMetrics().maxCharWidth();
  }

  int getCapHeight() {
    return getSnapshotWithMetrics().capHeight();
  }

  int getBidiFlags() {
    return mySnapshot.bidiFlags();
  }

  TextAttributes getPrefixAttributes() {
    return mySnapshot.prefixAttributes();
  }

  public @NotNull EditorViewSnapshot getSnapshot() {
    while (true) {
      EditorViewSnapshot snapshot = mySnapshot;
      if (snapshot.metrics() != EditorViewMetrics.UNINITIALIZED &&
          snapshot.tabSize() != -1 &&
          snapshot.prefixLayout() != NOT_INITIALIZED_PREFIX) {
        return snapshot;
      }
      initAllLazyFields();
    }
  }

  private void initAllLazyFields() {
    getOrComputePrefixLayout();
  }

  private @NotNull FontRenderContext readFontRenderContext(@NotNull EditorViewSnapshot snapshot) {
    FontRenderContext oldContext = snapshot.fontRenderContext();
    FontRenderContext context = computeFontRenderContext(oldContext, null);
    FontRenderContext newFontRenderContext = context != null ? context : oldContext;
    return newFontRenderContext;
  }

  private static int readBidiFlags() {
    return switch (EditorSettingsExternalizable.getInstance().getBidiTextDirection()) {
      case LTR -> Bidi.DIRECTION_LEFT_TO_RIGHT;
      case RTL -> Bidi.DIRECTION_RIGHT_TO_LEFT;
      default -> Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT;
    };
  }

  /**
   * Reads the tab size, and keeps it positive.
   * <p>
   * A tab size of -1 marks a snapshot that holds none, so a non-positive answer must never reach the
   * snapshot. {@link EditorSettings} is an interface, and an implementation can answer with any value.
   */
  private int readTabSize() {
    return Math.max(1, EditorUtil.getTabSize(myEditor));
  }

  /**
   * Returns the prefix layout, and computes it when the snapshot holds {@link #NOT_INITIALIZED_PREFIX}.
   */
  private @Nullable LineLayout getOrComputePrefixLayout() {
    getTabSize(); // force tabSize and metrics calculations to avoid nested CAS loop in createForStandaloneText
    getSnapshotWithMetrics();
    while (true) {
      EditorViewSnapshot snapshot = mySnapshot;
      LineLayout layout = snapshot.prefixLayout();
      if (layout != NOT_INITIALIZED_PREFIX) {
        return layout;
      }
      String prefixText = snapshot.prefixText();
      TextAttributes attributes = snapshot.prefixAttributes();
      // setPrefix rejects a text without attributes, so the last test only makes the null safety local.
      LineLayout newPrefixLayout = prefixText == null || prefixText.isEmpty() || attributes == null
                                   ? null
                                   : LineLayout.createForStandaloneText(this, prefixText, attributes.getFontType());
      EditorViewSnapshot newSnapshot = snapshot.withPrefixLayout(newPrefixLayout);
      if (SNAPSHOT_UPDATER.compareAndSet(this, snapshot, newSnapshot)) {
        return newPrefixLayout;
      }
    }
  }

  private @NotNull EditorViewSnapshot getSnapshotWithMetrics() {
    while (true) {
      EditorViewSnapshot snapshot = mySnapshot;
      EditorViewMetrics metrics = snapshot.metrics();
      if (metrics != EditorViewMetrics.UNINITIALIZED) {
        return snapshot;
      }
      EditorViewMetrics newMetrics = createNewMetrics(snapshot);
      EditorViewSnapshot newSnapshot = snapshot.withMetrics(newMetrics);
      if (SNAPSHOT_UPDATER.compareAndSet(this, snapshot, newSnapshot)) {
        return newSnapshot;
      }
    }
  }

  private @NotNull EditorViewMetrics createNewMetrics(@NotNull EditorViewSnapshot snapshot) {
    FontRenderContext fontRenderContext = snapshot.fontRenderContext();

    Editor editor = myEditor;
    EditorColorsScheme colorsScheme = editor.getColorsScheme();
    EditorSettings editorSettings = editor.getSettings();
    boolean editorOneLineMode = editor.isOneLineMode();
    boolean fullLineHeightCursor = editorSettings.isFullLineHeightCursor();

    FontLayoutService fontLayout = FontLayoutService.getInstance();
    Font font = colorsScheme.getFont(EditorFontType.PLAIN);
    FontMetrics fm = FontInfo.getFontMetrics(font, fontRenderContext);
    float width = fontLayout.charWidth2D(fm, ' ');

    float newPlainSpaceWidth = width > 0 ? width : 1;
    int newCharHeight = fontLayout.charWidth(fm, 'a');

    float verticalScalingFactor = editorOneLineMode ? 1 : getVerticalScalingFactor(colorsScheme);
    int fontMetricsHeight = fontLayout.getHeight(fm);
    int lineHeight = getLineHeight(font, fontRenderContext, fontMetricsHeight, verticalScalingFactor);
    int newLineHeight = Math.max(1, lineHeight);

    int descent = fontLayout.getDescent(fm);
    int newDescent = descent + (newLineHeight - fontMetricsHeight) / 2;
    int newTopOverhang = fontMetricsHeight - newLineHeight + newDescent - descent;
    int newBottomOverhang = descent - newDescent;
    int newCaretHeight = fullLineHeightCursor ? newLineHeight : newLineHeight + newTopOverhang + newBottomOverhang;
    // A full line height caret starts at the line top, so it overhangs nothing.
    int newCaretTopOverhang = fullLineHeightCursor ? 0 : newTopOverhang;

    // assuming that bold italic 'W' gives a good approximation of font's widest character
    FontMetrics fmBI = FontInfo.getFontMetrics(colorsScheme.getFont(EditorFontType.BOLD_ITALIC), fontRenderContext);
    float newMaxCharWidth = fontLayout.charWidth2D(fmBI, 'W');
    int newCapHeight = (int)font.createGlyphVector(fontRenderContext, "H").getVisualBounds().getHeight();

    return new EditorViewMetrics(
      /* plainSpaceWidth= */ newPlainSpaceWidth,
      /* lineHeight= */ newLineHeight,
      /* descent= */ newDescent,
      /* ascent= */ newLineHeight - newDescent,
      /* charHeight= */ newCharHeight,
      /* maxCharWidth= */ newMaxCharWidth,
      /* capHeight= */ newCapHeight,
      /* topOverhang= */ newTopOverhang,
      /* bottomOverhang= */ newBottomOverhang,
      /* caretHeight= */ newCaretHeight,
      /* caretTopOverhang= */ newCaretTopOverhang
    );
  }

  /**
   * Computes the font render context.
   *
   * @param oldContext the current context, or null before the first computation
   * @param context    the new context, or null to read the context from the content component
   * @return the normalized context, or null when the context did not change
   */
  private @Nullable FontRenderContext computeFontRenderContext(
    @Nullable FontRenderContext oldContext,
    @Nullable FontRenderContext context
  ) {
    boolean fromComponent = context == null;
    FontRenderContext contextToSet = fromComponent ? readFontRenderContext() : context;
    if (areEqualContexts(oldContext, contextToSet)) {
      return null;
    }
    return normalizeFontRenderContext(contextToSet, fromComponent);
  }

  /**
   * Reads the font render context from the content component.
   * <p>
   * The component supplies no context on some platforms. The default context replaces it then. The editor
   * keeps the default context until the component supplies a real one.
   */
  private @NotNull FontRenderContext readFontRenderContext() {
    FontRenderContext context = FontInfo.getFontRenderContext(myEditor.getContentComponent());
    return context != null ? context : FontInfo.getFontRenderContext(null);
  }

  /**
   * Replaces a degenerate transform, then applies the fractional metrics hint of the UI settings.
   *
   * @param fromComponent true when the content component supplied the context. It only marks the log message.
   */
  private static @NotNull FontRenderContext normalizeFontRenderContext(
    @NotNull FontRenderContext context,
    boolean fromComponent
  ) {
    FontRenderContext result = context;
    AffineTransform transform = result.getTransform();
    if (transform.getDeterminant() == 0) {
      LOG.error(
        "Incorrect transform in FontRenderContext" +
        (fromComponent ? " obtained from component" : "") +
        ": " + transform
      );
      result = new FontRenderContext(
        new AffineTransform(),
        result.getAntiAliasingHint(),
        result.getFractionalMetricsHint()
      );
    }
    Object fmHint = UISettings.getEditorFractionalMetricsHint();
    return fmHint == result.getFractionalMetricsHint()
           ? result
           : new FontRenderContext(
             result.getTransform(),
             result.getAntiAliasingHint(),
             fmHint
           );
  }

  private void checkFontRenderContext(@Nullable FontRenderContext context) {
    if (!updateFontRenderContext(context)) {
      return;
    }
    clearContentAnimationCache();
    myTextLayoutCache.resetToDocumentSize(false);
    invalidateFoldRegionLayouts();
    myCharWidthCache.clear();
    getFoldingModel().updateCachedOffsets();
    // TODO IJPL-255731: also invalidate the cached visual line widths of mySizeManager.
    //  They keep the values that the old context measured. Use invalidateRange, never reset,
    //  because reset reaches offsetToVisualLine, which throws in bulk and batch inlay mode.
  }

  /**
   * Puts a new font render context in the snapshot, and drops the metrics and the prefix layout with it.
   *
   * @return true when the context changed. The caller must then rebuild every cache that holds measured text.
   */
  private boolean updateFontRenderContext(@Nullable FontRenderContext context) {
    while (true) {
      EditorViewSnapshot snapshot = mySnapshot;
      FontRenderContext newContext = computeFontRenderContext(snapshot.fontRenderContext(), context);
      if (newContext == null) {
        return false;
      }
      EditorViewSnapshot newSnapshot = snapshot
        .withFontRenderContext(newContext)
        .withMetrics(EditorViewMetrics.UNINITIALIZED)
        .withPrefixLayout(NOT_INITIALIZED_PREFIX);
      if (SNAPSHOT_UPDATER.compareAndSet(this, snapshot, newSnapshot)) {
        return true;
      }
    }
  }

  private void assertNotInBulkMode() {
    if (myDocument instanceof DocumentImpl impl) {
      impl.assertNotInBulkUpdate();
    } else if (myDocument.isInBulkUpdate()) {
      throw new IllegalStateException("Current operation is not permitted in bulk mode");
    }
    if (getInlayModel().isInBatchMode()) {
      throw new IllegalStateException("Current operation is not permitted during batch inlay update");
    }
  }

  private static int getLineHeight(
    @NotNull Font font,
    @NotNull FontRenderContext fontRenderContext,
    int fontMetricsHeight,
    float verticalScalingFactor
  ) {
    if (Registry.is("editor.text.xcode.vertical.spacing")) {
      //Here we approximate line calculation to the variant used in Xcode 9 editor
      LineMetrics metrics = font.getLineMetrics("", fontRenderContext);
      double height = Math.ceil(metrics.getHeight()) + metrics.getLeading();
      double delta = verticalScalingFactor - 1;
      int spacing;
      if (Math.round((height * delta) / 2) <= 1) {
        spacing = delta > 0 ? 2 : 0;
      } else {
        spacing = ((int)Math.ceil((height * delta) / 2)) * 2;
      }
      return (int)Math.ceil(height) + spacing;
    }
    if (Registry.is("editor.text.vertical.spacing.correct.rounding")) {
      if (verticalScalingFactor == 1f) {
        return fontMetricsHeight;
      } else {
        Font scaledFont = font.deriveFont(font.getSize() * verticalScalingFactor);
        FontMetrics scaledMetrics = FontInfo.getFontMetrics(scaledFont, fontRenderContext);
        return FontLayoutService.getInstance().getHeight(scaledMetrics);
      }
    }
    return (int)Math.ceil(fontMetricsHeight * verticalScalingFactor);
  }

  private static float getVerticalScalingFactor(@NotNull EditorColorsScheme colorsScheme) {
    float lineSpacing = colorsScheme.getLineSpacing();
    return lineSpacing > 0 ? lineSpacing : 1;
  }

  private static void checkPrefixAttributes(@Nullable String prefixText, @Nullable TextAttributes attributes) {
    if (prefixText != null && !prefixText.isEmpty() && attributes == null) {
      throw new IllegalArgumentException("A prefix with a text needs attributes. The text is: " + prefixText);
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
