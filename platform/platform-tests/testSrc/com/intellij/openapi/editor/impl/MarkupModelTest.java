// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class MarkupModelTest extends AbstractEditorTest {
  public void testMarkupModelListenersDoWork() {
    ThreadingAssertions.assertEventDispatchThread();
    initText(" ".repeat(100));
    Document document = getDocument(getFile());
    MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(document, getProject(), true);
    List<String> events = new ArrayList<>();
    addMarkupListener(markupModel, events);
    checkEventsFiredSynchronously(markupModel, events);
  }

  private void checkEventsFiredSynchronously(MarkupModelEx markupModel, List<String> events) {
    RangeHighlighter highlighter = markupModel.addRangeHighlighter(1, 2, 0, null, HighlighterTargetArea.EXACT_RANGE);
    List<String> expected = new ArrayList<>();
    assertEventsFired(expected, events, "(1,2): AA");
    highlighter.setCustomRenderer((editor, highlighter1, g) -> {}); assertEventsFired(expected, events, "(1,2): AC");
    highlighter.setEditorFilter(__ -> true); assertEventsFired(expected, events, "(1,2): AC");
    highlighter.setErrorStripeMarkColor(new Color(1)); assertEventsFired(expected, events, "(1,2): AC");
    highlighter.setErrorStripeTooltip(this); assertEventsFired(expected, events, "(1,2): AC");
    highlighter.setGutterIconRenderer(MarkupModelStressTest.DUMMY_GUTTER_ICON_RENDERER); assertEventsFired(expected, events, "(1,2): AC");
    highlighter.setLineMarkerRenderer((editor, g, r) -> {}); assertEventsFired(expected, events, "(1,2): AC");
    highlighter.setLineSeparatorColor(new Color(1)); assertEventsFired(expected, events, "(1,2): AC");
    highlighter.setLineSeparatorPlacement(SeparatorPlacement.BOTTOM); assertEventsFired(expected, events, "(1,2): AC");
    highlighter.setLineSeparatorRenderer((g, x1, x2, y) -> {}); assertEventsFired(expected, events, "(1,2): AC");
    highlighter.setTextAttributesKey(CodeInsightColors.LINE_NONE_COVERAGE); assertEventsFired(expected, events, "(1,2): AC");
    highlighter.setThinErrorStripeMark(true); assertEventsFired(expected, events, "(1,2): AC");
    highlighter.dispose();
    assertEventsFired(expected, events,"(1,2): BR", "(1,2): AR");
  }

  private void addMarkupListener(@NotNull MarkupModelEx markupModel, @NotNull List<? super String> events) {
    markupModel.addMarkupModelListener(getTestRootDisposable(), new MarkupModelListener() {
      @Override
      public void afterAdded(@NotNull RangeHighlighterEx highlighter) {
        events.add(highlighter.getTextRange() + ": AA");
      }

      @Override
      public void beforeRemoved(@NotNull RangeHighlighterEx highlighter) {
        events.add(highlighter.getTextRange() + ": BR");
      }

      @Override
      public void afterRemoved(@NotNull RangeHighlighterEx highlighter) {
        events.add(highlighter.getTextRange() + ": AR");
      }

      @Override
      public void attributesChanged(@NotNull RangeHighlighterEx highlighter, boolean renderersChanged, boolean fontStyleOrColorChanged) {
        events.add(highlighter.getTextRange() + ": AC");
      }
    });
  }

  public void testMarkupModelListenersMustWorkInBGT() throws ExecutionException, InterruptedException {
    ThreadingAssertions.assertEventDispatchThread();
    initText(" ".repeat(100));
    Document document = getDocument(getFile());
    List<String> events = ContainerUtil.createLockFreeCopyOnWriteList();
    MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(document, getProject(), true);
    addMarkupListener(markupModel, events);
    Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      ApplicationManager.getApplication().assertIsNonDispatchThread();
      checkEventsFiredSynchronously(markupModel, events);
    });
    while (!future.isDone()) {
      UIUtil.dispatchAllInvocationEvents();
    }
    future.get();
  }

  private void assertEventsFired(List<String> expected, List<String> actualEvents, String... events) {
    expected.addAll(List.of(events));
    assertEquals(expected, actualEvents);
  }

  public void testRangeMarkerTreeClearMustFireRemoveEvents() {
    ThreadingAssertions.assertEventDispatchThread();
    initText(" ".repeat(100));
    Document document = getDocument(getFile());
    MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(document, getProject(), true);
    List<String> events = new ArrayList<>();
    addMarkupListener(markupModel, events);
    List<String> expected = new ArrayList<>();
    RangeHighlighter highlighter = markupModel.addRangeHighlighter(1, 2, 0, null, HighlighterTargetArea.EXACT_RANGE);
    assertEventsFired(expected, events, "(1,2): AA");
    RangeHighlighter highlighter2 = markupModel.addRangeHighlighter(2, 3, 0, null, HighlighterTargetArea.EXACT_RANGE);
    assertEventsFired(expected, events, "(2,3): AA");
    markupModel.removeAllHighlighters();
    assertEventsFired(expected, events, "(1,2): BR", "(1,2): AR", "(2,3): BR", "(2,3): AR");
    assertFalse(highlighter.isValid());
    assertFalse(highlighter2.isValid());
    assertEmpty((markupModel.getAllHighlighters()));
  }

  public void testMarkupModelListenersFireAfterDocumentChangeLedToRangeHighlighterRemoval() {
    initText(" ".repeat(100));
    Document document = getDocument(getFile());
    MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(document, getProject(), true);
    List<String> events = new ArrayList<>();
    addMarkupListener(markupModel, events);
    RangeHighlighter highlighter = markupModel.addRangeHighlighter(10, 11, 1, null, HighlighterTargetArea.EXACT_RANGE);
    List<String> expected = new ArrayList<>();
    assertEventsFired(expected, events, "(10,11): AA");
    WriteAction.run(() -> document.deleteString(5, 20));
    assertFalse(highlighter.isValid());
    assertEventsFired(expected, events, "(10,11): BR", "(10,11): AR");
  }

  public void testMustNotAllowCrazyStuffFromInsideMarkupModelListenerDuringHighlighterRemove() {
    initText(" ".repeat(100));
    Document document = getDocument(getFile());
    MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(document, getProject(), true);
    RangeHighlighter highlighter = markupModel.addRangeHighlighter(10, 11, 1, null, HighlighterTargetArea.EXACT_RANGE);
    RangeHighlighter highlighter2 = markupModel.addRangeHighlighter(10, 11, 1, null, HighlighterTargetArea.EXACT_RANGE);
    AtomicInteger fired = new AtomicInteger();
    markupModel.addMarkupModelListener(getTestRootDisposable(), new MarkupModelListener() {
      @Override
      public void beforeRemoved(@NotNull RangeHighlighterEx highlighter1) {
        fired.incrementAndGet();
        assertSame(highlighter, highlighter1);
        assertTrue(highlighter2.isValid());
        assertThrows(IncorrectOperationException.class, ()->markupModel.addRangeHighlighter(1, 3, 1, null, HighlighterTargetArea.EXACT_RANGE));
        assertThrows(IncorrectOperationException.class, ()->markupModel.removeHighlighter(highlighter2));
      }
    });
    highlighter.dispose();
    assertEquals(1, fired.get());
  }

  public void testOverlappingIteratorMustThrowOnModificationDuringIteration() {
    ThreadingAssertions.assertEventDispatchThread();
    initText(" ");
    Document document = getDocument(getFile());
    MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(document, getProject(), true);

    RangeHighlighter highlighter = markupModel.addRangeHighlighter(0, 1, 1, null, HighlighterTargetArea.EXACT_RANGE);
    try (var it = markupModel.overlappingIterator(0, document.getTextLength())) {
      ContainerUtil.process(it, h->{
        assertThrows(IllegalStateException.class, () -> h.setTextAttributes(TextAttributes.ERASE_MARKER));
        assertThrows(IllegalStateException.class, () -> h.setGreedyToLeft(true));
        assertThrows(IllegalStateException.class, () -> h.dispose());
        assertThrows(IllegalStateException.class, () -> markupModel.addRangeHighlighter(0, 1, 1, null, HighlighterTargetArea.EXACT_RANGE));
        return true;
      });
    }
    finally {
      highlighter.dispose();
    }
  }

}
