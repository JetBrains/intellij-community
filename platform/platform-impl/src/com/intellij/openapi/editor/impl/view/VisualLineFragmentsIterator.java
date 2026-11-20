// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.CustomFoldRegion;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.DocumentText;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.InlayModelEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.SoftWrapModelImpl;
import com.intellij.openapi.editor.impl.softwrap.CustomWrapToSoftWrapAdapter;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapEx;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Graphics2D;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

/**
 * Iterator over visual line's fragments. Fragment's text has the same font and directionality. Collapsed fold regions are also represented
 * as fragments.
 */
final class VisualLineFragmentsIterator implements Iterator<VisualLineFragmentsIterator.Fragment> {
  static @NotNull Iterable<Fragment> create(
    @NotNull EditorView view,
    @NotNull DocumentText document,
    int offset,
    boolean beforeSoftWrap,
    boolean align
  ) {
    return () -> new VisualLineFragmentsIterator(view, document, offset, beforeSoftWrap, align);
  }

  /**
   * If {@code quickEvaluationListener} is provided, quick approximate iteration mode becomes enabled, listener will be invoked
   * if approximation will in fact be used during width calculation.
   */
  static @NotNull Iterable<Fragment> create(
    @NotNull EditorView view,
    @NotNull DocumentText document,
    @NotNull VisualLinesIterator visualLinesIterator,
    @Nullable Runnable quickEvaluationListener,
    boolean align
  ) {
    return () -> new VisualLineFragmentsIterator(view, document, visualLinesIterator, quickEvaluationListener, align);
  }

  private final EditorView myView;
  private final DocumentText myDocument;
  private final InlayModelEx myInlayModel;
  private final ScaleContext myScaleContext;
  private final TextLayoutCache myTextLayoutCache;
  private final FoldRegion[] myRegions;
  private final @Nullable SoftWrapEx myNextWrap;
  private final int myVisualLineStartOffset;
  /**
   * The x shift of a right-aligned visual line, or 0.
   * The x coordinates of a {@link Fragment} include it. The x coordinates of a {@link LineVisualFragment} do not.
   */
  private final float myXOffset;
  private final @Nullable Runnable myQuickEvaluationListener;
  private final TextRunFragment myTextRunFragment;
  private final InlayFragment myInlayFragment;
  private final FoldRegionFragment myFoldRegionFragment;

  private int mySegmentStartOffset;
  private int mySegmentEndOffset;
  private float myCurrentX;
  private float myPrevX;
  private int myCurrentVisualColumn;
  private int myCurrentStartLogicalLine;
  private int myCurrentStartLogicalLineStart;
  private int myCurrentEndLogicalLine;
  private @Nullable Iterator<LineVisualFragment> myFragmentIterator;
  private List<Inlay<?>> myInlays;
  private int myCurrentInlayIndex;
  private int myCurrentFoldRegionIndex;
  private Step myNextStep;

  private VisualLineFragmentsIterator(
    @NotNull EditorView view,
    @NotNull DocumentText document,
    int offset,
    boolean beforeSoftWrap,
    boolean align
  ) {
    FoldingModelEx foldingModel = view.getFoldingModel();
    SoftWrapModelImpl softWrapModel = view.getSoftWrapModel();
    int startOrPrevWrapIndex = calculateStartOrPrevWrapIndex(softWrapModel, offset, beforeSoftWrap);
    int visualLineStartOffset = calculateVisualLineStartOffset(
      document,
      foldingModel,
      softWrapModel,
      offset,
      startOrPrevWrapIndex
    );
    int startFoldingIndex = foldingModel.getLastCollapsedRegionBefore(visualLineStartOffset) + 1;
    float xOffset = 0;
    if (align && view.getEditor().isRightAligned()) {
      int visualLine = view.offsetToVisualPosition(offset, false, beforeSoftWrap).line;
      xOffset = calculateXOffset(view, visualLine);
    }
    int startLogicalLine = document.lineNumber(visualLineStartOffset);
    this(
      view,
      document,
      visualLineStartOffset,
      startLogicalLine,
      startOrPrevWrapIndex,
      startFoldingIndex,
      null,
      xOffset
    );
  }

