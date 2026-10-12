// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.editor;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.ide.todo.TodoIndexPatternProvider;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.cache.TodoCacheManager;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoAttributes;
import com.intellij.psi.search.TodoPattern;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexEx;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.StorageException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TODO highlighting checks for a JUnit 5 test.
 * This is the replacement of {@link TodoItemsTestCase}.
 * The {@code doTest*} methods hold the checks of the {@code test*} methods of {@link TodoItemsTestCase}.
 */
@TestOnly
public final class TodoItemsTestUtil {
  private TodoItemsTestUtil() {
  }

  /**
   * Sets {@code pattern} as the only TODO pattern, runs {@code runnable}, and then restores the old TODO patterns.
   * Call it from any thread. The {@code runnable} runs on the caller thread.
   */
  public static void doWithPattern(@NotNull String pattern, @NotNull Runnable runnable) {
    TodoConfiguration todoConfiguration = TodoConfiguration.getInstance();
    TodoPattern[] originalPatterns = todoConfiguration.getTodoPatterns();
    try {
      TodoPattern todoPattern = new TodoPattern(pattern, new TodoAttributes(new TextAttributes()), false);
      setTodoPatterns(new TodoPattern[]{todoPattern});
      runnable.run();
    }
    finally {
      setTodoPatterns(originalPatterns);
    }
  }

  /**
   * Sets {@code patterns} as the TODO patterns and dispatches the events that the change causes.
   * Call it from any thread.
   */
  public static void setTodoPatterns(TodoPattern @NotNull [] patterns) {
    EdtTestUtil.runInEdtAndWait(() -> {
      TodoConfiguration.getInstance().setTodoPatterns(patterns);
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    });
  }

  /**
   * Runs the highlighting passes on the file of {@code fixture}, without folding and local inspections.
   * When the editor of {@code fixture} is an injected editor, it highlights the host file.
   * Call it from the EDT.
   */
  public static @NotNull @Unmodifiable List<HighlightInfo> doHighlighting(@NotNull CodeInsightTestFixture fixture) {
    PsiDocumentManager.getInstance(fixture.getProject()).commitAllDocuments();

    int[] toIgnore = {Pass.UPDATE_FOLDING, Pass.LOCAL_INSPECTIONS};
    Editor editor = fixture.getEditor();
    PsiFile file = fixture.getFile();
    if (editor instanceof EditorWindow) {
      editor = ((EditorWindow)editor).getDelegate();
      file = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
    }
    return CodeInsightTestFixtureImpl.instantiateAndRun(file, editor, toIgnore, false);
  }

  /**
   * Configures {@code Foo.<fileExtension>} with {@code text}, and checks the TODO highlighting.
   * The square brackets in {@code text} mark the expected TODO ranges. The method removes them from the file text.
   * It also checks that the TODO index and the TODO highlighting find the same number of TODO items.
   * Call it from any thread.
   */
  public static void testTodos(@NotNull CodeInsightTestFixture fixture, @NotNull String fileExtension, @NotNull String text) {
    fixture.configureByText("Foo." + fileExtension, text);
    Editor editor = fixture.getEditor();
    // set visible area for highlighting
    EdtTestUtil.runInEdtAndWait(() -> EditorTestUtil.setEditorVisibleSize(editor, 1000, 1000));
    List<TextRange> expectedTodoRanges = extractExpectedTodoRanges(fixture.getProject(), editor.getDocument());
    List<TextRange> actualTodoRanges = EdtTestUtil.runInEdtAndGet(() -> getActualTodoRanges(doHighlighting(fixture)));
    assertTodoRanges(fixture, expectedTodoRanges, actualTodoRanges);
    assertSameTodoCountInIndexAndHighlighting(fixture);
  }

