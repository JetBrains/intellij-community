// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * A mirror of highlighters which should be rendered on the error stripe.
 */
final class ErrorStripeMarkersModel {
  private static final Logger LOG = Logger.getInstance(ErrorStripeMarkersModel.class);

  private final EditorImpl myEditor;
  private final ErrorStripeRangeMarkerTree myTree;
  private final ErrorStripeRangeMarkerTree myTreeForLines;
  private final List<ErrorStripeListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final MarkupModelListener myDocumentMarkupListener = createMarkupListener(true);
  private final MarkupModelListener myEditorMarkupListener = createMarkupListener(false);

  private Disposable myActiveDisposable;

  ErrorStripeMarkersModel(@NotNull EditorImpl editor, @NotNull Disposable parentDisposable) {
    myEditor = editor;
    myTree = new ErrorStripeRangeMarkerTree(myEditor.getDocument());
    myTreeForLines = new ErrorStripeRangeMarkerTree(myEditor.getDocument());

    Disposer.register(parentDisposable, () -> {
      DocumentEx document = myEditor.getDocument();
      myTree.dispose(document);
      myTreeForLines.dispose(document);
      setActive(false);
    });
  }

  void setActive(boolean value) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (value == (myActiveDisposable != null)) return;

    if (value) {
      myActiveDisposable = Disposer.newDisposable("ErrorStripeMarkersModel");
      myEditor.getFilteredDocumentMarkupModel().addMarkupModelListener(myActiveDisposable, myDocumentMarkupListener);
      myEditor.getMarkupModel().addMarkupModelListener(myActiveDisposable, myEditorMarkupListener);
      rebuild();
    }
    else {
      Disposer.dispose(myActiveDisposable);
      myActiveDisposable = null;
      clear();
    }
  }

  void rebuild() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (myActiveDisposable == null) return;

    clear();

    int textLength = myEditor.getDocument().getTextLength();
    myEditor.getMarkupModel().processRangeHighlightersOverlappingWith(
      0, textLength, ex -> {
        afterAdded(ex, false);
        return true;
      });
    ((EditorFilteringMarkupModelEx)myEditor.getFilteredDocumentMarkupModel()).getDelegate().processRangeHighlightersOverlappingWith(
      0, textLength, ex -> {
        afterAdded(ex, true);
        return true;
      });
  }

  void addErrorMarkerListener(ErrorStripeListener listener, Disposable parent) {
    ContainerUtil.add(listener, myListeners, parent);
  }

  void fireErrorMarkerClicked(RangeHighlighter highlighter, MouseEvent e) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ErrorStripeEvent event = new ErrorStripeEvent(myEditor, e, highlighter);
    myListeners.forEach(listener -> listener.errorMarkerClicked(event));
  }

  private MarkupModelListener createMarkupListener(boolean documentMarkupModel) {
    return new MarkupModelListener() {
      @Override
      public void afterAdded(@NotNull RangeHighlighterEx highlighter) {
        ErrorStripeMarkersModel.this.afterAdded(highlighter, documentMarkupModel);
      }

      @Override
      public void beforeRemoved(@NotNull RangeHighlighterEx highlighter) {
        ErrorStripeMarkersModel.this.beforeRemoved(highlighter, documentMarkupModel);
      }

      @Override
      public void attributesChanged(@NotNull RangeHighlighterEx highlighter, boolean renderersChanged, boolean fontStyleOrColorChanged) {
        ErrorStripeMarkersModel.this.attributesChanged(highlighter, documentMarkupModel);
      }
    };
  }

  private void afterAdded(@NotNull RangeHighlighterEx highlighter, boolean documentMarkupModel) {
    if (isAvailable(highlighter, documentMarkupModel)) {
      createErrorStripeMarker(highlighter);
    }
  }

  private void beforeRemoved(@NotNull RangeHighlighterEx highlighter, boolean documentMarkupModel) {
    ErrorStripeMarkerImpl errorStripeMarker = findErrorStripeMarker(highlighter, false);
    if (errorStripeMarker != null) {
      removeErrorStripeMarker(errorStripeMarker);
    }
    else if (isAvailable(highlighter, documentMarkupModel)) {
      errorStripeMarker = findErrorStripeMarker(highlighter, true);
      if (errorStripeMarker == null) {
        LOG.error("Missing " + highlighter);
      }
      else {
        LOG.error("Full scan performed for " + highlighter);
        removeErrorStripeMarker(errorStripeMarker);
      }
    }
  }

  public void attributesChanged(@NotNull RangeHighlighterEx highlighter, boolean documentMarkupModel) {
    ErrorStripeMarkerImpl existingErrorStripeMarker = findErrorStripeMarker(highlighter, false);
    boolean hasErrorStripe = isAvailable(highlighter, documentMarkupModel);

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

  private boolean isAvailable(@NotNull RangeHighlighterEx highlighter, boolean documentMarkupModel) {
    if (documentMarkupModel) {
      if (!highlighter.getEditorFilter().avaliableIn(myEditor) || !myEditor.isHighlighterAvailable(highlighter)) return false;
    }
    return highlighter.getErrorStripeMarkColor(myEditor.getColorsScheme()) != null;
  }

  private void createErrorStripeMarker(@NotNull RangeHighlighterEx h) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ErrorStripeMarkerImpl marker = new ErrorStripeMarkerImpl(myEditor.getDocument(), h);
    treeFor(h).addInterval(marker, h.getStartOffset(), h.getEndOffset(), h.isGreedyToLeft(), h.isGreedyToRight(),
                           (h instanceof RangeMarkerImpl) && ((RangeMarkerImpl)h).isStickingToRight(), h.getLayer());
    myListeners.forEach(l -> l.errorMarkerChanged(new ErrorStripeEvent(myEditor, null, h)));
  }

  private void removeErrorStripeMarker(ErrorStripeMarkerImpl errorStripeMarker) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    RangeHighlighterEx highlighter = errorStripeMarker.getHighlighter();
    treeFor(highlighter).removeInterval(errorStripeMarker);
    myListeners.forEach(l -> l.errorMarkerChanged(new ErrorStripeEvent(myEditor, null, highlighter)));
  }

  private ErrorStripeMarkerImpl findErrorStripeMarker(@NotNull RangeHighlighterEx highlighter, boolean lookEverywhere) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    int offset = highlighter.getStartOffset();
    MarkupIterator<ErrorStripeMarkerImpl> iterator = treeFor(highlighter).overlappingIterator(
      new ProperTextRange(lookEverywhere ? 0 : offset, lookEverywhere ? myEditor.getDocument().getTextLength() : offset), null);
    try {
      return ContainerUtil.find(iterator, marker -> marker.getHighlighter() == highlighter);
    }
    finally {
      iterator.dispose();
    }
  }

  MarkupIterator<RangeHighlighterEx> highlighterIterator(int startOffset, int endOffset) {
    return new HighlighterIterator(startOffset, endOffset);
  }

  private ErrorStripeRangeMarkerTree treeFor(@NotNull RangeHighlighter highlighter) {
    return highlighter.getTargetArea() == HighlighterTargetArea.EXACT_RANGE ? myTree : myTreeForLines;
  }

  private void clear() {
    myTree.clear();
    myTreeForLines.clear();
  }

  private static final Comparator<ErrorStripeMarkerImpl> BY_AFFECTED_START_OFFSET =
    Comparator.comparingInt(marker -> {
      RangeHighlighterEx highlighter = marker.getHighlighter();
      return highlighter.isValid() ? highlighter.getAffectedAreaStartOffset() : -1;
    });

  private class HighlighterIterator implements MarkupIterator<RangeHighlighterEx> {
    private final MarkupIterator<ErrorStripeMarkerImpl> myDelegate;
    private final List<ErrorStripeMarkerImpl> myToRemove = new ArrayList<>();
    private RangeHighlighterEx myNext;

    private HighlighterIterator(int startOffset, int endOffset) {
      startOffset = Math.max(0, startOffset);
      endOffset = Math.max(startOffset, endOffset);

      MarkupIterator<ErrorStripeMarkerImpl> exact = myTree
        .overlappingIterator(new ProperTextRange(startOffset, endOffset), null);
      MarkupIterator<ErrorStripeMarkerImpl> lines = myTreeForLines
        .overlappingIterator(MarkupModelImpl.roundToLineBoundaries(myEditor.getDocument(), startOffset, endOffset), null);
      myDelegate = MarkupIterator.mergeIterators(exact, lines, BY_AFFECTED_START_OFFSET);

      advance();
    }

    @Override
    public void dispose() {
      myDelegate.dispose();
      myToRemove.forEach(m -> treeFor(m.getHighlighter()).removeInterval(m));
    }

    @Override
    public RangeHighlighterEx peek() throws NoSuchElementException {
      return myNext;
    }

    @Override
    public boolean hasNext() {
      return myNext != null;
    }

    @Override
    public RangeHighlighterEx next() {
      RangeHighlighterEx result = myNext;
      advance();
      return result;
    }

    private void advance() {
      while (myDelegate.hasNext()) {
        ErrorStripeMarkerImpl next = myDelegate.next();
        RangeHighlighterEx highlighter = next.getHighlighter();
        if (highlighter.isValid()) {
          myNext = highlighter;
          return;
        }
        else {
          LOG.error("Dangling highlighter found: " + highlighter + " (" + next + ")");
          myToRemove.add(next);
        }
      }
      myNext = null;
    }
  }
}