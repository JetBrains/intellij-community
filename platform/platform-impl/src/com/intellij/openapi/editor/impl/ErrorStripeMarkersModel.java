// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.ex.ErrorStripeEvent;
import com.intellij.openapi.editor.ex.ErrorStripeListener;
import com.intellij.openapi.editor.ex.MarkupIterator;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.List;

/**
 * A mirror of highlighters which should be rendered on the error stripe.
 * */
class ErrorStripeMarkersModel implements MarkupModelListener {
  private final EditorImpl myEditor;
  private final ErrorStripeRangeMarkerTree myTree;
  private final ErrorStripeRangeMarkerTree myTreeForLines;
  private final List<ErrorStripeListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  ErrorStripeMarkersModel(@NotNull EditorImpl editor) {
    myEditor = editor;
    myTree = new ErrorStripeRangeMarkerTree(myEditor.getDocument());
    myTreeForLines = new ErrorStripeRangeMarkerTree(myEditor.getDocument());
  }

  void addErrorMarkerListener(ErrorStripeListener listener, Disposable parent) {
    ContainerUtil.add(listener, myListeners, parent);
  }

  void fireErrorMarkerClicked(RangeHighlighter highlighter, MouseEvent e) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ErrorStripeEvent event = new ErrorStripeEvent(myEditor, e, highlighter);
    myListeners.forEach(listener -> listener.errorMarkerClicked(event));
  }

  @Override
  public void afterAdded(@NotNull RangeHighlighterEx highlighter) {
    if (isAvailable(highlighter))
    if (highlighter.getErrorStripeMarkColor(myEditor.getColorsScheme()) != null) {
      createErrorStripeMarker(highlighter);
    }
  }

  @Override
  public void beforeRemoved(@NotNull RangeHighlighterEx highlighter) {
    ErrorStripeMarkerImpl errorStripeMarker = findErrorStripeMarker(highlighter);
    if (errorStripeMarker != null) {
      removeErrorStripeMarker(errorStripeMarker);
    }
  }

  @Override
  public void attributesChanged(@NotNull RangeHighlighterEx highlighter, boolean renderersChanged, boolean fontStyleOrColorChanged) {
    ErrorStripeMarkerImpl existingErrorStripeMarker = findErrorStripeMarker(highlighter);
    boolean hasErrorStripe = isAvailable(highlighter);

    if (existingErrorStripeMarker == null) {
      if (hasErrorStripe) {
        createErrorStripeMarker(highlighter);
      }
      return;
    }

    if (!hasErrorStripe) {
      removeErrorStripeMarker(existingErrorStripeMarker);
      return;
    }

    existingErrorStripeMarker.setGreedyToLeft(highlighter.isGreedyToLeft());
    existingErrorStripeMarker.setGreedyToRight(highlighter.isGreedyToRight());
    if (highlighter instanceof RangeMarkerImpl) {
      existingErrorStripeMarker.setStickingToRight(((RangeMarkerImpl)highlighter).isStickingToRight());
    }
    myListeners.forEach(l -> l.errorMarkerChanged(new ErrorStripeEvent(myEditor, null, highlighter)));
  }

  private boolean isAvailable(@NotNull RangeHighlighterEx highlighter) {
    return highlighter.getEditorFilter().avaliableIn(myEditor) &&
           myEditor.isHighlighterAvailable(highlighter) &&
           highlighter.getErrorStripeMarkColor(myEditor.getColorsScheme()) != null;
  }

  private void createErrorStripeMarker(@NotNull RangeHighlighterEx highlighter) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ErrorStripeMarkerImpl marker = new ErrorStripeMarkerImpl(myEditor.getDocument(), highlighter);
    RangeHighlighterEx ex = marker.getHighlighter();
    boolean isStickingToRight = (ex instanceof RangeMarkerImpl) && ((RangeMarkerImpl)highlighter).isStickingToRight();
    treeFor(marker).addInterval(marker, ex.getStartOffset(), ex.getEndOffset(),
                                      ex.isGreedyToLeft(), ex.isGreedyToRight(), isStickingToRight, ex.getLayer());
    myListeners.forEach(l -> l.errorMarkerChanged(new ErrorStripeEvent(myEditor, null, highlighter)));
  }

  private void removeErrorStripeMarker(ErrorStripeMarkerImpl errorStripeMarker) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    RangeHighlighterEx highlighter = errorStripeMarker.getHighlighter();
    treeFor(errorStripeMarker).removeInterval(errorStripeMarker);
    myListeners.forEach(l -> l.errorMarkerChanged(new ErrorStripeEvent(myEditor, null, highlighter)));
  }

  private ErrorStripeMarkerImpl findErrorStripeMarker(@NotNull RangeHighlighterEx highlighter) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    MarkupIterator<ErrorStripeMarkerImpl> iterator = overlappingIterator(highlighter.getStartOffset(), highlighter.getStartOffset());
    try {
      return ContainerUtil.find(iterator, marker -> marker.getHighlighter() == highlighter);
    }
    finally {
      iterator.dispose();
    }
  }

  MarkupIterator<ErrorStripeMarkerImpl> overlappingIterator(int startOffset, int endOffset) {
    startOffset = Math.max(0, startOffset);
    endOffset = Math.max(startOffset, endOffset);

    MarkupIterator<ErrorStripeMarkerImpl> exact = myTree
      .overlappingIterator(new TextRangeInterval(startOffset, endOffset), null);
    MarkupIterator<ErrorStripeMarkerImpl> lines = myTreeForLines
      .overlappingIterator(MarkupModelImpl.roundToLineBoundaries(myEditor.getDocument(), startOffset, endOffset), null);
    return MarkupIterator.mergeIterators(exact, lines, BY_AFFECTED_START_OFFSET);
  }

  private ErrorStripeRangeMarkerTree treeFor(@NotNull ErrorStripeMarkerImpl errorStripeMarker) {
    return errorStripeMarker.getHighlighter().getTargetArea() == HighlighterTargetArea.EXACT_RANGE ? myTree : myTreeForLines;
  }

  void clear() {
    myTree.clear();
    myTreeForLines.clear();
  }

  private static final Comparator<ErrorStripeMarkerImpl> BY_AFFECTED_START_OFFSET =
    Comparator.comparingInt(marker -> marker.getHighlighter().getAffectedAreaStartOffset());
}