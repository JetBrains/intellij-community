// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.view;

import com.intellij.diagnostic.Dumpable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.impl.*;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapDrawingType;
import com.intellij.openapi.editor.impl.softwrap.mapping.IncrementalCacheUpdateEvent;
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapAwareDocumentParsingListenerAdapter;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Calculates width (in pixels) of editor contents.
 */
class EditorSizeManager extends InlayModel.SimpleAdapter implements PrioritizedDocumentListener, Disposable, FoldingListener, Dumpable {
  private static final Logger LOG = Logger.getInstance(EditorSizeManager.class);

  private static final int UNKNOWN_WIDTH = Integer.MAX_VALUE;
  private static final int SPECIFIC_LINES_RECALC_THRESHOLD = 2;

  private final EditorView myView;
  private final EditorImpl myEditor;
  private final DocumentEx myDocument;

  private final TIntArrayList myLineWidths = new TIntArrayList(); // cached widths of visual lines (in pixels)
                                                                  // negative value means an estimated (not precise) width
                                                                  // UNKNOWN_WIDTH(Integer.MAX_VALUE) means no value
  private boolean myWidthIsValid = true;
  private int myWidthInPixels;
  private int myWidthDefiningLineNumber;
  private int myStartInvalidLine = Integer.MAX_VALUE;
  private int myEndInvalidLine = 0;

  private int myMaxLineWithExtensionWidth;
  private int myWidestLineWithExtension;

  private int myDocumentChangeStartOffset;
  private int myDocumentChangeEndOffset;
  private int myFoldingChangeStartOffset = Integer.MAX_VALUE;
  private int myFoldingChangeEndOffset = Integer.MIN_VALUE;

  private int myVirtualPageHeight;

  private boolean myDuringDocumentUpdate;
  private boolean myDirty; // true if we cannot calculate preferred size now because soft wrap model was invalidated after editor
                           // became hidden. myLineWidths contents is irrelevant in such a state. Previously calculated preferred size
                           // is kept until soft wraps will be recalculated and size calculations will become possible

  private final List<TextRange> myDeferredRanges = new ArrayList<>();

  private final SoftWrapAwareDocumentParsingListenerAdapter mySoftWrapChangeListener = new SoftWrapAwareDocumentParsingListenerAdapter() {
    @Override
    public void onRecalculationEnd(@NotNull IncrementalCacheUpdateEvent event) {
      onSoftWrapRecalculationEnd(event);
    }
  };

  EditorSizeManager(EditorView view) {
    myView = view;
    myEditor = view.getEditor();
    myDocument = myEditor.getDocument();
    myDocument.addDocumentListener(this, this);
    myEditor.getFoldingModel().addListener(this, this);
    myEditor.getSoftWrapModel().getApplianceManager().addListener(mySoftWrapChangeListener);
    myEditor.getInlayModel().addListener(this, this);
  }