  /**
   * Checks the text and the TODO highlighting of the file of {@code fixture}.
   * The square brackets in {@code text} mark the expected TODO ranges.
   * Call it from any thread.
   */
  public static void checkTodos(@NotNull CodeInsightTestFixture fixture, @NotNull String text) {
    DocumentImpl document = new DocumentImpl(text);
    List<TextRange> expectedTodoRanges = extractExpectedTodoRanges(fixture.getProject(), document);
    List<TextRange> actualTodoRanges = EdtTestUtil.runInEdtAndGet(() -> getActualTodoRanges(doHighlighting(fixture)));
    fixture.checkResult(document.getText());
    assertTodoRanges(fixture, expectedTodoRanges, actualTodoRanges);
  }

  private static void assertSameTodoCountInIndexAndHighlighting(@NotNull CodeInsightTestFixture fixture) {
    Project project = fixture.getProject();
    int todosInIndex = ReadAction.computeBlocking(
      () -> TodoCacheManager.getInstance(project).getTodoCount(fixture.getFile().getVirtualFile(), TodoIndexPatternProvider.getInstance()));
    int todosInHighlighting = ReadAction.computeBlocking(
      () -> PsiTodoSearchHelper.getInstance(project).findTodoItems(fixture.getFile()).length);
    assertEquals(todosInIndex, todosInHighlighting, "Mismatch between todos in index and highlighting");
  }

  private static List<TextRange> extractExpectedTodoRanges(@NotNull Project project, @NotNull Document document) {
    ArrayList<TextRange> result = new ArrayList<>();
    int offset = 0;
    int startPos;
    while ((startPos = document.getText().indexOf('[', offset)) != -1) {
      int finalStartPos = startPos;
      WriteCommandAction.runWriteCommandAction(project, () -> document.deleteString(finalStartPos, finalStartPos + 1));
      int endPos = document.getText().indexOf(']', startPos);
      if (endPos == -1) break;
      WriteCommandAction.runWriteCommandAction(project, () -> document.deleteString(endPos, endPos + 1));
      result.add(new TextRange(startPos, endPos));
    }
    return result;
  }

  private static List<TextRange> getActualTodoRanges(List<? extends HighlightInfo> highlightInfos) {
    return highlightInfos.stream()
      .filter(info -> info.type == HighlightInfoType.TODO)
      .map(info -> info.getHighlighter().getTextRange())
      .sorted(Segment.BY_START_OFFSET_THEN_END_OFFSET)
      .toList();
  }

  private static void assertTodoRanges(@NotNull CodeInsightTestFixture fixture,
                                       List<? extends TextRange> expectedTodoRanges,
                                       List<? extends TextRange> actualTodoRanges) {
    assertEquals(generatePresentation(fixture, expectedTodoRanges), generatePresentation(fixture, actualTodoRanges),
                 "Unexpected todos highlighting");
  }

  private static String generatePresentation(@NotNull CodeInsightTestFixture fixture, List<? extends TextRange> ranges) {
    StringBuilder b = new StringBuilder(fixture.getEditor().getDocument().getText());
    int prevStart = Integer.MAX_VALUE;
    for (int i = ranges.size() - 1; i >= 0; i--) {
      TextRange r = ranges.get(i);
      assertTrue(r.getEndOffset() <= prevStart);
      b.insert(r.getEndOffset(), ']');
      b.insert(prevStart = r.getStartOffset(), '[');
    }
    return b.toString();
  }

  private static void assertTodoCountInIndexStorage(@NotNull CodeInsightTestFixture fixture, int count) {
    ID<Integer, Map<?, Integer>> todoIndex = ID.findByName("TodoIndex");
    assertNotNull(todoIndex, "TodoIndex is not registered");
    int fileId = FileBasedIndex.getFileId(fixture.getFile().getVirtualFile());
    Map<?, Integer> map = ReadAction.computeBlocking(() -> {
      try {
        return ContainerUtil.getFirstItem(((FileBasedIndexEx)FileBasedIndex.getInstance()).getIndex(todoIndex)
                                            .getIndexedFileData(fileId).values());
      }
      catch (StorageException e) {
        throw new RuntimeException(e);
      }
    });
    int result = map == null ? 0 : map.values().stream().mapToInt(o -> o).sum();
    assertEquals(count, result);
  }

