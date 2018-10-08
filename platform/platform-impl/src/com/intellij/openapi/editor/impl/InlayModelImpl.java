// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.PrioritizedInternalDocumentListener;
import com.intellij.openapi.util.Getter;
import com.intellij.util.DocumentUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public class InlayModelImpl implements InlayModel, Disposable {
  private static final Logger LOG = Logger.getInstance(InlayModelImpl.class);
  private static final Comparator<Inlay> INLINE_ELEMENTS_COMPARATOR = Comparator.comparingInt((Inlay inlay) -> inlay.getOffset())
    .thenComparing(i -> i.isRelatedToPrecedingText());
  private static final Comparator<BlockInlayImpl> BLOCK_ELEMENTS_PRIORITY_COMPARATOR =
    Comparator.comparingInt((BlockInlayImpl i) -> -i.myPriority);
  private static final Comparator<BlockInlayImpl> BLOCK_ELEMENTS_COMPARATOR =
    Comparator.comparing((BlockInlayImpl inlay) -> inlay.getVerticalAlignment())
      .thenComparing(i -> i.getVerticalAlignment() == Inlay.VerticalAlignment.ABOVE_LINE ? i.myPriority : -i.myPriority);
  private static final Processor<InlayImpl> UPDATE_SIZE_PROCESSOR = inlay -> {
    inlay.updateSize();
    return true;
  };

  private final EditorImpl myEditor;
  private final EventDispatcher<Listener> myDispatcher = EventDispatcher.create(Listener.class);

  final List<InlayImpl> myInlaysInvalidatedOnMove = new ArrayList<>();
  final RangeMarkerTree<InlineInlayImpl> myInlineElementsTree;
  final MarkerTreeWithPartialSums<BlockInlayImpl> myBlockElementsTree;

  boolean myMoveInProgress;
  boolean myPutMergedIntervalsAtBeginning;
  private List<Inlay> myInlaysAtCaret;

  InlayModelImpl(@NotNull EditorImpl editor) {
    myEditor = editor;
    myInlineElementsTree = new InlineElementsTree(editor.getDocument());
    myBlockElementsTree = new BlockElementsTree(editor.getDocument());
    myEditor.getDocument().addDocumentListener(new PrioritizedInternalDocumentListener() {
      @Override
      public int getPriority() {
        return EditorDocumentPriorities.INLAY_MODEL;
      }

      @Override
      public void beforeDocumentChange(@NotNull DocumentEvent event) {
        if (myEditor.getDocument().isInBulkUpdate()) return;
        int offset = event.getOffset();
        if (event.getOldLength() == 0 && offset == myEditor.getCaretModel().getOffset()) {
          List<Inlay> inlays = getInlineElementsInRange(offset, offset);
          int inlayCount = inlays.size();
          if (inlayCount > 0) {
            VisualPosition inlaysStartPosition = myEditor.offsetToVisualPosition(offset, false, false);
            VisualPosition caretPosition = myEditor.getCaretModel().getVisualPosition();
            if (inlaysStartPosition.line == caretPosition.line &&
                caretPosition.column >= inlaysStartPosition.column && caretPosition.column <= inlaysStartPosition.column + inlayCount) {
              myInlaysAtCaret = inlays;
              for (int i = 0; i < inlayCount; i++) {
                ((InlayImpl)inlays.get(i)).setStickingToRight(i >= (caretPosition.column - inlaysStartPosition.column));
              }
            }
          }
        }
      }

      @Override
      public void documentChanged(@NotNull DocumentEvent event) {
        if (myInlaysAtCaret != null) {
          for (Inlay inlay : myInlaysAtCaret) {
            ((InlayImpl)inlay).setStickingToRight(inlay.isRelatedToPrecedingText());
          }
          myInlaysAtCaret = null;
        }
      }

      @Override
      public void moveTextHappened(@NotNull Document document, int start, int end, int base) {
        for (InlayImpl inlay : myInlaysInvalidatedOnMove) {
          notifyRemoved(inlay);
        }
        myInlaysInvalidatedOnMove.clear();
      }
    }, this);
  }

  void reinitSettings() {
    myInlineElementsTree.processAll(UPDATE_SIZE_PROCESSOR);
    myBlockElementsTree.processAll(UPDATE_SIZE_PROCESSOR);
  }

  @Override
  public void dispose() {
    myInlineElementsTree.dispose(myEditor.getDocument());
    myBlockElementsTree.dispose(myEditor.getDocument());
  }

  @Nullable
  @Override
  public <T extends EditorCustomElementRenderer> Inlay<T> addInlineElement(int offset,
                                                                           boolean relatesToPrecedingText,
                                                                           @NotNull T renderer) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Document document = myEditor.getDocument();
    if (DocumentUtil.isInsideSurrogatePair(document, offset)) return null;
    offset = Math.max(0, Math.min(document.getTextLength(), offset));
    InlineInlayImpl<T> inlay = new InlineInlayImpl<>(myEditor, offset, relatesToPrecedingText, renderer);
    notifyAdded(inlay);
    return inlay;
  }

  @Nullable
  @Override
  public <T extends EditorCustomElementRenderer> Inlay<T> addBlockElement(int offset,
                                                                          boolean relatesToPrecedingText,
                                                                          boolean showAbove,
                                                                          int priority,
                                                                          @NotNull T renderer) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    offset = Math.max(0, Math.min(myEditor.getDocument().getTextLength(), offset));
    BlockInlayImpl<T> inlay = new BlockInlayImpl<>(myEditor, offset, relatesToPrecedingText, showAbove, priority, renderer);
    notifyAdded(inlay);
    return inlay;
  }

  @NotNull
  @Override
  public List<Inlay> getInlineElementsInRange(int startOffset, int endOffset) {
    List<InlineInlayImpl> range =
      getElementsInRange(myInlineElementsTree, startOffset, endOffset, inlay -> true, INLINE_ELEMENTS_COMPARATOR);
    //noinspection unchecked
    return (List)range;
  }

  @NotNull
  @Override
  public <T> List<Inlay<? extends T>> getInlineElementsInRange(int startOffset, int endOffset, Class<T> type) {
    List<InlineInlayImpl> range =
      getElementsInRange(myInlineElementsTree, startOffset, endOffset, inlay -> type.isInstance(inlay.myRenderer),
                         INLINE_ELEMENTS_COMPARATOR);
    //noinspection unchecked
    return (List)range;
  }

  @NotNull
  @Override
  public List<Inlay> getBlockElementsInRange(int startOffset, int endOffset) {
    List<BlockInlayImpl> range =
      getElementsInRange(myBlockElementsTree, startOffset, endOffset, inlay -> true, BLOCK_ELEMENTS_PRIORITY_COMPARATOR);
    //noinspection unchecked
    return (List)range;
  }

  @NotNull
  @Override
  public <T> List<Inlay<? extends T>> getBlockElementsInRange(int startOffset, int endOffset, Class<T> type) {
    List<BlockInlayImpl> range = getElementsInRange(myBlockElementsTree, startOffset, endOffset, inlay -> type.isInstance(inlay.myRenderer),
                                                    BLOCK_ELEMENTS_PRIORITY_COMPARATOR);
    //noinspection unchecked
    return (List)range;
  }

  private static <T extends Inlay> List<T> getElementsInRange(@NotNull IntervalTreeImpl<? extends T> tree,
                                                              int startOffset,
                                                              int endOffset,
                                                              Predicate<? super T> predicate,
                                                              Comparator<? super T> comparator) {
    List<T> result = new ArrayList<>();
    tree.processOverlappingWith(startOffset, endOffset, inlay -> {
      if (predicate.test(inlay)) result.add(inlay);
      return true;
    });
    Collections.sort(result, comparator);
    return result;
  }

  @NotNull
  @Override
  public List<Inlay> getBlockElementsForVisualLine(int visualLine, boolean above) {
    int visibleLineCount = myEditor.getVisibleLineCount();
    if (visualLine < 0 || visualLine >= visibleLineCount) return Collections.emptyList();
    List<BlockInlayImpl> result = new ArrayList<>();
    int startOffset = myEditor.visualLineStartOffset(visualLine);
    int endOffset = visualLine == visibleLineCount - 1 ? myEditor.getDocument().getTextLength()
                                                       : myEditor.visualLineStartOffset(visualLine + 1) - 1;
    myBlockElementsTree.processOverlappingWith(startOffset, endOffset, inlay -> {
      if (inlay.myShowAbove == above && !myEditor.getFoldingModel().isOffsetCollapsed(inlay.getOffset())) {
        result.add(inlay);
      }
      return true;
    });
    Collections.sort(result, BLOCK_ELEMENTS_COMPARATOR);
    //noinspection unchecked
    return (List)result;
  }

  public int getHeightOfBlockElementsBeforeVisualLine(int visualLine) {
    if (visualLine < 0 || !hasBlockElements()) return 0;
    int visibleLineCount = myEditor.getVisibleLineCount();
    if (visualLine >= visibleLineCount) {
      return myBlockElementsTree.getSumOfValuesUpToOffset(Integer.MAX_VALUE) -
             myEditor.getFoldingModel().getTotalHeightOfFoldedBlockInlays();
    }
    int[] result = {0};
    int startOffset = myEditor.visualLineStartOffset(visualLine);
    int endOffset = visualLine >= visibleLineCount - 1 ? myEditor.getDocument().getTextLength()
                                                       : myEditor.visualLineStartOffset(visualLine + 1) - 1;
    if (visualLine > 0) {
      result[0] += myBlockElementsTree.getSumOfValuesUpToOffset(startOffset - 1) -
                   myEditor.getFoldingModel().getHeightOfFoldedBlockInlaysBefore(startOffset);
    }
    myBlockElementsTree.processOverlappingWith(startOffset, endOffset, inlay -> {
      if (inlay.myShowAbove && !myEditor.getFoldingModel().isOffsetCollapsed(inlay.getOffset())) {
        result[0] += inlay.getHeightInPixels();
      }
      return true;
    });
    return result[0];
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
    int offset = myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(visualPosition));
    int inlayCount = getInlineElementsInRange(offset, offset).size();
    if (inlayCount == 0) return false;
    VisualPosition inlayStartPosition = myEditor.offsetToVisualPosition(offset, false, false);
    return visualPosition.line == inlayStartPosition.line &&
           visualPosition.column >= inlayStartPosition.column && visualPosition.column < inlayStartPosition.column + inlayCount;
  }

  @Nullable
  @Override
  public Inlay getInlineElementAt(@NotNull VisualPosition visualPosition) {
    int offset = myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(visualPosition));
    List<Inlay> inlays = getInlineElementsInRange(offset, offset);
    if (inlays.isEmpty()) return null;
    VisualPosition inlayStartPosition = myEditor.offsetToVisualPosition(offset, false, false);
    if (visualPosition.line != inlayStartPosition.line) return null;
    int inlayIndex = visualPosition.column - inlayStartPosition.column;
    return inlayIndex >= 0 && inlayIndex < inlays.size() ? inlays.get(inlayIndex) : null;
  }

  @Nullable
  @Override
  public Inlay getElementAt(@NotNull Point point) {
    boolean hasInlineElements = hasInlineElements();
    boolean hasBlockElements = hasBlockElements();
    if (!hasInlineElements && !hasBlockElements) return null;

    VisualPosition visualPosition = myEditor.xyToVisualPosition(point);
    if (hasBlockElements) {
      int visualLine = visualPosition.line;
      int baseY = myEditor.visualLineToY(visualLine);
      if (point.y < baseY) {
        List<Inlay> inlays = getBlockElementsForVisualLine(visualLine, true);
        int yDiff = baseY - point.y;
        for (int i = inlays.size() - 1; i >= 0; i--) {
          Inlay inlay = inlays.get(i);
          int height = inlay.getHeightInPixels();
          if (yDiff <= height) return inlay;
          yDiff -= height;
        }
        throw new IllegalStateException();
      }
      else {
        int lineBottom = baseY + myEditor.getLineHeight();
        if (point.y >= lineBottom) {
          List<Inlay> inlays = getBlockElementsForVisualLine(visualLine, false);
          int yDiff = point.y - lineBottom;
          for (Inlay inlay : inlays) {
            int height = inlay.getHeightInPixels();
            if (yDiff < height) return inlay;
            yDiff -= height;
          }
          throw new IllegalStateException();
        }
      }
    }
    if (hasInlineElements) {
      int offset = myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(visualPosition));
      List<Inlay> inlays = getInlineElementsInRange(offset, offset);
      if (inlays.isEmpty()) return null;

      VisualPosition startVisualPosition = myEditor.offsetToVisualPosition(offset);
      int x = myEditor.visualPositionToXY(startVisualPosition).x;
      for (Inlay inlay : inlays) {
        int endX = x + inlay.getWidthInPixels();
        if (point.x >= x && point.x < endX) return inlay;
        x = endX;
      }
    }
    return null;
  }

  @Override
  public void addListener(@NotNull Listener listener, @NotNull Disposable disposable) {
    myDispatcher.addListener(listener, disposable);
  }

  private void notifyAdded(InlayImpl inlay) {
    myDispatcher.getMulticaster().onAdded(inlay);
  }

  void notifyChanged(InlayImpl inlay) {
    myDispatcher.getMulticaster().onUpdated(inlay);
  }

  void notifyRemoved(InlayImpl inlay) {
    myDispatcher.getMulticaster().onRemoved(inlay);
  }

  @TestOnly
  public void validateState() {
    for (Inlay inlay : getInlineElementsInRange(0, myEditor.getDocument().getTextLength())) {
      LOG.assertTrue(!DocumentUtil.isInsideSurrogatePair(myEditor.getDocument(), inlay.getOffset()));
    }
  }

  private class InlineElementsTree extends HardReferencingRangeMarkerTree<InlineInlayImpl> {
    InlineElementsTree(@NotNull Document document) {
      super(document);
    }

    @NotNull
    @Override
    protected Node<InlineInlayImpl> createNewNode(@NotNull InlineInlayImpl key, int start, int end,
                                                  boolean greedyToLeft, boolean greedyToRight, boolean stickingToRight, int layer) {
      return new Node<InlineInlayImpl>(this, key, start, end, greedyToLeft, greedyToRight, stickingToRight) {
        @Override
        void addIntervalsFrom(@NotNull IntervalNode<InlineInlayImpl> otherNode) {
          super.addIntervalsFrom(otherNode);
          if (myPutMergedIntervalsAtBeginning) {
            List<Getter<InlineInlayImpl>> added = ContainerUtil.subList(intervals, intervals.size() - otherNode.intervals.size());
            List<Getter<InlineInlayImpl>> addedCopy = new ArrayList<>(added);
            added.clear();
            intervals.addAll(0, addedCopy);
          }
        }
      };
    }

    @Override
    void fireBeforeRemoved(@NotNull InlineInlayImpl inlay, @NotNull @NonNls Object reason) {
      if (inlay.getUserData(InlayImpl.OFFSET_BEFORE_DISPOSAL) == null) {
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

  private class BlockElementsTree extends MarkerTreeWithPartialSums<BlockInlayImpl> {
    BlockElementsTree(@NotNull Document document) {
      super(document);
    }

    @Override
    void fireBeforeRemoved(@NotNull BlockInlayImpl inlay, @NotNull @NonNls Object reason) {
      if (inlay.getUserData(InlayImpl.OFFSET_BEFORE_DISPOSAL) == null) {
        notifyRemoved(inlay);
      }
    }
  }
}