  private VisualLineFragmentsIterator(
    @NotNull EditorView view,
    @NotNull DocumentText document,
    @NotNull VisualLinesIterator visualLinesIterator,
    @Nullable Runnable quickEvaluationListener,
    boolean align
  ) {
    assert !visualLinesIterator.atEnd();
    float xOffset = 0;
    if (align && view.getEditor().isRightAligned()) {
      xOffset = calculateXOffset(view, visualLinesIterator.getVisualLine());
    }
    this(
      view,
      document,
      visualLinesIterator.getVisualLineStartOffset(),
      visualLinesIterator.getStartLogicalLine(),
      visualLinesIterator.getStartOrPrevWrapIndex(),
      visualLinesIterator.getStartFoldingIndex(),
      quickEvaluationListener,
      xOffset
    );
  }

  private VisualLineFragmentsIterator(
    @NotNull EditorView view,
    @NotNull DocumentText document,
    int visualLineStartOffset,
    int startLogicalLine,
    int startOrPrevWrapIndex,
    int startFoldingIndex,
    @Nullable Runnable quickEvaluationListener,
    float xOffset
  ) {
    myView = view;
    myDocument = document;
    myInlayModel = view.getInlayModel();
    myTextLayoutCache = view.getTextLayoutCache();
    myQuickEvaluationListener = quickEvaluationListener;
    myScaleContext = ScaleContext.create(view.getEditor().getContentComponent());
    myXOffset = xOffset;
    myTextRunFragment = new TextRunFragment();
    myInlayFragment = new InlayFragment();
    myFoldRegionFragment = new FoldRegionFragment();
    myRegions = fetchTopLevelFoldings(view);
    myVisualLineStartOffset = visualLineStartOffset;
    var softWraps = view.getSoftWrapModel().getRegisteredSoftWrapsEx();
    myNextWrap = getSoftWrap(softWraps, startOrPrevWrapIndex + 1);
    init(visualLineStartOffset, startLogicalLine, startFoldingIndex, getSoftWrap(softWraps, startOrPrevWrapIndex));
  }

  @Override
  public boolean hasNext() {
    return myNextStep != Step.END;
  }

