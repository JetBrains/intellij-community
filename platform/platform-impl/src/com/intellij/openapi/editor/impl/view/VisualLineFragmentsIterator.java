// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.CustomFoldRegion;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.ex.DocumentText;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.InlayModelEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.SoftWrapModelImpl;
import com.intellij.openapi.editor.impl.softwrap.CustomWrapToSoftWrapAdapter;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapEx;
import com.intellij.openapi.editor.impl.view.VisualLineFragment.FoldRegionFragment;
import com.intellij.openapi.editor.impl.view.VisualLineFragment.InlayFragment;
import com.intellij.openapi.editor.impl.view.VisualLineFragment.TextRunFragment;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Iterator over visual line's fragments. Fragment's text has the same font and directionality. Collapsed fold regions are also represented
 * as fragments.
 */
final class VisualLineFragmentsIterator implements Iterator<VisualLineFragment> {
  static @NotNull Iterable<VisualLineFragment> create(
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
  static @NotNull Iterable<VisualLineFragment> create(
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
  private final @Nullable Runnable myQuickEvaluationListener;
  private final TextRunFragment myTextRunFragment;
  private final InlayFragment myInlayFragment;
  private final FoldRegionFragment myFoldRegionFragment;

  private int mySegmentStartOffset;
  private int mySegmentEndOffset;
  private float myCurrentX;
  private int myCurrentVisualColumn;
  private int myCurrentEndLogicalLine;
  private Iterator<LineVisualFragment> myFragmentIterator;
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
      int visualLine = view.offsetToVisualLine(offset, beforeSoftWrap);
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
    myTextRunFragment = new TextRunFragment(view, visualLineStartOffset, xOffset);
    myInlayFragment = new InlayFragment(view, visualLineStartOffset, xOffset);
    myFoldRegionFragment = new FoldRegionFragment(view, visualLineStartOffset, xOffset);
    myRegions = fetchTopLevelFoldings(view);
    var softWraps = view.getSoftWrapModel().getRegisteredSoftWrapsEx();
    myNextWrap = getSoftWrap(softWraps, startOrPrevWrapIndex + 1);
    init(visualLineStartOffset, startLogicalLine, startFoldingIndex, getSoftWrap(softWraps, startOrPrevWrapIndex));
  }

  @Override
  public boolean hasNext() {
    return myNextStep != Step.END;
  }

  @Override
  public VisualLineFragment next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    float startX = myCurrentX;
    int startLogicalLine = myCurrentEndLogicalLine;
    VisualLineFragment fragment;
    if (myNextStep == Step.FOLD_REGION) {
      fragment = moveToFoldRegion();
    } else if (myNextStep == Step.INLAY) {
      fragment = moveToInlay();
    } else {
      fragment = moveToTextRun();
    }
    fragment.setBounds(
      startX,
      myCurrentX,
      myCurrentVisualColumn,
      startLogicalLine,
      myCurrentEndLogicalLine
    );
    return fragment;
  }

  private @NotNull VisualLineFragment moveToFoldRegion() {
    FoldRegion foldRegion = myRegions[myCurrentFoldRegionIndex];
    assert foldRegion.isValid();
    int foldRegionColumns = FoldRegionFragment.calculateWidthInColumns(myView, foldRegion);
    float foldRegionWidth = FoldRegionFragment.calculateWidthInPixels(myView, foldRegion);
    int segmentStartOffset = foldRegion.getEndOffset();
    int logicalLine = myDocument.lineNumber(segmentStartOffset);
    int visualColumn = myCurrentVisualColumn + foldRegionColumns;
    float x = myCurrentX + foldRegionWidth;
    if (foldRegion instanceof CustomFoldRegion) {
      endVisualLine(logicalLine, visualColumn, x);
    } else {
      enterSegment(
        segmentStartOffset,
        logicalLine,
        myCurrentFoldRegionIndex + 1,
        visualColumn,
        x,
        null
      );
    }
    myFoldRegionFragment.set(foldRegion, foldRegionColumns);
    return myFoldRegionFragment;
  }

  private void endVisualLine(int logicalLine, int visualColumn, float x) {
    myCurrentEndLogicalLine = logicalLine;
    myCurrentVisualColumn = visualColumn;
    myCurrentX = x;
    myNextStep = Step.END;
  }

  private @NotNull VisualLineFragment moveToInlay() {
    Inlay<?> inlay = myInlays.get(myCurrentInlayIndex);
    float x = (float)(myCurrentX + alignToInt(inlay.getWidthInPixels()));
    int visualColumn = myCurrentVisualColumn + 1;
    int inlayIndex = myCurrentInlayIndex + 1;
    Iterator<LineVisualFragment> fragmentIterator = Collections.emptyIterator();
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

  private @NotNull VisualLineFragment moveToTextRun() {
    LineVisualFragment delegate = myFragmentIterator.next();
    float x = delegate.getEndX();
    int visualColumn = delegate.getEndVisualColumn();
    int lineStartOffset = myDocument.lineStartOffset(myCurrentEndLogicalLine);
    Step nextStep = Step.TEXT_RUN;
    if (!myFragmentIterator.hasNext()) {
      if (myCurrentInlayIndex < myInlays.size()) {
        nextStep = Step.INLAY;
      } else if (mySegmentEndOffset == getFoldRegionStartOffset(myCurrentFoldRegionIndex)) {
        nextStep = Step.FOLD_REGION;
      } else {
        nextStep = Step.END;
      }
    }
    myCurrentX = x;
    myCurrentVisualColumn = visualColumn;
    myNextStep = nextStep;
    myTextRunFragment.set(delegate, lineStartOffset);
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
      startOrPrevWrap
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
    @Nullable SoftWrapEx startWrap
  ) {
    int foldRegionStartOffset = getFoldRegionStartOffset(foldRegionIndex);
    if (foldRegionStartOffset < segmentStartOffset) {
      String message = assertMessage(segmentStartOffset, foldRegionStartOffset, foldRegionIndex, null, 0);
      myView.getEditor().throwEditorInvariantBroken(message);
    }
    int segmentEndOffset = foldRegionStartOffset;
    List<Inlay<?>> inlays = List.of();
    Iterator<LineVisualFragment> fragmentIterator = Collections.emptyIterator();
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
