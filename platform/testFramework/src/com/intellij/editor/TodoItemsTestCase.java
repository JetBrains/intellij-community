// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.editor;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ArrayUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public abstract class TodoItemsTestCase extends LightPlatformCodeInsightTestCase {
  protected abstract String getFileExtension();

  @NotNull
  protected List<HighlightInfo> doHighlighting() {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    TIntArrayList toIgnoreList = new TIntArrayList();

    toIgnoreList.add(Pass.UPDATE_FOLDING);

    toIgnoreList.add(Pass.LOCAL_INSPECTIONS);
    toIgnoreList.add(Pass.WHOLE_FILE_LOCAL_INSPECTIONS);

    int[] toIgnore = toIgnoreList.isEmpty() ? ArrayUtil.EMPTY_INT_ARRAY : toIgnoreList.toNativeArray();
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
    EditorTestUtil.setEditorVisibleSize(myEditor, 1000, 1000); // set visible area for highlighting
    List<TextRange> expectedTodoRanges = extractExpectedTodoRanges(myEditor.getDocument());
    List<HighlightInfo> highlightInfos = doHighlighting();
    List<TextRange> actualTodoRanges = getActualTodoRanges(highlightInfos);
    assertTodoRanges(expectedTodoRanges, actualTodoRanges);
  }

  protected void checkTodos(String text) {
    DocumentImpl document = new DocumentImpl(text);
    List<TextRange> expectedTodoRanges = extractExpectedTodoRanges(document);
    List<HighlightInfo> highlightInfos = doHighlighting();
    checkResultByText(document.getText());
    List<TextRange> actualTodoRanges = getActualTodoRanges(highlightInfos);
    assertTodoRanges(expectedTodoRanges, actualTodoRanges);
  }

  private static List<TextRange> extractExpectedTodoRanges(Document document) {
    ArrayList<TextRange> result = new ArrayList<>();
    int offset = 0;
    int startPos;
    while ((startPos = document.getText().indexOf('[', offset)) != -1) {
      int finalStartPos = startPos;
      WriteCommandAction.runWriteCommandAction(ourProject, () -> document.deleteString(finalStartPos, finalStartPos + 1));
      int endPos = document.getText().indexOf(']', startPos);
      if (endPos == -1) break;
      WriteCommandAction.runWriteCommandAction(ourProject, () -> document.deleteString(endPos, endPos + 1));
      result.add(new TextRange(startPos, endPos));
    }
    return result;
  }

  private static List<TextRange> getActualTodoRanges(List<HighlightInfo> highlightInfos) {
    return highlightInfos.stream()
      .filter(info -> info.type == HighlightInfoType.TODO)
      .map(info -> TextRange.create(info.getHighlighter()))
      .sorted(Segment.BY_START_OFFSET_THEN_END_OFFSET)
      .collect(Collectors.toList());
  }

  private static void assertTodoRanges(List<TextRange> expectedTodoRanges, List<TextRange> actualTodoRanges) {
    assertEquals("Unexpected todos highlighting", generatePresentation(expectedTodoRanges), generatePresentation(actualTodoRanges));
  }

  private static String generatePresentation(List<TextRange> ranges) {
    StringBuilder b = new StringBuilder(myEditor.getDocument().getText());
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
    testTodos("/*\n" +
              " * [TODO first line]\n" +
              " *  [second line]\n" +
              " */");
  }
}
