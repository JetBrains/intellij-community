// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.editor;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.ide.todo.TodoIndexPatternProvider;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.cache.TodoCacheManager;
import com.intellij.psi.impl.cache.impl.todo.TodoIndex;
import com.intellij.psi.impl.cache.impl.todo.TodoIndexEntry;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoAttributes;
import com.intellij.psi.search.TodoPattern;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexEx;
import com.intellij.util.indexing.StorageException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class TodoItemsTestCase extends LightPlatformCodeInsightTestCase {
  protected abstract String getFileExtension();

  protected void doWithPattern(@NotNull String pattern, @NotNull Runnable runnable) {
    TodoConfiguration todoConfiguration = TodoConfiguration.getInstance();
    TodoPattern[] originalPatterns = todoConfiguration.getTodoPatterns();
    try {
      TodoPattern todoPattern = new TodoPattern(pattern, new TodoAttributes(new TextAttributes()), false);
      todoConfiguration.setTodoPatterns(new TodoPattern[]{todoPattern});
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
      runnable.run();
    }
    finally {
      todoConfiguration.setTodoPatterns(originalPatterns);
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    }
  }

  @NotNull
  protected List<HighlightInfo> doHighlighting() {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    int[] toIgnore = {Pass.UPDATE_FOLDING, Pass.LOCAL_INSPECTIONS};
    Editor editor = getEditor();
    PsiFile file = getFile();
    if (editor instanceof EditorWindow) {
      editor = ((EditorWindow)editor).getDelegate();
      file = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
    }
    return CodeInsightTestFixtureImpl.instantiateAndRun(file, editor, toIgnore, false);
  }

  protected void testTodos(String text) {
    configureFromFileText("Foo." + getFileExtension(), text);
    EditorTestUtil.setEditorVisibleSize(getEditor(), 1000, 1000); // set visible area for highlighting
    List<TextRange> expectedTodoRanges = extractExpectedTodoRanges(getEditor().getDocument());
    List<HighlightInfo> highlightInfos = doHighlighting();
    List<TextRange> actualTodoRanges = getActualTodoRanges(highlightInfos);
    assertTodoRanges(expectedTodoRanges, actualTodoRanges);
    assertSameTodoCountInIndexAndHighlighting();
  }

  private void assertSameTodoCountInIndexAndHighlighting() {
    int todosInIndex = TodoCacheManager.getInstance(getProject()).getTodoCount(getVFile(), TodoIndexPatternProvider.getInstance());
    int todosInHighlighting = PsiTodoSearchHelper.getInstance(getProject()).findTodoItems(getFile()).length;
    assertEquals("Mismatch between todos in index and highlighting", todosInIndex, todosInHighlighting);
  }

  protected void checkTodos(String text) {
    DocumentImpl document = new DocumentImpl(text);
    List<TextRange> expectedTodoRanges = extractExpectedTodoRanges(document);
    List<HighlightInfo> highlightInfos = doHighlighting();
    checkResultByText(document.getText());
    List<TextRange> actualTodoRanges = getActualTodoRanges(highlightInfos);
    assertTodoRanges(expectedTodoRanges, actualTodoRanges);
  }

  private List<TextRange> extractExpectedTodoRanges(Document document) {
    ArrayList<TextRange> result = new ArrayList<>();
    int offset = 0;
    int startPos;
    while ((startPos = document.getText().indexOf('[', offset)) != -1) {
      int finalStartPos = startPos;
      WriteCommandAction.runWriteCommandAction(getProject(), () -> document.deleteString(finalStartPos, finalStartPos + 1));
      int endPos = document.getText().indexOf(']', startPos);
      if (endPos == -1) break;
      WriteCommandAction.runWriteCommandAction(getProject(), () -> document.deleteString(endPos, endPos + 1));
      result.add(new TextRange(startPos, endPos));
    }
    return result;
  }

  private static List<TextRange> getActualTodoRanges(List<? extends HighlightInfo> highlightInfos) {
    return highlightInfos.stream()
      .filter(info -> info.type == HighlightInfoType.TODO)
      .map(info -> info.getHighlighter().getTextRange())
      .sorted(Segment.BY_START_OFFSET_THEN_END_OFFSET)
      .collect(Collectors.toList());
  }

  private void assertTodoRanges(List<? extends TextRange> expectedTodoRanges, List<? extends TextRange> actualTodoRanges) {
    assertEquals("Unexpected todos highlighting", generatePresentation(expectedTodoRanges), generatePresentation(actualTodoRanges));
  }

  private String generatePresentation(List<? extends TextRange> ranges) {
    StringBuilder b = new StringBuilder(getEditor().getDocument().getText());
    int prevStart = Integer.MAX_VALUE;
    for (int i = ranges.size() - 1; i >= 0; i--) {
      TextRange r = ranges.get(i);
      assertTrue(r.getEndOffset() <= prevStart);
      b.insert(r.getEndOffset(), ']');
      b.insert(prevStart = r.getStartOffset(), '[');
    }
    return b.toString();
  }

  protected abstract boolean supportsCStyleSingleLineComments();
  protected abstract boolean supportsCStyleMultiLineComments();

  public void testSuccessiveLineComments() {
    if (!supportsCStyleSingleLineComments()) return;
    testTodos("// [TODO first line]\n" +
              "//      [second line]");
  }

  public void testSuccessiveLineCommentsAfterEditing() {
    if (!supportsCStyleSingleLineComments()) return;
    testTodos("// [TODO first line]\n" +
              "// <caret>second line");
    type("     ");
    checkTodos("// [TODO first line]\n" +
               "//      [second line]");
  }

  public void testNewTodoAfterUnsavedEditing() {
    if (!supportsCStyleSingleLineComments()) return;
    testTodos("// [TODO first]\nwords\n<caret>");
    Document document = getEditor().getDocument();
    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    PsiTodoSearchHelper todoSearchHelper = PsiTodoSearchHelper.getInstance(getProject());
    assertTodoCountInIndexStorage(0);
    documentManager.saveDocument(document);
    assertFalse("saved doc expected", documentManager.isDocumentUnsaved(document));
    assertEquals(1, todoSearchHelper.getTodoItemsCount(getFile()));
    assertSameTodoCountInIndexAndHighlighting();
    assertTodoCountInIndexStorage(1);
    type("// TODO second");
    assertTrue("unsaved doc expected", documentManager.isDocumentUnsaved(document));
    assertEquals(2, todoSearchHelper.getTodoItemsCount(getFile()));
    checkTodos("""
                 // [TODO first]
                 words
                 // [TODO second]""");
    assertSameTodoCountInIndexAndHighlighting();
    assertTodoCountInIndexStorage(1);
  }

  private void assertTodoCountInIndexStorage(int count) {
    try {
      Map<TodoIndexEntry, Integer> map =
        ContainerUtil.getFirstItem(((FileBasedIndexEx)FileBasedIndex.getInstance()).getIndex(TodoIndex.NAME)
                                     .getIndexedFileData(FileBasedIndex.getFileId(getVFile())).values());
      int result = map == null ? 0 : map.values().stream().mapToInt(o -> o).sum();
      assertEquals(count, result);
    }
    catch (StorageException e) {
      throw new RuntimeException(e);
    }
  }

  public void testAllLinesLoseHighlightingWithFirstLine() {
    if (!supportsCStyleSingleLineComments()) return;
    testTodos("// [TO<caret>DO first line]\n" +
              "//      [second line]");
    delete();
    checkTodos("// TOO first line\n" +
               "//      second line");
  }

  public void testContinuationIsNotOverlappedWithFollowingTodo() {
    if (!supportsCStyleSingleLineComments()) return;
    testTodos("// [TODO first line]\n" +
              "//  [TODO second line]");
  }

  public void testContinuationInBlockCommentWithStars() {
    if (!supportsCStyleMultiLineComments()) return;
    testTodos("""
                /*
                 * [TODO first line]
                 *  [second line]
                 */""");
  }
}