  /**
   * The check of {@code TodoItemsTestCase.testSuccessiveLineComments}.
   * Use it for a language with C-style line comments.
   * Call it from any thread.
   */
  public static void doTestSuccessiveLineComments(@NotNull CodeInsightTestFixture fixture, @NotNull String fileExtension) {
    doTestSuccessiveLineComments(fixture, fileExtension, "");
  }

  /**
   * The same as {@link #doTestSuccessiveLineComments(CodeInsightTestFixture, String)}, but each file text starts with {@code textPrefix}.
   * Call it from any thread.
   */
  public static void doTestSuccessiveLineComments(@NotNull CodeInsightTestFixture fixture,
                                                  @NotNull String fileExtension,
                                                  @NotNull String textPrefix) {
    testTodos(fixture, fileExtension, textPrefix + "// [TODO first line]\n" +
                                                   "//      [second line]");
  }

  /**
   * The check of {@code TodoItemsTestCase.testSuccessiveLineCommentsAfterEditing}.
   * Use it for a language with C-style line comments.
   * Call it from any thread.
   */
  public static void doTestSuccessiveLineCommentsAfterEditing(@NotNull CodeInsightTestFixture fixture, @NotNull String fileExtension) {
    doTestSuccessiveLineCommentsAfterEditing(fixture, fileExtension, "");
  }

  /**
   * The same as {@link #doTestSuccessiveLineCommentsAfterEditing(CodeInsightTestFixture, String)}.
   * Each file text starts with {@code textPrefix}.
   * Call it from any thread.
   */
  public static void doTestSuccessiveLineCommentsAfterEditing(@NotNull CodeInsightTestFixture fixture,
                                                              @NotNull String fileExtension,
                                                              @NotNull String textPrefix) {
    testTodos(fixture, fileExtension, textPrefix + "// [TODO first line]\n" +
                                                   "// <caret>second line");
    fixture.type("     ");
    checkTodos(fixture, textPrefix + "// [TODO first line]\n" +
                                     "//      [second line]");
  }

  /**
   * The check of {@code TodoItemsTestCase.testNewTodoAfterUnsavedEditing}.
   * Use it for a language with C-style line comments.
   * Call it from any thread.
   */
  public static void doTestNewTodoAfterUnsavedEditing(@NotNull CodeInsightTestFixture fixture, @NotNull String fileExtension) {
    doTestNewTodoAfterUnsavedEditing(fixture, fileExtension, "");
  }

  /**
   * The same as {@link #doTestNewTodoAfterUnsavedEditing(CodeInsightTestFixture, String)}.
   * Each file text starts with {@code textPrefix}.
   * Call it from any thread.
   */
  public static void doTestNewTodoAfterUnsavedEditing(@NotNull CodeInsightTestFixture fixture,
                                                      @NotNull String fileExtension,
                                                      @NotNull String textPrefix) {
    testTodos(fixture, fileExtension, textPrefix + "// [TODO first]\nwords\n<caret>");
    Document document = fixture.getEditor().getDocument();
    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    PsiTodoSearchHelper todoSearchHelper = PsiTodoSearchHelper.getInstance(fixture.getProject());
    assertTodoCountInIndexStorage(fixture, 0);
    EdtTestUtil.runInEdtAndWait(() -> documentManager.saveDocument(document));
    assertFalse(documentManager.isDocumentUnsaved(document), "saved doc expected");
    assertEquals(1, (int)ReadAction.computeBlocking(() -> todoSearchHelper.getTodoItemsCount(fixture.getFile())));
    assertSameTodoCountInIndexAndHighlighting(fixture);
    assertTodoCountInIndexStorage(fixture, 1);
    fixture.type("// TODO second");
    assertTrue(documentManager.isDocumentUnsaved(document), "unsaved doc expected");
    assertEquals(2, (int)ReadAction.computeBlocking(() -> todoSearchHelper.getTodoItemsCount(fixture.getFile())));
    checkTodos(fixture, textPrefix + """
      // [TODO first]
      words
      // [TODO second]""");
    assertSameTodoCountInIndexAndHighlighting(fixture);
    assertTodoCountInIndexStorage(fixture, 1);
  }

