// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EdtInvocationManager;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A mirror of highlighters which should be rendered on the error stripe.
 */
final class ErrorStripeMarkersModel {
  private final @NotNull EditorImpl myEditor;
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
    ThreadingAssertions.assertEventDispatchThread();

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
    ThreadingAssertions.assertEventDispatchThread();

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
    ThreadingAssertions.assertEventDispatchThread();
    ErrorStripeEvent event = new ErrorStripeEvent(myEditor, e, highlighter);
    logMarkerClicked(event);
    myListeners.forEach(listener -> listener.errorMarkerClicked(event));
  }

  private MarkupModelListener createMarkupListener(boolean documentMarkupModel) {
    return new MarkupModelListener() {
      final Queue<RangeHighlighterEx> toRemove = new ConcurrentLinkedDeque<>();
      @Override
      public void afterAdded(@NotNull RangeHighlighterEx highlighter) {
        ErrorStripeMarkersModel.this.afterAdded(highlighter, documentMarkupModel);
      }

      @Override
      public void beforeRemoved(@NotNull RangeHighlighterEx highlighter) {
        toRemove.add(highlighter);
      }

      @Override
      public void afterRemoved(@NotNull RangeHighlighterEx highlighter) {
        while (true) {
          RangeHighlighterEx marker = toRemove.poll();
          if (marker == null) break;
          ErrorStripeMarkerImpl errorStripeMarker = errorStripeForRemovedMarker(highlighter, documentMarkupModel);
          if (errorStripeMarker != null) {
            removeErrorStripeMarker(errorStripeMarker);
          }
        }
      }

      @Override
      public void attributesChanged(@NotNull RangeHighlighterEx highlighter, boolean renderersChanged, boolean fontStyleOrColorChanged) {
        ErrorStripeMarkersModel.this.attributesChanged(highlighter, documentMarkupModel);
      }
    };
  }

  private void afterAdded(@NotNull RangeHighlighterEx highlighter, boolean documentMarkupModel) {
    if (isErrorStripeHighlighter(highlighter, documentMarkupModel, myEditor)) {
      createErrorStripeMarker(highlighter);
    }
  }

  private ErrorStripeMarkerImpl errorStripeForRemovedMarker(@NotNull RangeHighlighterEx originalHighlighter, boolean documentMarkupModel) {
    ErrorStripeMarkerImpl errorStripeMarker = findErrorStripeMarker(originalHighlighter, false);
    if (errorStripeMarker == null && isErrorStripeHighlighter(originalHighlighter, documentMarkupModel, myEditor)) {
      errorStripeMarker = findErrorStripeMarker(originalHighlighter, true);
    }
    return errorStripeMarker;
  }

  void attributesChanged(@NotNull RangeHighlighterEx highlighter, boolean documentMarkupModel) {
    ErrorStripeMarkerImpl existingErrorStripeMarker = findErrorStripeMarker(highlighter, false);
    boolean hasErrorStripe = isErrorStripeHighlighter(highlighter, documentMarkupModel, myEditor);

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
    EdtInvocationManager.invokeLaterIfNeeded(() -> {
      if (!myEditor.isDisposed()) {
        myListeners.forEach(l -> l.errorMarkerChanged(new ErrorStripeEvent(myEditor, null, highlighter)));
      }
    });
  }

  static boolean isErrorStripeHighlighter(@NotNull RangeHighlighterEx highlighter, boolean documentMarkupModel, @NotNull EditorImpl editor) {
    if (documentMarkupModel) {
      if (!highlighter.getEditorFilter().avaliableIn(editor) || !editor.isHighlighterAvailable(highlighter)) return false;
    }
    return highlighter.getErrorStripeMarkColor(editor.getColorsScheme()) != null;
  }

  private void createErrorStripeMarker(@NotNull RangeHighlighterEx h) {
    ErrorStripeMarkerImpl marker = new ErrorStripeMarkerImpl(myEditor.getDocument(), h);
    treeFor(h).addInterval(marker, h.getStartOffset(), h.getEndOffset(), h.isGreedyToLeft(), h.isGreedyToRight(),
                           (h instanceof RangeMarkerImpl) && ((RangeMarkerImpl)h).isStickingToRight(), h.getLayer());
    EdtInvocationManager.invokeLaterIfNeeded(() -> {
      if (!myEditor.isDisposed()) {
        myListeners.forEach(l -> l.errorMarkerChanged(new ErrorStripeEvent(myEditor, null, h)));
      }
    });
  }

  private void removeErrorStripeMarker(@NotNull ErrorStripeMarkerImpl errorStripeMarker) {
    RangeHighlighterEx highlighter = errorStripeMarker.getHighlighter();
    treeFor(highlighter).removeInterval(errorStripeMarker);
    EdtInvocationManager.invokeLaterIfNeeded(() -> {
      if (!myEditor.isDisposed()) {
        myListeners.forEach(l -> l.errorMarkerChanged(new ErrorStripeEvent(myEditor, null, highlighter)));
      }
    });
  }

  private ErrorStripeMarkerImpl findErrorStripeMarker(@NotNull RangeHighlighterEx highlighter, boolean lookEverywhere) {
    TextRange range = lookEverywhere ? new ProperTextRange(0, myEditor.getDocument().getTextLength()) : highlighter.getTextRange();
    MarkupIterator<ErrorStripeMarkerImpl> iterator = treeFor(highlighter).overlappingIterator(range);
    try {
      return ContainerUtil.find(iterator, marker -> marker.getHighlighter() == highlighter);
    }
    finally {
      iterator.dispose();
    }
  }

  @NotNull
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

  private void logMarkerClicked(@NotNull ErrorStripeEvent event) {
    Project project = event.getEditor().getProject();
    if (project != null) {
      HighlightInfo info = HighlightInfo.fromRangeHighlighter(event.getHighlighter());
      int severity = info != null ? info.getSeverity().myVal : -1;
      int totalMarkersInFile = countStripeMarkers(myTree) + countStripeMarkers(myTreeForLines);
      VirtualFile vFile = event.getEditor().getVirtualFile();
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        FileType fileType = vFile != null && vFile.isValid() ? vFile.getFileType() : null;
        UIEventLogger.ErrorStripeNavigate.log(project, severity, totalMarkersInFile, fileType);
      });
    }
  }

  private int countStripeMarkers(@NotNull ErrorStripeRangeMarkerTree tree) {
    AtomicInteger counter = new AtomicInteger();
    tree.processAll(marker -> {
      if (isErrorStripeHighlighter(marker.getHighlighter(), true, myEditor)) {
        counter.incrementAndGet();
      }
      return true;
    });
    return counter.get();
  }

  private final class HighlighterIterator implements MarkupIterator<RangeHighlighterEx> {
    private final MarkupIterator<ErrorStripeMarkerImpl> myDelegate;
    private final List<ErrorStripeMarkerImpl> myToRemove = new ArrayList<>();
    private RangeHighlighterEx myNext;

    private HighlighterIterator(int startOffset, int endOffset) {
      startOffset = Math.max(0, startOffset);
      endOffset = Math.max(startOffset, endOffset);

      MarkupIterator<ErrorStripeMarkerImpl> exact = myTree
        .overlappingIterator(new ProperTextRange(startOffset, endOffset));
      MarkupIterator<ErrorStripeMarkerImpl> lines = myTreeForLines
        .overlappingIterator(MarkupModelImpl.roundToLineBoundaries(myEditor.getDocument(), startOffset, endOffset));
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
          myToRemove.add(next);
        }
      }
      myNext = null;
    }
  }
}