  @Override
  public Fragment next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    float prevX = myCurrentX;
    int startLogicalLine = myCurrentEndLogicalLine;
    int startLogicalLineStart = myDocument.lineStartOffset(startLogicalLine);
    Fragment fragment;
    if (myNextStep == Step.FOLD_REGION) {
      fragment = moveToFoldRegion();
    } else if (myNextStep == Step.INLAY) {
      fragment = moveToInlay();
    } else {
      fragment = moveToTextRun();
    }
    myPrevX = prevX;
    myCurrentStartLogicalLine = startLogicalLine;
    myCurrentStartLogicalLineStart = startLogicalLineStart;
    return fragment;
  }

  private @NotNull Fragment moveToFoldRegion() {
    FoldRegion foldRegion = myRegions[myCurrentFoldRegionIndex];
    assert foldRegion.isValid();
    int foldRegionColumns = getFoldRegionWidthInColumns(foldRegion);
    float foldRegionWidth = getFoldRegionWidthInPixels(foldRegion);
    int segmentStartOffset = foldRegion.getEndOffset();
    enterSegment(
      segmentStartOffset,
      myDocument.lineNumber(segmentStartOffset),
      myCurrentFoldRegionIndex + 1,
      myCurrentVisualColumn + foldRegionColumns,
      myCurrentX + foldRegionWidth,
      null,
      foldRegion instanceof CustomFoldRegion
    );
    myFoldRegionFragment.set(foldRegion, foldRegionColumns);
    return myFoldRegionFragment;
  }

  private @NotNull Fragment moveToInlay() {
    Inlay<?> inlay = myInlays.get(myCurrentInlayIndex);
    float x = (float)(myCurrentX + alignToInt(inlay.getWidthInPixels()));
    int visualColumn = myCurrentVisualColumn + 1;
    int inlayIndex = myCurrentInlayIndex + 1;
    Iterator<LineVisualFragment> fragmentIterator = null;
    Step nextStep = Step.INLAY;
    if (inlayIndex >= myInlays.size() ||
        myInlays.get(inlayIndex).getOffset() > inlay.getOffset()) {
      fragmentIterator = createFragmentIterator(
        mySegmentStartOffset,
        mySegmentEndOffset,
        myCurrentEndLogicalLine,
        myCurrentFoldRegionIndex,
        myInlays,
        inlayIndex,
        visualColumn,
        x
      );
      nextStep = fragmentIterator.hasNext() ? Step.TEXT_RUN : Step.END;
    }
    myCurrentX = x;
    myCurrentVisualColumn = visualColumn;
    myCurrentInlayIndex = inlayIndex;
    myFragmentIterator = fragmentIterator;
    myNextStep = nextStep;
    myInlayFragment.set(inlay);
    return myInlayFragment;
  }

  private @NotNull Fragment moveToTextRun() {
    Iterator<LineVisualFragment> fragmentIterator = myFragmentIterator;
    assert fragmentIterator != null;
    LineVisualFragment delegate = fragmentIterator.next();
    float x = delegate.getEndX();
    int visualColumn = delegate.getEndVisualColumn();
    Step nextStep = Step.TEXT_RUN;
    if (!fragmentIterator.hasNext()) {
      if (myCurrentInlayIndex < myInlays.size()) {
        fragmentIterator = null;
        nextStep = Step.INLAY;
      } else if (mySegmentEndOffset == getFoldRegionStartOffset(myCurrentFoldRegionIndex)) {
        nextStep = Step.FOLD_REGION;
      } else {
        nextStep = Step.END;
      }
    }
    myCurrentX = x;
    myCurrentVisualColumn = visualColumn;
    myFragmentIterator = fragmentIterator;
    myNextStep = nextStep;
    myTextRunFragment.set(delegate);
    return myTextRunFragment;
  }

  private void init(
    int visualLineStartOffset,
    int startLogicalLine,
    int startFoldingIndex,
    @Nullable SoftWrapEx startOrPrevWrap
  ) {
    float startX = calculateStartX(visualLineStartOffset, startOrPrevWrap);
    int startVisualColumn = calculateStartVisualColumn(visualLineStartOffset, startOrPrevWrap);
    enterSegment(
      visualLineStartOffset,
      startLogicalLine,
      startFoldingIndex,
      startVisualColumn,
      startX,
      startOrPrevWrap,
      false
    );
  }

  private float calculateStartX(int visualLineStartOffset, @Nullable SoftWrapEx startOrPrevWrap) {
    int leftInset = myView.getInsets().left;
    if (visualLineStartOffset == 0) {
      return leftInset + myView.getPrefixTextWidthInPixels();
    }
    if (startOrPrevWrap != null && visualLineStartOffset == startOrPrevWrap.getStart()) {
      return (float)(leftInset + alignToInt(startOrPrevWrap.getIndentInPixels()));
    }
    return leftInset;
  }

  private void enterSegment(
    int segmentStartOffset,
    int logicalLine,
    int foldRegionIndex,
    int visualColumn,
    float x,
    @Nullable SoftWrapEx startWrap,
    boolean afterCustomFoldRegion
  ) {
    int foldRegionStartOffset = getFoldRegionStartOffset(foldRegionIndex);
    if (foldRegionStartOffset < segmentStartOffset) {
      String message = assertMessage(segmentStartOffset, foldRegionStartOffset, foldRegionIndex, null, 0);
      myView.getEditor().throwEditorInvariantBroken(message);
    }
    int segmentEndOffset = foldRegionStartOffset;
    List<Inlay<?>> inlays = List.of();
    Iterator<LineVisualFragment> fragmentIterator = null;
    Step nextStep = Step.FOLD_REGION;
    if (segmentStartOffset < foldRegionStartOffset) {
      segmentEndOffset = calculateSegmentEndOffset(logicalLine, foldRegionStartOffset);
      inlays = calculateInlays(segmentStartOffset, segmentEndOffset, foldRegionStartOffset, startWrap);
      nextStep = Step.INLAY;
      if (inlays.isEmpty() || inlays.getFirst().getOffset() > segmentStartOffset) {
        fragmentIterator = createFragmentIterator(
          segmentStartOffset,
          segmentEndOffset,
          logicalLine,
          foldRegionIndex,
          inlays,
          0,
          visualColumn,
          x
        );
        nextStep = fragmentIterator.hasNext() ? Step.TEXT_RUN : Step.END;
      }
    }
    if (afterCustomFoldRegion) {
      nextStep = Step.END;
    }
    mySegmentStartOffset = segmentStartOffset;
    mySegmentEndOffset = segmentEndOffset;
    myCurrentEndLogicalLine = logicalLine;
    myCurrentFoldRegionIndex = foldRegionIndex;
    myCurrentVisualColumn = visualColumn;
    myCurrentX = x;
    myInlays = inlays;
    myCurrentInlayIndex = 0;
    myFragmentIterator = fragmentIterator;
    myNextStep = nextStep;
  }

  private int calculateSegmentEndOffset(int logicalLine, int foldRegionStartOffset) {
    int lineEndOffset = myDocument.lineEndOffset(logicalLine);
    return Math.min(getNextWrapOffset(), Math.min(foldRegionStartOffset, lineEndOffset));
  }

  private @NotNull List<Inlay<?>> calculateInlays(
    int segmentStartOffset,
    int segmentEndOffset,
    int foldRegionStartOffset,
    @Nullable SoftWrapEx startWrap
  ) {
    boolean normalLineEnd = segmentEndOffset < foldRegionStartOffset &&
                            (segmentEndOffset < getNextWrapOffset() || myNextWrap instanceof CustomWrapToSoftWrapAdapter);
    int effectiveEndOffset = normalLineEnd ? segmentEndOffset : segmentEndOffset - 1;
    int endOffsetForInlays = Math.max(segmentStartOffset, effectiveEndOffset);
    // including inlays at line end
    List<Inlay<?>> inlays = myInlayModel.getInlineElementsInRange(segmentStartOffset, endOffsetForInlays);
    if (myNextWrap instanceof CustomWrapToSoftWrapAdapter customWrap &&
        customWrap.getStart() == segmentEndOffset) {
      for (int i = inlays.size() - 1; i >= 0; i--) {
        Inlay<?> inlay = inlays.get(i);
        if (inlay.getOffset() < segmentEndOffset) {
          break;
        }
        if (!inlay.isRelatedToPrecedingText()) {
          inlays.remove(i);
        }
      }
    }
    if (startWrap instanceof CustomWrapToSoftWrapAdapter customWrap &&
        customWrap.getStart() == segmentStartOffset) {
      for (var it = inlays.iterator(); it.hasNext();) {
        Inlay<?> inlay = it.next();
        if (inlay.getOffset() > segmentStartOffset) {
          break;
        }
        if (inlay.isRelatedToPrecedingText()) {
          it.remove();
        }
      }
    }
    return inlays;
  }

  private @NotNull Iterator<LineVisualFragment> createFragmentIterator(
    int segmentStartOffset,
    int segmentEndOffset,
    int logicalLine,
    int foldRegionIndex,
    @NotNull List<Inlay<?>> inlays,
    int inlayIndex,
    int visualColumn,
    float x
  ) {
    if (logicalLine >= myDocument.lineCount()) {
      // handle empty document case
      return Collections.emptyIterator();
    }
    int startOffset = inlayIndex > 0 ? inlays.get(inlayIndex - 1).getOffset() : segmentStartOffset;
    int endOffset = inlayIndex < inlays.size() ? inlays.get(inlayIndex).getOffset() : segmentEndOffset;
    if (startOffset > endOffset) {
      String message = assertMessage(segmentStartOffset, segmentEndOffset, foldRegionIndex, inlays, inlayIndex);
      myView.getEditor().throwEditorInvariantBroken(message);
    }
    int lineStartOffset = myDocument.lineStartOffset(logicalLine);
    LineLayout lineLayout = myTextLayoutCache.getLineLayout(myDocument, logicalLine);
    return lineLayout.getFragmentsInVisualOrder(
      myView,
      myDocument,
      logicalLine,
      startOffset - lineStartOffset,
      endOffset - lineStartOffset,
      visualColumn,
      x,
      myQuickEvaluationListener
    );
  }

  private int getFoldRegionStartOffset(int foldRegionIndex) {
    if (foldRegionIndex >= myRegions.length) {
      return Integer.MAX_VALUE;
    }
    int nextFoldingOffset = myRegions[foldRegionIndex].getStartOffset();
    if (nextFoldingOffset < getNextWrapOffset()) {
      return nextFoldingOffset;
    }
    return Integer.MAX_VALUE;
  }

  private float getFoldRegionWidthInPixels(@NotNull FoldRegion foldRegion) {
    if (foldRegion instanceof CustomFoldRegion region) {
      return region.getWidthInPixels();
    }
    return myView.getFoldRegionLayout(foldRegion).getWidth();
  }

  private int getFoldRegionWidthInColumns(@NotNull FoldRegion foldRegion) {
    int maxVisualColumn = 0;
    for (var fragment : myView.getFoldRegionLayout(foldRegion).getFragmentsInVisualOrder(0)) {
      maxVisualColumn = fragment.getEndVisualColumn();
    }
    return maxVisualColumn;
  }

  private @NotNull LogicalPosition offsetToLogicalPosition(int offset) {
    return myView.offsetToLogicalPosition(offset);
  }

  private double alignToInt(int width) {
    return PaintUtil.alignToInt(width, myScaleContext);
  }

  private int getNextWrapOffset() {
    return myNextWrap == null ? Integer.MAX_VALUE : myNextWrap.getStart();
  }

  private static int calculateStartOrPrevWrapIndex(
    @NotNull SoftWrapModelImpl softWrapModel,
    int offset,
    boolean beforeSoftWrap
  ) {
    int wrapIndex = softWrapModel.getSoftWrapIndex(offset);
    if (wrapIndex < 0) {
      return -wrapIndex - 2;
    }
    if (beforeSoftWrap) {
      return wrapIndex - 1;
    }
    return wrapIndex;
  }

  private static int calculateVisualLineStartOffset(
    @NotNull DocumentText document,
    @NotNull FoldingModelEx foldingModel,
    @NotNull SoftWrapModelImpl softWrapModel,
    int offset,
    int startOrPrevWrapIndex
  ) {
    int notFoldedLineStartOffset = EditorUtil.getNotFoldedLineStartOffset(document, foldingModel, offset, false);
    SoftWrapEx startOrPrevWrap = getSoftWrap(softWrapModel.getRegisteredSoftWrapsEx(), startOrPrevWrapIndex);
    if (startOrPrevWrap != null && startOrPrevWrap.getStart() > notFoldedLineStartOffset) {
      return startOrPrevWrap.getStart();
    }
    return notFoldedLineStartOffset;
  }

  private static int calculateStartVisualColumn(int visualLineStartOffset, @Nullable SoftWrapEx startOrPrevWrap) {
    if (visualLineStartOffset != 0 &&
        startOrPrevWrap != null &&
        visualLineStartOffset == startOrPrevWrap.getStart()) {
      return startOrPrevWrap.getIndentInColumns();
    }
    return 0;
  }

  private static float calculateXOffset(@NotNull EditorView view, int visualLine) {
    return view.getRightAlignmentLineStartX(visualLine) - view.getInsets().left;
  }

  private static @Nullable SoftWrapEx getSoftWrap(@NotNull List<? extends SoftWrapEx> softWraps, int index) {
    if (0 <= index && index < softWraps.size()) {
      return softWraps.get(index);
    }
    return null;
  }

  private static @NotNull FoldRegion @NotNull [] fetchTopLevelFoldings(@NotNull EditorView view) {
    FoldingModelEx foldingModel = view.getFoldingModel();
    return ObjectUtils.notNull(foldingModel.fetchTopLevel(), FoldRegion.EMPTY_ARRAY);
  }

  /**
   * The kind of the fragment that {@link #next()} returns, or {@link #END} when the visual line has no more fragments.
   */
  private enum Step {
    TEXT_RUN,
    INLAY,
    FOLD_REGION,
    END
  }

  /**
   * A fragment of a visual line: a text run, an inline inlay or a collapsed fold region.
   * The iterator returns one object for all fragments of a kind.
   * So the values of a fragment change at the next call of {@link #next()}.
   */
  abstract sealed class Fragment permits TextRunFragment, InlayFragment, FoldRegionFragment {
    int getVisualLineStartOffset() {
      return myVisualLineStartOffset;
    }

    int getMinLogicalColumn() {
      return offsetToLogicalPosition(getMinOffset()).column;
    }

    int getMaxLogicalColumn() {
      return offsetToLogicalPosition(getMaxOffset()).column;
    }

    int getStartLogicalColumn() {
      return offsetToLogicalPosition(getStartOffset()).column;
    }

    int getEndLogicalColumn() {
      return offsetToLogicalPosition(getEndOffset()).column;
    }

    abstract int getStartVisualColumn();

    int getEndVisualColumn() {
      return myCurrentVisualColumn;
    }

    int getStartLogicalLine() {
      return myCurrentStartLogicalLine;
    }

    int getEndLogicalLine() {
      return myCurrentEndLogicalLine;
    }

    float getStartX() {
      return myPrevX + myXOffset;
    }

    float getEndX() {
      return myCurrentX + myXOffset;
    }

    /**
     * The column must be between the min and the max logical column of this fragment.
     */
    abstract int logicalToVisualColumn(int column);

    /**
     * The column must be between the start and the end visual column of this fragment.
     */
    abstract int visualToLogicalColumn(int column);

    abstract VisualColumn xToVisualColumn(float x);

    abstract float visualColumnToX(int column);

    abstract int getVisualLength();

    /**
     * Returns a visual offset, relative to the start of this fragment.
     */
    abstract int visualColumnToOffset(int relativeVisualColumn);

    /**
     * Returns an absolute offset.
     */
    abstract int getStartOffset();

    /**
     * Returns an absolute offset.
     */
    abstract int getEndOffset();

    /**
     * Returns an absolute offset.
     */
    abstract int getMinOffset();

    /**
     * Returns an absolute offset.
     */
    abstract int getMaxOffset();

    /**
     * The offset is absolute.
     */
    abstract float offsetToX(int offset);

    /**
     * The offsets are absolute. Only a text run supports this method.
     */
    float offsetToX(float startX, int startOffset, int offset) {
      throw new UnsupportedOperationException();
    }

    boolean isRtl() {
      return false;
    }

    FoldRegion getCurrentFoldRegion() {
      return null;
    }

    Inlay<?> getCurrentInlay() {
      return null;
    }

    /**
     * The offsets are visual, relative to the start of this fragment.
     */
    abstract @NotNull Consumer<Graphics2D> draw(float x, float y, int startRelativeOffset, int endRelativeOffset);
  }

  /**
   * A part of a logical line with one font and one direction.
   */
  final class TextRunFragment extends Fragment {
    private LineVisualFragment delegate;

    void set(LineVisualFragment lineVisualFragment) {
      delegate = lineVisualFragment;
    }

    @Override
    int getMinLogicalColumn() {
      return delegate.getMinLogicalColumn();
    }

    @Override
    int getMaxLogicalColumn() {
      return delegate.getMaxLogicalColumn();
    }

    @Override
    int getStartLogicalColumn() {
      return delegate.getStartLogicalColumn();
    }

    @Override
    int getEndLogicalColumn() {
      return delegate.getEndLogicalColumn();
    }

    @Override
    int getStartVisualColumn() {
      return delegate.getStartVisualColumn();
    }

    @Override
    float getStartX() {
      return delegate.getStartX() + myXOffset;
    }

    @Override
    int logicalToVisualColumn(int column) {
      return delegate.logicalToVisualColumn(column);
    }

    @Override
    int visualToLogicalColumn(int column) {
      return delegate.visualToLogicalColumn(column);
    }

    @Override
    VisualColumn xToVisualColumn(float x) {
      return delegate.xToVisualColumn(x - myXOffset);
    }

    @Override
    float visualColumnToX(int column) {
      return delegate.visualColumnToX(column) + myXOffset;
    }

    @Override
    int getVisualLength() {
      return delegate.getLength();
    }

    @Override
    int visualColumnToOffset(int relativeVisualColumn) {
      return delegate.visualColumnToOffset(relativeVisualColumn);
    }

    @Override
    int getStartOffset() {
      return delegate.getStartOffset() + myCurrentStartLogicalLineStart;
    }

    @Override
    int getEndOffset() {
      return delegate.getEndOffset() + myCurrentStartLogicalLineStart;
    }

    @Override
    int getMinOffset() {
      return delegate.getMinOffset() + myCurrentStartLogicalLineStart;
    }

    @Override
    int getMaxOffset() {
      return delegate.getMaxOffset() + myCurrentStartLogicalLineStart;
    }

    @Override
    float offsetToX(int offset) {
      return delegate.offsetToX(offset - myCurrentStartLogicalLineStart) + myXOffset;
    }

    @Override
    float offsetToX(float startX, int startOffset, int offset) {
      int lineStartOffset = myCurrentStartLogicalLineStart;
      float x = delegate.offsetToX(startX - myXOffset, startOffset - lineStartOffset, offset - lineStartOffset);
      return x + myXOffset;
    }

    @Override
    boolean isRtl() {
      return delegate.isRtl();
    }

    @Override
    @NotNull
    Consumer<Graphics2D> draw(float x, float y, int startRelativeOffset, int endRelativeOffset) {
      return delegate.draw(x, y, startRelativeOffset, endRelativeOffset);
    }
  }

  /**
   * An inline inlay. It takes one visual column.
   */
  final class InlayFragment extends Fragment {
    private Inlay<?> inlay;

    void set(Inlay<?> currentInlay) {
      inlay = currentInlay;
    }

    @Override
    int getStartVisualColumn() {
      return myCurrentVisualColumn - 1;
    }

    @Override
    int logicalToVisualColumn(int column) {
      return getEndVisualColumn();
    }

    @Override
    int visualToLogicalColumn(int column) {
      return getEndLogicalColumn();
    }

    @Override
    VisualColumn xToVisualColumn(float x) {
      boolean closerToStart = x < (getStartX() + getEndX()) / 2;
      return new VisualColumn(myCurrentVisualColumn - (closerToStart ? 1 : 0), !closerToStart);
    }

    @Override
    float visualColumnToX(int column) {
      if (column == myCurrentVisualColumn) {
        return getEndX();
      }
      return getStartX();
    }

    @Override
    int getVisualLength() {
      return 0;
    }

    @Override
    int visualColumnToOffset(int relativeVisualColumn) {
      return 0;
    }

    @Override
    int getStartOffset() {
      return inlay.getOffset();
    }

    @Override
    int getEndOffset() {
      return inlay.getOffset();
    }

    @Override
    int getMinOffset() {
      return inlay.getOffset();
    }

    @Override
    int getMaxOffset() {
      return inlay.getOffset();
    }

    @Override
    float offsetToX(int offset) {
      return getEndX();
    }

    @Override
    Inlay<?> getCurrentInlay() {
      return inlay;
    }

    @Override
    @NotNull
    Consumer<Graphics2D> draw(float x, float y, int startRelativeOffset, int endRelativeOffset) {
      return _ -> {};
    }
  }

  /**
   * A collapsed fold region. Its placeholder takes the visual columns of its text.
   */
  final class FoldRegionFragment extends Fragment {
    private FoldRegion region;
    private int columns;

    void set(FoldRegion foldRegion, int foldRegionColumns) {
      region = foldRegion;
      columns = foldRegionColumns;
    }

    @Override
    int getStartVisualColumn() {
      return myCurrentVisualColumn - columns;
    }

    @Override
    int logicalToVisualColumn(int column) {
      return myCurrentVisualColumn - columns;
    }

    @Override
    int visualToLogicalColumn(int column) {
      if (column == myCurrentVisualColumn) {
        return getEndLogicalColumn();
      }
      return getStartLogicalColumn();
    }

    @Override
    VisualColumn xToVisualColumn(float x) {
      VisualColumn column = getVisualColumnForXInsideFoldRegion(x - getStartX());
      column.column += getStartVisualColumn();
      return column;
    }

    @Override
    float visualColumnToX(int column) {
      return getStartX() + getXForVisualColumnInsideFoldRegion(column - myCurrentVisualColumn + columns);
    }

    @Override
    int getVisualLength() {
      int length = 0;
      for (var fragment : getFoldRegionLayout().getFragmentsInVisualOrder(0)) {
        length += fragment.getLength();
      }
      return length;
    }

    @Override
    int visualColumnToOffset(int relativeVisualColumn) {
      int relativeOffset = 0;
      for (var fragment : getFoldRegionLayout().getFragmentsInVisualOrder(0)) {
        if (relativeVisualColumn >= fragment.getStartVisualColumn() &&
            relativeVisualColumn <= fragment.getEndVisualColumn()) {
          int visualOffset = fragment.visualColumnToOffset(relativeVisualColumn - fragment.getStartVisualColumn());
          return visualOffset + relativeOffset;
        }
        relativeOffset += fragment.getLength();
      }
      return 0;
    }

    @Override
    int getStartOffset() {
      return region.getStartOffset();
    }

    @Override
    int getEndOffset() {
      return region.getEndOffset();
    }

    @Override
    int getMinOffset() {
      return region.getStartOffset();
    }

    @Override
    int getMaxOffset() {
      return region.getEndOffset();
    }

    @Override
    float offsetToX(int offset) {
      return getStartX() + getXForOffsetInsideFoldRegion(offset);
    }

    @Override
    FoldRegion getCurrentFoldRegion() {
      return region;
    }

    @Override
    @NotNull
    Consumer<Graphics2D> draw(float x, float y, int startRelativeOffset, int endRelativeOffset) {
      LineLayout foldRegionLayout = getFoldRegionLayout();
      return g -> {
        int relativeOffset = 0;
        for (var fragment : foldRegionLayout.getFragmentsInVisualOrder(x)) {
          int relativeOffsetEnd = relativeOffset + fragment.getLength();
          if (relativeOffset < endRelativeOffset && relativeOffsetEnd > startRelativeOffset) {
            fragment.draw(
              fragment.getStartX(), y,
              Math.max(0, startRelativeOffset - relativeOffset),
              Math.min(relativeOffsetEnd, endRelativeOffset) - relativeOffset
            ).accept(g);
          }
          relativeOffset = relativeOffsetEnd;
        }
      };
    }

    private @NotNull LineLayout getFoldRegionLayout() {
      return myView.getFoldRegionLayout(region);
    }

    private @NotNull VisualColumn getVisualColumnForXInsideFoldRegion(float x) {
      LineLayout layout = getFoldRegionLayout();
      for (var fragment : layout.getFragmentsInVisualOrder(0)) {
        if (x <= fragment.getEndX()) {
          return fragment.xToVisualColumn(x);
        }
      }
      return new VisualColumn(columns, true);
    }

    private float getXForVisualColumnInsideFoldRegion(int column) {
      LineLayout layout = getFoldRegionLayout();
      for (LineVisualFragment fragment : layout.getFragmentsInVisualOrder(0)) {
        if (column <= fragment.getEndVisualColumn()) {
          return fragment.visualColumnToX(column);
        }
      }
      return getFoldRegionWidthInPixels(region);
    }

    /**
     * The offset is absolute.
     */
    private float getXForOffsetInsideFoldRegion(int offset) {
      return offset < region.getEndOffset() ? 0 : getFoldRegionWidthInPixels(region);
    }
  }

  private @NotNull String assertMessage(
    int segmentStartOffset,
    int segmentEndOffset,
    int foldRegionIndex,
    @Nullable List<Inlay<?>> inlays,
    int inlayIndex
  ) {
    String foldRegion = foldRegionIndex < myRegions.length ? myRegions[foldRegionIndex].toString() : "";
    String inlayCount = inlays != null ? String.valueOf(inlays.size()) : "null";
    String inlay = inlays != null && inlayIndex < inlays.size() ? inlays.get(inlayIndex).toString() : "";
    return String.join(
      ", ",
      "startOffset: " + segmentStartOffset,
      "endOffset: " + segmentEndOffset,
      "foldIndex: " + foldRegionIndex,
      "foldCount: " + myRegions.length,
      "fold: " + foldRegion,
      "inlayIndex: " + inlayIndex,
      "inlayCount: " + inlayCount,
      "inlay: " + inlay,
      "textLength: " + myDocument.length()
    );
  }
}