  /**
   * The check of {@code TodoItemsTestCase.testAllLinesLoseHighlightingWithFirstLine}.
   * Use it for a language with C-style line comments.
   * Call it from any thread.
   */
  public static void doTestAllLinesLoseHighlightingWithFirstLine(@NotNull CodeInsightTestFixture fixture, @NotNull String fileExtension) {
    doTestAllLinesLoseHighlightingWithFirstLine(fixture, fileExtension, "");
  }

  /**
   * The same as {@link #doTestAllLinesLoseHighlightingWithFirstLine(CodeInsightTestFixture, String)}.
   * Each file text starts with {@code textPrefix}.
   * Call it from any thread.
   */
  public static void doTestAllLinesLoseHighlightingWithFirstLine(@NotNull CodeInsightTestFixture fixture,
                                                                 @NotNull String fileExtension,
                                                                 @NotNull String textPrefix) {
    testTodos(fixture, fileExtension, textPrefix + "// [TO<caret>DO first line]\n" +
                                                   "//      [second line]");
    delete(fixture);
    checkTodos(fixture, textPrefix + "// TOO first line\n" +
                                     "//      second line");
  }

  /**
   * The check of {@code TodoItemsTestCase.testContinuationIsNotOverlappedWithFollowingTodo}.
   * Use it for a language with C-style line comments.
   * Call it from any thread.
   */
  public static void doTestContinuationIsNotOverlappedWithFollowingTodo(@NotNull CodeInsightTestFixture fixture,
                                                                        @NotNull String fileExtension) {
    doTestContinuationIsNotOverlappedWithFollowingTodo(fixture, fileExtension, "");
  }

  /**
   * The same as {@link #doTestContinuationIsNotOverlappedWithFollowingTodo(CodeInsightTestFixture, String)}.
   * Each file text starts with {@code textPrefix}.
   * Call it from any thread.
   */
  public static void doTestContinuationIsNotOverlappedWithFollowingTodo(@NotNull CodeInsightTestFixture fixture,
                                                                        @NotNull String fileExtension,
                                                                        @NotNull String textPrefix) {
    testTodos(fixture, fileExtension, textPrefix + "// [TODO first line]\n" +
                                                   "//  [TODO second line]");
  }

  /**
   * The check of {@code TodoItemsTestCase.testContinuationInBlockCommentWithStars}.
   * Use it for a language with C-style block comments.
   * Call it from any thread.
   */
  public static void doTestContinuationInBlockCommentWithStars(@NotNull CodeInsightTestFixture fixture, @NotNull String fileExtension) {
    doTestContinuationInBlockCommentWithStars(fixture, fileExtension, "");
  }

  /**
   * The same as {@link #doTestContinuationInBlockCommentWithStars(CodeInsightTestFixture, String)}.
   * Each file text starts with {@code textPrefix}.
   * Call it from any thread.
   */
  public static void doTestContinuationInBlockCommentWithStars(@NotNull CodeInsightTestFixture fixture,
                                                               @NotNull String fileExtension,
                                                               @NotNull String textPrefix) {
    testTodos(fixture, fileExtension, textPrefix + """
      /*
       * [TODO first line]
       *  [second line]
       */""");
  }

  /**
   * Deletes the character at the caret in the editor of {@code fixture}.
   * Call it from any thread.
   */
  public static void delete(@NotNull CodeInsightTestFixture fixture) {
    fixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE);
  }
}
