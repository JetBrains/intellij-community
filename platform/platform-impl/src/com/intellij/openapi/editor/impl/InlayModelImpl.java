// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.diagnostic.Dumpable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.view.EditorView;
import com.intellij.openapi.util.Predicates;
import com.intellij.openapi.util.Ref;
import com.intellij.util.DocumentEventUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.intellij.openapi.editor.impl.InlayKeys.OFFSET_BEFORE_DISPOSAL;

//@ApiStatus.Internal
public final class InlayModelImpl implements InlayModel, PrioritizedDocumentListener, Disposable, Dumpable {
  private static final Logger LOG = Logger.getInstance(InlayModelImpl.class);

  private static final Comparator<InlineInlayImpl> INLINE_ELEMENTS_COMPARATOR = Comparator
    .comparingInt((InlineInlayImpl i) -> i.getOffset())
    .thenComparing(i -> i.isRelatedToPrecedingText())
    .thenComparing(i -> -i.myPriority);
  private static final Comparator<BlockInlayImpl> BLOCK_ELEMENTS_PRIORITY_COMPARATOR = Comparator
    .comparingInt(i -> -i.myPriority);
  private static final Comparator<BlockInlayImpl> BLOCK_ELEMENTS_COMPARATOR = Comparator
    .comparing((BlockInlayImpl i) -> i.getPlacement())
    .thenComparing(i -> i.getPlacement() == Inlay.Placement.ABOVE_LINE ? i.myPriority : -i.myPriority);
  private static final Comparator<AfterLineEndInlayImpl> AFTER_LINE_END_ELEMENTS_OFFSET_COMPARATOR = Comparator
    .comparingInt((AfterLineEndInlayImpl i) -> i.getOffset())
    .thenComparing(i -> !i.mySoftWrappable)
    .thenComparingInt(i -> -i.myPriority)
    .thenComparingInt(i -> i.myOrder);
  private static final Comparator<AfterLineEndInlayImpl> AFTER_LINE_END_ELEMENTS_COMPARATOR = Comparator
    .comparing((AfterLineEndInlayImpl i) -> !i.mySoftWrappable)
    .thenComparingInt(i -> -i.myPriority)
    .thenComparingInt(i -> i.myOrder);

  private static final Processor<InlayImpl> UPDATE_PROCESSOR = inlay -> {
    inlay.update();
    return true;
  };

  private final EditorImpl myEditor;
  private final EventDispatcher<Listener> myDispatcher = EventDispatcher.create(Listener.class);

  private final List<InlayImpl> myInlaysInvalidatedOnMove = new ArrayList<>();
  final RangeMarkerTree<InlineInlayImpl<?>> myInlineElementsTree;
  final MarkerTreeWithPartialSums<BlockInlayImpl<?>> myBlockElementsTree;
  final RangeMarkerTree<AfterLineEndInlayImpl<?>> myAfterLineEndElementsTree;

  boolean myMoveInProgress;
  boolean myPutMergedIntervalsAtBeginning;
  private boolean myConsiderCaretPositionOnDocumentUpdates = true;
  private List<Inlay<?>> myInlaysAtCaret;
  private boolean myInBatchMode;