  @Override
  public void dispose() {
    myEditor.getSoftWrapModel().getApplianceManager().removeListener(mySoftWrapChangeListener);
  }

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.EDITOR_TEXT_WIDTH_CACHE;
  }

  @Override
  public void beforeDocumentChange(@NotNull DocumentEvent event) {
    myDuringDocumentUpdate = true;
    if (myDocument.isInBulkUpdate()) return;
    myDocumentChangeStartOffset = event.getOffset();
    myDocumentChangeEndOffset = event.getOffset() + event.getNewLength();
  }

  @Override
  public void documentChanged(@NotNull DocumentEvent event) {
    myDuringDocumentUpdate = false;
    if (myDocument.isInBulkUpdate()) return;
    doInvalidateRange(myDocumentChangeStartOffset, myDocumentChangeEndOffset);
    assertValidState();
  }

  @Override
  public void onFoldRegionStateChange(@NotNull FoldRegion region) {
    if (myDocument.isInBulkUpdate()) return;
    if (region.isValid()) {
      myFoldingChangeStartOffset = Math.min(myFoldingChangeStartOffset, region.getStartOffset());
      myFoldingChangeEndOffset = Math.max(myFoldingChangeEndOffset, region.getEndOffset());
    }
  }

  @Override
  public void onFoldProcessingEnd() {
    if (myDocument.isInBulkUpdate()) return;
    if (myFoldingChangeStartOffset <= myFoldingChangeEndOffset) {
      doInvalidateRange(myFoldingChangeStartOffset, myFoldingChangeEndOffset);
    }
    myFoldingChangeStartOffset = Integer.MAX_VALUE;
    myFoldingChangeEndOffset = Integer.MIN_VALUE;

    for (TextRange range : myDeferredRanges) {
      onTextLayoutPerformed(range.getStartOffset(), range.getEndOffset());
    }
    myDeferredRanges.clear();
    assertValidState();
  }

  @Override
  public void onUpdated(@NotNull Inlay inlay) {
    if (myDuringDocumentUpdate || myDocument.isInBulkUpdate() || inlay.getVerticalAlignment() != Inlay.VerticalAlignment.INLINE) return;
    doInvalidateRange(inlay.getOffset(), inlay.getOffset());
  }

  private void onSoftWrapRecalculationEnd(IncrementalCacheUpdateEvent event) {
    if (myDocument.isInBulkUpdate()) return;
    boolean invalidate = true;
    if (myEditor.getFoldingModel().isInBatchFoldingOperation()) {
      myFoldingChangeStartOffset = Math.min(myFoldingChangeStartOffset, event.getStartOffset());
      myFoldingChangeEndOffset = Math.max(myFoldingChangeEndOffset, event.getActualEndOffset());
      invalidate = false;
    }
    if (myDuringDocumentUpdate) {
      myDocumentChangeStartOffset = Math.min(myDocumentChangeStartOffset, event.getStartOffset());
      myDocumentChangeEndOffset = Math.max(myDocumentChangeEndOffset, event.getActualEndOffset());
      invalidate = false;
    }
    if (invalidate) {
      doInvalidateRange(event.getStartOffset(), event.getActualEndOffset());
    }
  }

  Dimension getPreferredSize() {
    Insets insets = myView.getInsets();
    int widthWithoutCaret = getTextPreferredWidth() + insets.left;
    int width = widthWithoutCaret;
    if (!myDocument.isInBulkUpdate() && !myEditor.isRightAligned() && myEditor.getSettings().isVirtualSpace()) {
      CaretModelImpl caretModel = myEditor.getCaretModel();
      int caretMaxX = (caretModel.isIteratingOverCarets() ? Stream.of(caretModel.getCurrentCaret()) : caretModel.getAllCarets().stream())
        .filter(caret -> caret.isUpToDate() && ((CaretImpl)caret).isInVirtualSpace())
        .mapToInt(c -> (int)myView.visualPositionToXY(c.getVisualPosition()).getX())
        .max().orElse(0);
      width = Math.max(width, caretMaxX);
    }
    if (shouldRespectAdditionalColumns(widthWithoutCaret)) {
      width += myEditor.getSettings().getAdditionalColumnsCount() * myView.getPlainSpaceWidth();
    }
    return new Dimension(width + insets.right, getPreferredHeight());
  }

  // Returns preferred width of the lines in range.
  // This method is currently used only with "idea.true.smooth.scrolling" experimental option.
  // We may unite the code with the getPreferredSize() method.
  int getPreferredWidth(int beginLine, int endLine) {
    Insets insets = myView.getInsets();
    int widthWithoutCaret = getTextPreferredWidthWithoutCaret(beginLine, endLine) + insets.left;
    int width = widthWithoutCaret;
    boolean rightAligned = myEditor.isRightAligned();
    if (!myDocument.isInBulkUpdate() && !rightAligned) {
      CaretModelImpl caretModel = myEditor.getCaretModel();
      int caretMaxX = (caretModel.isIteratingOverCarets() ? Stream.of(caretModel.getCurrentCaret()) : caretModel.getAllCarets().stream())
        .filter(Caret::isUpToDate)
        .filter(caret -> caret.getVisualPosition().line >= beginLine && caret.getVisualPosition().line < endLine)
        .mapToInt(c -> (int)myView.visualPositionToXY(c.getVisualPosition()).getX())
        .max().orElse(0);
      width = Math.max(width, caretMaxX);
    }
    if (shouldRespectAdditionalColumns(widthWithoutCaret)) {
      width += myEditor.getSettings().getAdditionalColumnsCount() * myView.getPlainSpaceWidth();
    }
    return width + insets.right;
  }

  int getPreferredHeight() {
    int lineHeight = myView.getLineHeight();
    if (myEditor.isOneLineMode()) return lineHeight;

    int linesHeight = myView.visualLineToY(myEditor.getVisibleLineCount());

    // Preferred height of less than a single line height doesn't make sense:
    // at least a single line with a blinking caret on it is to be displayed
    int size = Math.max(linesHeight, lineHeight);

    EditorSettings settings = myEditor.getSettings();
    if (settings.isAdditionalPageAtBottom()) {
      int visibleAreaHeight = myEditor.getScrollingModel().getVisibleArea().height;
      // There is a possible case that user with 'show additional page at bottom' scrolls to that virtual page; switched to another
      // editor (another tab); and then returns to the previously used editor (the one scrolled to virtual page). We want to preserve
      // correct view size then because viewport position is set to the end of the original text otherwise.
      if (visibleAreaHeight > 0 || myVirtualPageHeight <= 0) {
        myVirtualPageHeight = Math.max(visibleAreaHeight - 2 * lineHeight, lineHeight);
      }

      size += Math.max(myVirtualPageHeight, 0);
    }
    else {
      size += settings.getAdditionalLinesCount() * lineHeight;
    }

    Insets insets = myView.getInsets();
    return size + insets.top + insets.bottom;
  }

  private boolean shouldRespectAdditionalColumns(int widthWithoutCaret) {
    return !myEditor.getSoftWrapModel().isSoftWrappingEnabled()
           || myEditor.getSoftWrapModel().isRespectAdditionalColumns()
           || widthWithoutCaret > myEditor.getScrollingModel().getVisibleArea().getWidth();
  }

  private int getTextPreferredWidth() {
    if (!myWidthIsValid) {
      assert !myDocument.isInBulkUpdate();
      boolean needFullScan = true;
      if (myStartInvalidLine <= myEndInvalidLine && (myEndInvalidLine - myStartInvalidLine) < SPECIFIC_LINES_RECALC_THRESHOLD ) {
        Pair<Integer, Integer> pair = calculateTextPreferredWidth(myStartInvalidLine, myEndInvalidLine);
        needFullScan = pair.first < myWidthInPixels &&
                       myStartInvalidLine <= myWidthDefiningLineNumber && myWidthDefiningLineNumber <= myEndInvalidLine;
        if (pair.first >= myWidthInPixels) {
          myWidthInPixels = pair.first;
          myWidthDefiningLineNumber = pair.second;
        }
      }
      if (needFullScan) {
        Pair<Integer, Integer> pair = calculateTextPreferredWidth(0, Integer.MAX_VALUE);
        myWidthInPixels = pair.first;
        myWidthDefiningLineNumber = pair.second;
      }
      myWidthIsValid = true;
      myStartInvalidLine = Integer.MAX_VALUE;
      myEndInvalidLine = 0;
    }
    validateMaxLineWithExtension();
    return Math.max(myWidthInPixels, myMaxLineWithExtensionWidth);
  }

  // This method is currently used only with "idea.true.smooth.scrolling" experimental option.
  // We may optimize this computation by caching results and performing incremental updates.
  private int getTextPreferredWidthWithoutCaret(int beginLine, int endLine) {
    if (!myWidthIsValid) {
      assert !myDocument.isInBulkUpdate();
      calculateTextPreferredWidth(0, Integer.MAX_VALUE);
    }
    int maxWidth = beginLine == 0 && endLine == 0 ? (int)myView.getPrefixTextWidthInPixels() : 0;
    for (int i = beginLine; i < endLine && i < myLineWidths.size(); i++) {
      maxWidth = Math.max(maxWidth, Math.abs(myLineWidths.get(i)));
    }
    validateMaxLineWithExtension();
    return Math.max(maxWidth, myMaxLineWithExtensionWidth);
  }

  private void validateMaxLineWithExtension() {
    if (myMaxLineWithExtensionWidth > 0) {
      boolean hasNoExtensions = myEditor.processLineExtensions(myWidestLineWithExtension, (info) -> false);
      if (hasNoExtensions) {
        myMaxLineWithExtensionWidth = 0;
      }
    }
  }

  // first number is the width, second number is the largest visual line number
  private Pair<Integer, Integer> calculateTextPreferredWidth(int startVisualLine, int endVisualLine) {
    if (checkDirty()) return Pair.pair(1, 0);
    assertValidState();
    VisualLinesIterator iterator = new VisualLinesIterator(myEditor, startVisualLine);
    int maxWidth = 0;
    int largestLineNumber = 0;
    if (startVisualLine == 0 && iterator.atEnd()) {
      maxWidth += myView.getPrefixTextWidthInPixels();
    }
    while (!iterator.atEnd()) {
      int width = getVisualLineWidth(iterator, true);
      if (width > maxWidth) {
        maxWidth = width;
        largestLineNumber = iterator.getVisualLine();
      }
      if (iterator.getVisualLine() >= endVisualLine) break;
      iterator.advance();
    }
    return Pair.create(maxWidth, largestLineNumber);
  }

  int getVisualLineWidth(VisualLinesIterator visualLinesIterator, boolean allowQuickCalculation) {
    assert !visualLinesIterator.atEnd();
    int visualLine = visualLinesIterator.getVisualLine();
    boolean useCache = shouldUseLineWidthCache();
    int cached = useCache ? myLineWidths.get(visualLine) : UNKNOWN_WIDTH;
    if (cached != UNKNOWN_WIDTH && (cached >= 0 || allowQuickCalculation)) return Math.abs(cached);
    Ref<Boolean> evaluatedQuick = Ref.create(Boolean.FALSE);
    int width = calculateLineWidth(visualLinesIterator, allowQuickCalculation ? () -> evaluatedQuick.set(Boolean.TRUE) : null);
    if (useCache) myLineWidths.set(visualLine, evaluatedQuick.get() ? -width : width);
    return width;
  }

  private int calculateLineWidth(@NotNull VisualLinesIterator iterator, @Nullable Runnable quickEvaluationListener) {
    int visualLine = iterator.getVisualLine();
    FoldRegion[] topLevelRegions = myEditor.getFoldingModel().fetchTopLevel();
    if (quickEvaluationListener != null &&
        (topLevelRegions == null || topLevelRegions.length == 0) && myEditor.getSoftWrapModel().getRegisteredSoftWraps().isEmpty() &&
        !myView.getTextLayoutCache().hasCachedLayoutFor(visualLine) && !myEditor.getInlayModel().hasInlineElements()) {
      // fast path - speeds up editor opening
      quickEvaluationListener.run();
      return (int)(myView.getLogicalPositionCache().offsetToLogicalColumn(visualLine,
                                                                          myDocument.getLineEndOffset(visualLine) -
                                                                          myDocument.getLineStartOffset(visualLine)) *
                   myView.getMaxCharWidth());
    }
    float x = 0;
    int maxOffset = iterator.getVisualLineStartOffset();
    int leftInset = myView.getInsets().left;
    for (VisualLineFragmentsIterator.Fragment fragment : VisualLineFragmentsIterator.create(myView, iterator,
                                                                                            quickEvaluationListener, false)) {
      x = fragment.getEndX() - leftInset;
      maxOffset = Math.max(maxOffset, fragment.getMaxOffset());
    }
    if (myEditor.getSoftWrapModel().getSoftWrap(maxOffset) != null) {
      x += myEditor.getSoftWrapModel().getMinDrawingWidthInPixels(SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED);
    }
    return (int)x;
  }

  void reset() {
    assert !myDocument.isInBulkUpdate();
    doInvalidateRange(0, myDocument.getTextLength());
  }

  void invalidateRange(int startOffset, int endOffset) {
    if (myDocument.isInBulkUpdate()) return;
    if (myDuringDocumentUpdate) {
      myDocumentChangeStartOffset = Math.min(myDocumentChangeStartOffset, startOffset);
      myDocumentChangeEndOffset = Math.max(myDocumentChangeEndOffset, endOffset);
    }
    else if (myFoldingChangeEndOffset != Integer.MIN_VALUE) {
      // during batch folding processing we delay invalidation requests, as we cannot perform coordinate conversions immediately
      myFoldingChangeStartOffset = Math.min(myFoldingChangeStartOffset, startOffset);
      myFoldingChangeEndOffset = Math.max(myFoldingChangeEndOffset, endOffset);
    }
    else {
      doInvalidateRange(startOffset, endOffset);
    }
  }

  private boolean shouldUseLineWidthCache() {
    if (myView.getEditor().isPurePaintingMode()) return false;

    FoldingModelImpl model = myView.getEditor().getFoldingModel();
    if (model.isFoldingEnabled()) return true;

    model.setFoldingEnabled(true);
    FoldRegion[] regions;
    try {
      regions = model.fetchTopLevel();
    }
    finally {
      model.setFoldingEnabled(false);
    }
    return regions == null || regions.length == 0;
  }

  private void doInvalidateRange(int startOffset, int endOffset) {
    if (checkDirty()) return;
    int startVisualLine = myView.offsetToVisualLine(startOffset, false);
    int endVisualLine = myView.offsetToVisualLine(endOffset, true);
    int lineDiff = myEditor.getVisibleLineCount() - myLineWidths.size();
    invalidateWidth(lineDiff == 0 && startVisualLine == endVisualLine, startVisualLine);
    if (lineDiff > 0) {
      int[] newEntries = new int[lineDiff];
      myLineWidths.insert(startVisualLine, newEntries);
    }
    else if (lineDiff < 0) {
      myLineWidths.remove(startVisualLine, -lineDiff);
    }
    for (int i = startVisualLine; i <= endVisualLine && i < myLineWidths.size(); i++) {
      myLineWidths.set(i, UNKNOWN_WIDTH);
    }
  }

  int getMaxLineWithExtensionWidth() {
    return myMaxLineWithExtensionWidth;
  }

  void setMaxLineWithExtensionWidth(int lineNumber, int width) {
    myWidestLineWithExtension = lineNumber;
    myMaxLineWithExtensionWidth = width;
  }

  void textLayoutPerformed(int startOffset, int endOffset) {
    assert 0 <= startOffset && startOffset < endOffset && endOffset <= myDocument.getTextLength()
      : "startOffset=" + startOffset + ", endOffset=" + endOffset;
    if (myDocument.isInBulkUpdate()) return;
    if (myEditor.getFoldingModel().isInBatchFoldingOperation()) {
      myDeferredRanges.add(new TextRange(startOffset, endOffset));
    }
    else {
      onTextLayoutPerformed(startOffset, endOffset);
    }
  }

  private void onTextLayoutPerformed(int startOffset, int endOffset) {
    if (checkDirty()) return;
    boolean purePaintingMode = myEditor.isPurePaintingMode();
    boolean foldingEnabled = myEditor.getFoldingModel().isFoldingEnabled();
    myEditor.setPurePaintingMode(false);
    myEditor.getFoldingModel().setFoldingEnabled(true);
    try {
      int startVisualLine = myView.offsetToVisualLine(startOffset, false);
      int endVisualLine = myView.offsetToVisualLine(endOffset, true);
      boolean sizeInvalidated = false;
      for (int i = startVisualLine; i <= endVisualLine; i++) {
        if (myLineWidths.get(i) < 0) {
          myLineWidths.set(i, UNKNOWN_WIDTH);
          sizeInvalidated = true;
        }
      }
      if (sizeInvalidated) {
        invalidateWidth(startVisualLine == endVisualLine, startVisualLine);
        myEditor.getContentComponent().revalidate();
      }
    }
    finally {
      myEditor.setPurePaintingMode(purePaintingMode);
      myEditor.getFoldingModel().setFoldingEnabled(foldingEnabled);
    }
  }

  private void invalidateWidth(boolean invalidateOneLine, int invalidVisualLine) {
    myWidthIsValid = false;
    if (invalidateOneLine) {
      myStartInvalidLine = Math.min(myStartInvalidLine, invalidVisualLine);
      myEndInvalidLine = Math.max(myEndInvalidLine, invalidVisualLine);
    }
    else {
      myStartInvalidLine = 0;
      myEndInvalidLine = Integer.MAX_VALUE;
    }
  }

  private boolean checkDirty() {
    if (myEditor.getSoftWrapModel().isDirty()) {
      myDirty = true;
      return true;
    }
    if (myDirty) {
      int visibleLineCount = myEditor.getVisibleLineCount();
      int lineDiff = visibleLineCount - myLineWidths.size();
      if (lineDiff > 0) myLineWidths.add(new int[lineDiff]);
      else if (lineDiff < 0) myLineWidths.remove(visibleLineCount, -lineDiff);
      for (int i = 0; i < visibleLineCount; i++) {
        myLineWidths.set(i, UNKNOWN_WIDTH);
      }
      myDirty = false;
    }
    return false;
  }

  @NotNull
  @Override
  public String dumpState() {
    return "[cached width: " + myWidthInPixels +
           ", longest visual line: " + myWidthDefiningLineNumber +
           ", cached width is valid: " + myWidthIsValid +
           ", invalid visual lines: [" + myStartInvalidLine + ", " + myEndInvalidLine + "]" +
           ", max line with extension width: " + myMaxLineWithExtensionWidth +
           ", line widths: " + myLineWidths + "]";
  }

  private void assertValidState() {
    if (myDocument.isInBulkUpdate() || myDirty) return;
    if (myLineWidths.size() != myEditor.getVisibleLineCount()) {
      LOG.error("Inconsistent state", new Attachment("editor.txt", myEditor.dumpState()));
      reset();
    }
    assert myLineWidths.size() == myEditor.getVisibleLineCount();
  }

  private void assertCorrectCachedWidths() {
    if (myDocument.isInBulkUpdate() || myDirty) return;
    for (int visualLine = 0; visualLine < myLineWidths.size(); visualLine++) {
      int cachedWidth = myLineWidths.get(visualLine);
      if (cachedWidth < 0 || cachedWidth == UNKNOWN_WIDTH) continue;
      Ref<Boolean> quickEvaluation = new Ref<>();
      int actualWidth = calculateLineWidth(new VisualLinesIterator(myEditor, visualLine), () -> quickEvaluation.set(Boolean.TRUE));
      assert !quickEvaluation.isNull() || actualWidth == cachedWidth :
        "Wrong cached width for visual line " + visualLine + ", cached: " + cachedWidth + ", actual: " + actualWidth;
    }
  }

  @TestOnly
  void validateState() {
    assertValidState();
    assertCorrectCachedWidths();
  }
}
