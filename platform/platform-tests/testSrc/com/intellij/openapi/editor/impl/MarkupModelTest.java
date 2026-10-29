// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.ex.MarkupIterator;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.FontFamilies;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.DocumentUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class MarkupModelTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;

  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
  }

  private Document getDocument(@NotNull PsiFile file) {
    return PsiDocumentManager.getInstance(myFixture.getProject()).getDocument(file);
  }

  @Test
  public void testFilteredIteratorsUseHighlighterFlavorFlags() {
    myFixture.runTest(() -> {
      myFixture.initText(" ");
      Document document = getDocument(myFixture.getFile());
      MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(document, myFixture.getProject(), true);
      RangeHighlighter highlighter = markupModel.addRangeHighlighter(0, 1, 0, null, HighlighterTargetArea.EXACT_RANGE);

      assertIteratorEmpty(markupModel.overlappingErrorStripeIterator(0, 1));
      highlighter.setErrorStripeMarkColor(Color.RED);
      assertIteratorContainsOnly(markupModel.overlappingErrorStripeIterator(0, 1), highlighter);
      highlighter.setErrorStripeMarkColor(null);
      assertIteratorEmpty(markupModel.overlappingErrorStripeIterator(0, 1));

      assertIteratorEmpty(markupModel.overlappingGutterIterator(0, 1));
      highlighter.setGutterIconRenderer(MarkupModelStressTest.DUMMY_GUTTER_ICON_RENDERER);
      assertIteratorContainsOnly(markupModel.overlappingGutterIterator(0, 1), highlighter);
      highlighter.setGutterIconRenderer(null);
      assertIteratorEmpty(markupModel.overlappingGutterIterator(0, 1));
    });
  }

  @Test
  public void testFontFamiliesChangeIsFontStyleChange() {
    myFixture.runTest(() -> {
      myFixture.initText("abc");
      Document document = getDocument(myFixture.getFile());
      MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(document, myFixture.getProject(), true);
      RangeHighlighterEx highlighter =
        (RangeHighlighterEx)markupModel.addRangeHighlighter(0, 3, 0, null, HighlighterTargetArea.EXACT_RANGE);
      assertEquals("RangeHighlighterImpl", highlighter.getClass().getSimpleName());
      List<Boolean> fontStyleChanges = new ArrayList<>();
      markupModel.addMarkupModelListener(myFixture.getTestRootDisposable(), new MarkupModelListener() {
        @Override
        public void attributesChanged(@NotNull RangeHighlighterEx changed,
                                      boolean renderersChanged,
                                      boolean fontStyleChanged,
                                      boolean foregroundColorChanged) {
          fontStyleChanges.add(fontStyleChanged);
        }
      });

      highlighter.setTextAttributes(attributes(Color.RED, FontFamilies.of("JetBrains Mono")));
      highlighter.setTextAttributes(attributes(Color.RED, FontFamilies.of("Noto Serif")));
      highlighter.setTextAttributes(attributes(Color.BLUE, FontFamilies.of("Noto Serif")));

      assertEquals(List.of(true, true, false), fontStyleChanges);
    });
  }

  private static TextAttributes attributes(Color background, FontFamilies fontFamilies) {
    TextAttributes attributes = new TextAttributes(null, background, null, null, Font.PLAIN);
    attributes.setFontFamilies(fontFamilies);
    return attributes;
  }

  private static void assertIteratorEmpty(MarkupIterator<RangeHighlighterEx> iterator) {
    try (iterator) {
      assertFalse(iterator.hasNext());
    }
  }

  private static void assertIteratorContainsOnly(MarkupIterator<RangeHighlighterEx> iterator, RangeHighlighter highlighter) {
    try (iterator) {
      assertSame(highlighter, iterator.next());
      assertFalse(iterator.hasNext());
    }
  }

  @Test
  public void testMarkupModelListenersDoWork() {
    myFixture.runTest(() -> {
      ThreadingAssertions.assertEventDispatchThread();
      myFixture.initText(" ".repeat(100));
      Document document = getDocument(myFixture.getFile());
      MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(document, myFixture.getProject(), true);
      List<String> events = new ArrayList<>();
      addMarkupListener(markupModel, events);
      checkEventsFiredSynchronously(markupModel, events);
    });
  }

  private void checkEventsFiredSynchronously(MarkupModelEx markupModel, List<String> events) {
    RangeHighlighter highlighter = markupModel.addRangeHighlighter(1, 2, 0, null, HighlighterTargetArea.EXACT_RANGE);
    List<String> expected = new ArrayList<>();
    assertEventsFired(expected, events, "(1,2): AA");
    highlighter.setCustomRenderer((editor, highlighter1, g) -> {}); assertEventsFired(expected, events, "(1,2): AC");
    highlighter.setEditorFilter(_ -> true); assertEventsFired(expected, events, "(1,2): AC");
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
    markupModel.addMarkupModelListener(myFixture.getTestRootDisposable(), new MarkupModelListener() {
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

  @Test
  public void testMarkupModelListenersMustWorkInBGT() {
    myFixture.runTest(() -> {
      ThreadingAssertions.assertEventDispatchThread();
      myFixture.initText(" ".repeat(100));
      Document document = getDocument(myFixture.getFile());
      List<String> events = ContainerUtil.createLockFreeCopyOnWriteList();
      MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(document, myFixture.getProject(), true);
      addMarkupListener(markupModel, events);
      Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
        ApplicationManager.getApplication().assertIsNonDispatchThread();
        checkEventsFiredSynchronously(markupModel, events);
      });
      while (!future.isDone()) {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      }
      future.get();
    });
  }

  private static void assertEventsFired(List<String> expected, List<String> actualEvents, String... events) {
    expected.addAll(List.of(events));
    assertEquals(expected, actualEvents);
  }

  @Test
  public void testRangeMarkerTreeClearMustFireRemoveEvents() {
    myFixture.runTest(() -> {
      ThreadingAssertions.assertEventDispatchThread();
      myFixture.initText(" ".repeat(100));
      Document document = getDocument(myFixture.getFile());
      MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(document, myFixture.getProject(), true);
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
      assertThat(markupModel.getAllHighlighters()).isEmpty();
    });
  }

  @Test
  public void testMarkupModelListenersFireAfterDocumentChangeLedToRangeHighlighterRemoval() {
    myFixture.runTest(() -> {
      myFixture.initText(" ".repeat(100));
      Document document = getDocument(myFixture.getFile());
      MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(document, myFixture.getProject(), true);
      List<String> events = new ArrayList<>();
      addMarkupListener(markupModel, events);
      RangeHighlighter highlighter = markupModel.addRangeHighlighter(10, 11, 1, null, HighlighterTargetArea.EXACT_RANGE);
      List<String> expected = new ArrayList<>();
      assertEventsFired(expected, events, "(10,11): AA");
      WriteAction.run(() -> document.deleteString(5, 20));
      assertFalse(highlighter.isValid());
      assertEventsFired(expected, events, "(10,11): BR", "(10,11): AR");
    });
  }

  @Test
  public void testMustNotAllowCrazyStuffFromInsideMarkupModelListenerDuringHighlighterRemove() {
    myFixture.runTest(() -> {
      myFixture.initText(" ".repeat(100));
      Document document = getDocument(myFixture.getFile());
      MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(document, myFixture.getProject(), true);
      RangeHighlighter highlighter = markupModel.addRangeHighlighter(10, 11, 1, null, HighlighterTargetArea.EXACT_RANGE);
      RangeHighlighter highlighter2 = markupModel.addRangeHighlighter(10, 11, 1, null, HighlighterTargetArea.EXACT_RANGE);
      AtomicInteger fired = new AtomicInteger();
      markupModel.addMarkupModelListener(myFixture.getTestRootDisposable(), new MarkupModelListener() {
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
    });
  }

  @Test
  public void testOverlappingIteratorMustThrowOnModificationDuringIteration() {
    myFixture.runTest(() -> {
      ThreadingAssertions.assertEventDispatchThread();
      myFixture.initText(" ");
      Document document = getDocument(myFixture.getFile());
      MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(document, myFixture.getProject(), true);

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
    });
  }

  @Test
  public void testHighlightersShiftAfterBulkMultiReplaceAtDescendingOffsets() {
    myFixture.runTest(() -> {
      myFixture.initText("xxxx mmmm yyyy nnnn zzzz tail");
      Document document = getDocument(myFixture.getFile());
      MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(document, myFixture.getProject(), true);
      RangeHighlighter highlighterA = markupModel.addRangeHighlighter(5, 9, 0, null, HighlighterTargetArea.EXACT_RANGE);
      RangeHighlighter highlighterB = markupModel.addRangeHighlighter(15, 19, 0, null, HighlighterTargetArea.EXACT_RANGE);
      RangeHighlighter highlighterC = markupModel.addRangeHighlighter(25, 29, 0, null, HighlighterTargetArea.EXACT_RANGE);

      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () ->
        DocumentUtil.executeInBulk(document, () -> {
          document.replaceString(20, 24, "zzzzTT");
          document.replaceString(10, 14, "yyyyTT");
          document.replaceString(0, 4, "xxxxTT");
        }));

      assertEquals("xxxxTT mmmm yyyyTT nnnn zzzzTT tail", document.getText());
      assertValidHighlighter(highlighterA, 7, 11);
      assertValidHighlighter(highlighterB, 19, 23);
      assertValidHighlighter(highlighterC, 31, 35);
    });
  }

  private static void assertValidHighlighter(@NotNull RangeHighlighter highlighter, int start, int end) {
    assertTrue(highlighter.isValid());
    assertEquals(start, highlighter.getStartOffset());
    assertEquals(end, highlighter.getEndOffset());
  }

}