  InlayModelImpl(@NotNull EditorImpl editor) {
    myEditor = editor;
    myInlineElementsTree = new InlineElementsTree(editor.getDocument());
    myBlockElementsTree = new BlockElementsTree(editor.getDocument());
    myAfterLineEndElementsTree = new AfterLineEndElementTree(editor.getDocument());
    myEditor.getDocument().addDocumentListener(this, this);
  }

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.INLAY_MODEL;
  }

  @Override
  public void beforeDocumentChange(@NotNull DocumentEvent event) {
    if (myEditor.getDocument().isInBulkUpdate()) return;
    if (myInBatchMode) LOG.error("Document shouldn't be changed during batch inlay operation");
    int offset = event.getOffset();
    if (myConsiderCaretPositionOnDocumentUpdates && event.getOldLength() == 0 && offset == myEditor.getCaretModel().getOffset()) {
      List<Inlay<?>> inlays = getInlineElementsInRange(offset, offset);
      int inlayCount = inlays.size();
      if (inlayCount > 0) {
        VisualPosition inlaysStartPosition = myEditor.offsetToVisualPosition(offset, false, false);
        VisualPosition caretPosition = myEditor.getCaretModel().getVisualPosition();
        if (inlaysStartPosition.line == caretPosition.line &&
            caretPosition.column >= inlaysStartPosition.column && caretPosition.column <= inlaysStartPosition.column + inlayCount) {
          myInlaysAtCaret = inlays;
          for (int i = 0; i < inlayCount; i++) {
            ((InlayImpl<?, ?>)inlays.get(i)).setStickingToRight(i >= caretPosition.column - inlaysStartPosition.column);
          }
        }
      }
    }
  }

  @Override
  public void documentChanged(@NotNull DocumentEvent event) {
    if (myInlaysAtCaret != null) {
      for (Inlay<?> inlay : myInlaysAtCaret) {
        ((InlayImpl<?, ?>)inlay).setStickingToRight(inlay.isRelatedToPrecedingText());
      }
      myInlaysAtCaret = null;
    }
    if (DocumentEventUtil.isMoveInsertion(event)) {
      for (InlayImpl<?, ?> inlay : myInlaysInvalidatedOnMove) {
        notifyRemoved(inlay);
      }
      myInlaysInvalidatedOnMove.clear();
    }
  }

  void reinitSettings() {
    myInlineElementsTree.processAll(UPDATE_PROCESSOR);
    myBlockElementsTree.processAll(UPDATE_PROCESSOR);
    myAfterLineEndElementsTree.processAll(UPDATE_PROCESSOR);
  }

  @Override
  public void dispose() {
    myInlineElementsTree.dispose(myEditor.getDocument());
    myBlockElementsTree.dispose(myEditor.getDocument());
    myAfterLineEndElementsTree.dispose(myEditor.getDocument());
  }

  @Override
  public @Nullable <T extends EditorCustomElementRenderer> Inlay<T> addInlineElement(int offset,
                                                                                     boolean relatesToPrecedingText,
                                                                                     @NotNull T renderer) {
    return addInlineElement(offset, relatesToPrecedingText, 0, renderer);
  }

  @Override
  public @Nullable <T extends EditorCustomElementRenderer> Inlay<T> addInlineElement(int offset,
                                                                                     boolean relatesToPrecedingText,
                                                                                     int priority,
                                                                                     @NotNull T renderer) {
    EditorImpl.assertIsDispatchThread();
    Document document = myEditor.getDocument();
    if (DocumentUtil.isInsideSurrogatePair(document, offset)) return null;
    offset = Math.max(0, Math.min(document.getTextLength(), offset));
    InlineInlayImpl<T> inlay = new InlineInlayImpl<>(myEditor, offset, relatesToPrecedingText, priority, renderer);
    notifyAdded(inlay);
    return inlay;
  }

  @Override
  public @Nullable <T extends EditorCustomElementRenderer> Inlay<T> addInlineElement(int offset,
                                                                                     @NotNull InlayProperties properties,
                                                                                     @NotNull T renderer) {
    return addInlineElement(offset, properties.isRelatedToPrecedingText(), properties.getPriority(), renderer);
  }

  @Override
  public <T extends EditorCustomElementRenderer> @NotNull Inlay<T> addBlockElement(int offset,
                                                                                   boolean relatesToPrecedingText,
                                                                                   boolean showAbove,
                                                                                   int priority,
                                                                                   @NotNull T renderer) {
    return addBlockElement(offset, relatesToPrecedingText, showAbove, false, priority, renderer);
  }

  @Override
  public <T extends EditorCustomElementRenderer> Inlay<T> addBlockElement(int offset,
                                                                          @NotNull InlayProperties properties,
                                                                          @NotNull T renderer) {
    return addBlockElement(offset,
                           properties.isRelatedToPrecedingText(),
                           properties.isShownAbove(),
                           properties.isShownWhenFolded(),
                           properties.getPriority(),
                           renderer);
  }

  private <T extends EditorCustomElementRenderer> Inlay<T> addBlockElement(int offset,
                                                                           boolean relatesToPrecedingText,
                                                                           boolean showAbove,
                                                                           boolean showWhenFolded,
                                                                           int priority,
                                                                           @NotNull T renderer) {
    EditorImpl.assertIsDispatchThread();
    offset = Math.max(0, Math.min(myEditor.getDocument().getTextLength(), offset));
    BlockInlayImpl<T> inlay = new BlockInlayImpl<>(myEditor, offset, relatesToPrecedingText, showAbove, showWhenFolded, priority, renderer);
    notifyAdded(inlay);
    return inlay;
  }

  @Override
  public <T extends EditorCustomElementRenderer> @NotNull Inlay<T> addAfterLineEndElement(int offset,
                                                                                          boolean relatesToPrecedingText,
                                                                                          @NotNull T renderer) {
   return addAfterLineEndElement(offset, relatesToPrecedingText, true, 0, renderer);
  }

  @Override
  public <T extends EditorCustomElementRenderer> Inlay<T> addAfterLineEndElement(int offset,
                                                                                 @NotNull InlayProperties properties,
                                                                                 @NotNull T renderer) {
    return addAfterLineEndElement(offset, properties.isRelatedToPrecedingText(), !properties.isSoftWrappingDisabled(),
                                  properties.getPriority(), renderer);
  }


  private @NotNull <T extends EditorCustomElementRenderer> Inlay<T> addAfterLineEndElement(int offset,
                                                                                           boolean relatesToPrecedingText,
                                                                                           boolean softWrappable,
                                                                                           int priority,
                                                                                           @NotNull T renderer) {
    EditorImpl.assertIsDispatchThread();
    Document document = myEditor.getDocument();
    offset = Math.max(0, Math.min(document.getTextLength(), offset));
    AfterLineEndInlayImpl<T> inlay = new AfterLineEndInlayImpl<>(myEditor, offset, relatesToPrecedingText, softWrappable, priority,
                                                                 renderer);
    notifyAdded(inlay);
    return inlay;
  }

  @Override
  public @NotNull List<Inlay<?>> getInlineElementsInRange(int startOffset, int endOffset) {
    List<InlineInlayImpl<?>> range = getElementsInRange(myInlineElementsTree, startOffset, endOffset, Predicates.alwaysTrue(),
                                                        INLINE_ELEMENTS_COMPARATOR);
    //noinspection unchecked
    return (List)range;
  }

  @Override
  public @NotNull <T> List<Inlay<? extends T>> getInlineElementsInRange(int startOffset, int endOffset, @NotNull Class<T> type) {
    List<InlineInlayImpl<?>> range =
      getElementsInRange(myInlineElementsTree, startOffset, endOffset, inlay -> type.isInstance(inlay.myRenderer),
                         INLINE_ELEMENTS_COMPARATOR);
    //noinspection unchecked
    return (List)range;
  }

  @Override
  public @NotNull List<Inlay<?>> getBlockElementsInRange(int startOffset, int endOffset) {
    List<BlockInlayImpl<?>> range =
      getElementsInRange(myBlockElementsTree, startOffset, endOffset, Predicates.alwaysTrue(), BLOCK_ELEMENTS_PRIORITY_COMPARATOR);
    //noinspection unchecked
    return (List)range;
  }

  @Override
  public @NotNull <T> List<Inlay<? extends T>> getBlockElementsInRange(int startOffset, int endOffset, @NotNull Class<T> type) {
    List<BlockInlayImpl<?>> range = getElementsInRange(myBlockElementsTree, startOffset, endOffset,
                                                       inlay -> type.isInstance(inlay.myRenderer), BLOCK_ELEMENTS_PRIORITY_COMPARATOR);
    //noinspection unchecked
    return (List)range;
  }

  private static <T extends Inlay<?>> List<T> getElementsInRange(@NotNull IntervalTreeImpl<? extends T> tree,
                                                                 int startOffset,
                                                                 int endOffset,
                                                                 Predicate<? super T> predicate,
                                                                 Comparator<? super T> comparator) {
    List<T> result = new ArrayList<>();
    tree.processOverlappingWith(startOffset, endOffset, inlay -> {
      if (predicate.test(inlay)) result.add(inlay);
      return true;
    });
    result.sort(comparator);
    return result;
  }

  @Override
  public @NotNull List<Inlay<?>> getBlockElementsForVisualLine(int visualLine, boolean above) {
    int visibleLineCount = myEditor.getVisibleLineCount();
    if (visualLine < 0 || visualLine >= visibleLineCount || myBlockElementsTree.size() == 0) return Collections.emptyList();
    List<BlockInlayImpl> result = new ArrayList<>();
    int startOffset = myEditor.visualLineStartOffset(visualLine);
    int endOffset = visualLine == visibleLineCount - 1 ? myEditor.getDocument().getTextLength()
                                                       : myEditor.visualLineStartOffset(visualLine + 1) - 1;
    myBlockElementsTree.processOverlappingWith(startOffset, endOffset, inlay -> {
      if (inlay.myShowAbove == above && !EditorUtil.isInlayFolded(inlay)) {
        result.add(inlay);
      }
      return true;
    });
    if (above) Collections.reverse(result); // matters for inlays with equal priority
    result.sort(BLOCK_ELEMENTS_COMPARATOR);
    //noinspection unchecked
    return (List)result;
  }

  @ApiStatus.Internal
  public int getHeightOfBlockElementsBeforeVisualLine(int visualLine, int startOffset, int prevFoldRegionIndex) {
    if (visualLine < 0 || !hasBlockElements()) return 0;
    int visibleLineCount = myEditor.getVisibleLineCount();
    if (visualLine >= visibleLineCount) {
      return myBlockElementsTree.getSumOfValuesUpToOffset(Integer.MAX_VALUE) -
             myEditor.getFoldingModel().getTotalHeightOfFoldedBlockInlays();
    }
    int[] result = {0};
    int endOffset = visualLine >= visibleLineCount - 1 ? myEditor.getDocument().getTextLength()
                                                       : myEditor.visualLineStartOffset(visualLine + 1) - 1;
    if (visualLine > 0) {
      result[0] += myBlockElementsTree.getSumOfValuesUpToOffset(startOffset - 1) -
                   myEditor.getFoldingModel().getHeightOfFoldedBlockInlaysBefore(prevFoldRegionIndex);
    }
    myBlockElementsTree.processOverlappingWith(startOffset, endOffset, inlay -> {
      if (inlay.myShowAbove && !EditorUtil.isInlayFolded(inlay)) {
        result[0] += inlay.getHeightInPixels();
      }
      return true;
    });
    return result[0];
  }

  /**
   * Optimized method making {@link EditorView#getPreferredSize()} faster.
   * Unlike {@link #getElementsInRange}, this method does not allocate and sort an array
   */
  @ApiStatus.Internal
  public @Nullable Inlay<?> getWidestVisibleBlockInlay() {
    AtomicInteger maxWidth = new AtomicInteger(-1);
    Ref<Inlay<?>> inlayRef = new Ref<>(null);
    myBlockElementsTree.processAll(inlay -> {
      int width = inlay.getWidthInPixels();
      if (width > maxWidth.get() && !EditorUtil.isInlayFolded(inlay)) {
        maxWidth.set(width);
        inlayRef.set(inlay);
      }
      return true;
    });
    return inlayRef.get();
  }

  @Override
  public boolean hasBlockElements() {
    return myBlockElementsTree.size() > 0;
  }

  @Override
  public boolean hasInlineElementsInRange(int startOffset, int endOffset) {
    return !myInlineElementsTree.processOverlappingWith(startOffset, endOffset, inlay -> false);
  }

  @Override
  public boolean hasInlineElements() {
    return myInlineElementsTree.size() > 0;
  }

  @Override
  public boolean hasInlineElementAt(int offset) {
    return !myInlineElementsTree.processOverlappingWith(offset, offset, inlay -> false);
  }

  @Override
  public boolean hasInlineElementAt(@NotNull VisualPosition visualPosition) {
    int offset = myEditor.visualPositionToOffset(visualPosition);
    int inlayCount = getInlineElementsInRange(offset, offset).size();
    if (inlayCount == 0) return false;
    VisualPosition inlayStartPosition = myEditor.offsetToVisualPosition(offset, false, false);
    return visualPosition.line == inlayStartPosition.line &&
           visualPosition.column >= inlayStartPosition.column && visualPosition.column < inlayStartPosition.column + inlayCount;
  }

  @Override
  public @Nullable Inlay getInlineElementAt(@NotNull VisualPosition visualPosition) {
    int offset = myEditor.visualPositionToOffset(visualPosition);
    List<Inlay<?>> inlays = getInlineElementsInRange(offset, offset);
    if (inlays.isEmpty()) return null;
    VisualPosition inlayStartPosition = myEditor.offsetToVisualPosition(offset, false, false);
    if (visualPosition.line != inlayStartPosition.line) return null;
    int inlayIndex = visualPosition.column - inlayStartPosition.column;
    return inlayIndex >= 0 && inlayIndex < inlays.size() ? inlays.get(inlayIndex) : null;
  }

  @Override
  public @Nullable Inlay getElementAt(@NotNull Point point) {
    return getElementAt(new EditorLocation(myEditor, point), false);
  }

  Inlay getElementAt(@NotNull EditorLocation location, boolean ignoreBlockElementWidth) {
    return ReadAction.compute(() -> {
      Insets insets = myEditor.getContentComponent().getInsets();
      Point point = location.getPoint();
      if (point.y < insets.top) return null; // can happen for mouse drag events
      int relX = point.x - insets.left;
      if (relX < 0) return null;

      boolean hasInlineElements = hasInlineElements();
      boolean hasBlockElements = hasBlockElements();
      boolean hasAfterLineEndElements = hasAfterLineEndElements();
      if (!hasInlineElements && !hasBlockElements && !hasAfterLineEndElements) return null;

      VisualPosition visualPosition = location.getVisualPosition();
      if (hasBlockElements) {
        int visualLine = visualPosition.line;
        int baseY = location.getVisualLineStartY();
        if (point.y < baseY) {
          List<Inlay<?>> inlays = getBlockElementsForVisualLine(visualLine, true);
          int yDiff = baseY - point.y;
          for (int i = inlays.size() - 1; i >= 0; i--) {
            Inlay inlay = inlays.get(i);
            yDiff -= inlay.getHeightInPixels();
            if (yDiff <= 0) {
              return ignoreBlockElementWidth || relX < inlay.getWidthInPixels() ? inlay : null;
            }
          }
          LOG.error("Inconsistent state: " + point + ", " + visualPosition + ", baseY=" + baseY + ", " + inlays,
                    new Attachment("editorState.txt", myEditor.dumpState()));
          return null;
        }
        else {
          int lineBottom = location.getVisualLineEndY();
          if (point.y >= lineBottom) {
            List<Inlay<?>> inlays = getBlockElementsForVisualLine(visualLine, false);
            int yDiff = point.y - lineBottom;
            for (Inlay inlay : inlays) {
              yDiff -= inlay.getHeightInPixels();
              if (yDiff < 0) {
                return relX < inlay.getWidthInPixels() ? inlay : null;
              }
            }
            LOG.error("Inconsistent state: " + point + ", " + visualPosition + ", lineBottom=" + lineBottom + ", " + inlays,
                      new Attachment("editorState.txt", myEditor.dumpState()));
            return null;
          }
        }
      }
      if (hasInlineElements) {
        if (location.getCollapsedRegion() == null) {
          int offset = location.getOffset();
          List<Inlay<?>> inlays = getInlineElementsInRange(offset, offset);
          if (!inlays.isEmpty()) {
            VisualPosition startVisualPosition = myEditor.offsetToVisualPosition(offset);
            Point inlayPoint = myEditor.visualPositionToXY(startVisualPosition);
            if (point.y < inlayPoint.y || point.y >= inlayPoint.y + myEditor.getLineHeight()) return null;
            Inlay<?> inlay = findInlay(inlays, point.x, inlayPoint.x);
            if (inlay != null) return inlay;
          }
        }
      }
      if (hasAfterLineEndElements) {
        int offset = location.getOffset();
        int logicalLine = myEditor.getDocument().getLineNumber(offset);
        if (offset == myEditor.getDocument().getLineEndOffset(logicalLine) && location.getCollapsedRegion() == null) {
          List<Inlay<?>> inlays = myEditor.getInlayModel().getAfterLineEndElementsForLogicalLine(logicalLine);
          if (!inlays.isEmpty()) {
            Rectangle bounds = inlays.get(0).getBounds();
            assert bounds != null;
            if (point.y < bounds.y || point.y >= bounds.y + bounds.height) return null;
            Inlay<?> inlay = findInlay(inlays, point.x, bounds.x);
            if (inlay != null) return inlay;
          }
        }
      }
      return null;
    });
  }

  private static Inlay<?> findInlay(List<? extends Inlay<?>> inlays, int x, int startX) {
    for (Inlay inlay : inlays) {
      int endX = startX + inlay.getWidthInPixels();
      if (x >= startX && x < endX) return inlay;
      startX = endX;
    }
    return null;
  }

  @Override
  public @NotNull List<Inlay<?>> getAfterLineEndElementsInRange(int startOffset, int endOffset) {
    if (!hasAfterLineEndElements()) return Collections.emptyList();
    List<AfterLineEndInlayImpl<?>> range =
      getElementsInRange(myAfterLineEndElementsTree, startOffset, endOffset, Predicates.alwaysTrue(), AFTER_LINE_END_ELEMENTS_OFFSET_COMPARATOR);
    //noinspection unchecked
    return (List)range;
  }

  @Override
  public @NotNull <T> List<Inlay<? extends T>> getAfterLineEndElementsInRange(int startOffset, int endOffset, @NotNull Class<T> type) {
    if (!hasAfterLineEndElements()) return Collections.emptyList();
    List<AfterLineEndInlayImpl> range =
      getElementsInRange(myAfterLineEndElementsTree, startOffset, endOffset, inlay -> type.isInstance(inlay.myRenderer),
                         AFTER_LINE_END_ELEMENTS_OFFSET_COMPARATOR);
    //noinspection unchecked
    return (List)range;
  }

  @Override
  public @NotNull List<Inlay<?>> getAfterLineEndElementsForLogicalLine(int logicalLine) {
    DocumentEx document = myEditor.getDocument();
    if (!hasAfterLineEndElements() || logicalLine < 0 || logicalLine > 0 && logicalLine >= document.getLineCount()) {
      return Collections.emptyList();
    }
    List<AfterLineEndInlayImpl> result = new ArrayList<>();
    int startOffset = document.getLineStartOffset(logicalLine);
    int endOffset = document.getLineEndOffset(logicalLine);
    myAfterLineEndElementsTree.processOverlappingWith(startOffset, endOffset, inlay -> {
      result.add(inlay);
      return true;
    });
    result.sort(AFTER_LINE_END_ELEMENTS_COMPARATOR);
    //noinspection unchecked
    return (List)result;
  }

  @Override
  public boolean hasAfterLineEndElements() {
    return myAfterLineEndElementsTree.size() > 0;
  }

  @Override
  public void setConsiderCaretPositionOnDocumentUpdates(boolean enabled) {
    myConsiderCaretPositionOnDocumentUpdates = enabled;
  }

  @Override
  public void execute(boolean batchMode, @NotNull Runnable operation) {
    EditorImpl.assertIsDispatchThread();
    if (myInBatchMode || !batchMode) {
      operation.run();
    }
    else {
      try {
        notifyBatchModeStarting();
        myInBatchMode = true;
        operation.run();
      }
      finally {
        myInBatchMode = false;
        notifyBatchModeFinished();
      }
    }
  }

  @Override
  public boolean isInBatchMode() {
    return myInBatchMode;
  }

  @Override
  public void addListener(@NotNull Listener listener, @NotNull Disposable disposable) {
    EditorImpl.assertIsDispatchThread();
    myDispatcher.addListener(listener, disposable);
  }

  private void notifyAdded(InlayImpl inlay) {
    myDispatcher.getMulticaster().onAdded(inlay);
  }

  void notifyChanged(InlayImpl inlay, int changeFlags) {
    myDispatcher.getMulticaster().onUpdated(inlay, changeFlags);
  }

  void notifyRemoved(InlayImpl inlay) {
    myDispatcher.getMulticaster().onRemoved(inlay);
  }

  private void notifyBatchModeStarting() {
    List<Listener> listeners = myDispatcher.getListeners();
    for (int i = listeners.size() - 1; i >= 0; i--) {
      listeners.get(i).onBatchModeStart(myEditor);
    }
  }

  private void notifyBatchModeFinished() {
    myDispatcher.getMulticaster().onBatchModeFinish(myEditor);
  }

  @TestOnly
  public void validateState() {
    for (Inlay inlay : getInlineElementsInRange(0, myEditor.getDocument().getTextLength())) {
      LOG.assertTrue(!DocumentUtil.isInsideSurrogatePair(myEditor.getDocument(), inlay.getOffset()));
    }
  }

  @Override
  public @NotNull String dumpState() {
    return "Inline elements: " + dumpInlays(myInlineElementsTree)
           + ", after-line-end elements: " + dumpInlays(myAfterLineEndElementsTree)
           + ", block elements: " + dumpInlays(myBlockElementsTree);
  }


  private static String dumpInlays(RangeMarkerTree<? extends InlayImpl> tree) {
    StringJoiner joiner = new StringJoiner(",", "[", "]");
    tree.processAll(o -> {
      joiner.add(Integer.toString(o.getOffset()));
      return true;
    });
    return joiner.toString();
  }

  public static boolean showWhenFolded(@NotNull Inlay<?> inlay) {
    return inlay instanceof BlockInlayImpl && ((BlockInlayImpl<?>)inlay).myShowWhenFolded;
  }

  private final class InlineElementsTree extends HardReferencingRangeMarkerTree<InlineInlayImpl<?>> {
    InlineElementsTree(@NotNull Document document) {
      super(document);
    }

    @Override
    protected @NotNull RMNode<InlineInlayImpl<?>> createNewNode(@NotNull InlineInlayImpl key, int start, int end,
                                                                boolean greedyToLeft, boolean greedyToRight, boolean stickingToRight, int layer) {
      return new RMNode<InlineInlayImpl<?>>(this, key, start, end, greedyToLeft, greedyToRight, stickingToRight) {
        @Override
        void addIntervalsFrom(@NotNull IntervalNode<InlineInlayImpl<?>> otherNode) {
          super.addIntervalsFrom(otherNode);
          if (myPutMergedIntervalsAtBeginning) {
            List<Supplier<? extends InlineInlayImpl<?>>> added = ContainerUtil.subList(intervals, intervals.size() - otherNode.intervals.size());
            List<Supplier<? extends InlineInlayImpl<?>>> addedCopy = new ArrayList<>(added);
            added.clear();
            intervals.addAll(0, addedCopy);
          }
        }
      };
    }

    @Override
    void fireBeforeRemoved(@NotNull InlineInlayImpl inlay) {
      if (inlay.getUserData(OFFSET_BEFORE_DISPOSAL) == null) {
        if (myMoveInProgress) {
          // delay notification about invalidated inlay - folding model is not consistent at this point
          // (FoldingModelImpl.moveTextHappened hasn't been called yet at this point)
          myInlaysInvalidatedOnMove.add(inlay);
        }
        else {
          notifyRemoved(inlay);
        }
      }
    }
  }

  private final class BlockElementsTree extends MarkerTreeWithPartialSums<BlockInlayImpl<?>> {
    BlockElementsTree(@NotNull Document document) {
      super(document);
    }

    @Override
    void fireBeforeRemoved(@NotNull BlockInlayImpl inlay) {
      if (inlay.getUserData(OFFSET_BEFORE_DISPOSAL) == null) {
        notifyRemoved(inlay);
      }
    }
  }

  private final class AfterLineEndElementTree extends HardReferencingRangeMarkerTree<AfterLineEndInlayImpl<?>> {
    AfterLineEndElementTree(@NotNull Document document) {
      super(document);
    }

    @Override
    void fireBeforeRemoved(@NotNull AfterLineEndInlayImpl inlay) {
      if (inlay.getUserData(OFFSET_BEFORE_DISPOSAL) == null) {
        notifyRemoved(inlay);
      }
    }
  }
}
