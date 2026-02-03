// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeInsight.editor.quotes;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

public class SelectionQuotingTypedHandlerTest extends BasePlatformTestCase {
 /**
   * Performs an action as write action
   *
   * @param project Project
   * @param action  Runnable to be executed
   */
  public static void performAction(final Project project, final Runnable action) {
    ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(project, action, "test command", null));
  }

  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    CodeInsightSettings.runWithTemporarySettings(settings -> {
      settings.SURROUND_SELECTION_ON_QUOTE_TYPED = true;
      super.runTestRunnable(testRunnable);
      return null;
    });
  }

  public void testWOSelection() {
    doTest("\"", "aaa\nbbb\n\n", "\"aaa\nbbb\n\n");
  }

  public void testWithSelection() {
    doTest("\"", "<selection><caret>aaa\n</selection>bbb\n\n", "\"aaa\n\"bbb\n\n");
  }

  public void testWithSingleCharSelection() {
    doTest("\"", "<selection><caret>a</selection>aa\nbbb\n\n", "\"a\"aa\nbbb\n\n");
  }

  public void testWithBacktick() {
    doTest("`", "<selection><caret>a</selection>aa\nbbb\n\n", "`a`aa\nbbb\n\n");
  }

  public void testChangeQuotes() {
    doTest("'", "<selection><caret>\"aaa\"</selection>\nbbb\n\n", "'aaa'\nbbb\n\n");
  }

  public void testChangeBrackets() {
    doTest("[", "<selection><caret>(aaa)</selection>\nbbb\n\n", "[aaa]\nbbb\n\n");
  }

  public void testDontChangeBrackets() {
    doTest("(", "aaa<selection>[foo][bar]<caret></selection>bbb", "aaa([foo][bar])bbb");
  }

  public void testDoubleBrackets() {
    doTest("[", "<selection><caret>[aaa]</selection>\nbbb\n\n", "[[aaa]]\nbbb\n\n");
  }

  public void testChangeNonSimilar() {
    doTest("[", "<selection><caret>\"aaa\"</selection>\nbbb\n\n", "[\"aaa\"]\nbbb\n\n");
  }

  public void testReplaceBracketAndText() {
    doTest("'a", "<selection><caret>\"b\"</selection>\nbbb\n\n", "'a<caret>'\nbbb\n\n");
  }

  public void testTripleEnquote() {
    doTest("\"\"\"", "<selection>text<caret></selection>\nbbb\n\n", "\"\"\"<selection>text</selection>\"\"\"\nbbb\n\n");
  }

  public void testMultipleCarets() {
    doTest("\"",
           "aa<caret>a <selection><caret>bbb</selection> c<selection>c<caret>c</selection>",
           "aa\"<caret>a \"<selection><caret>bbb</selection>\" c\"<selection>cc<caret></selection>\"");
  }

  private void doUpdateQuoteTest(@NotNull String before, @NotNull String expected) {
    String typeChar = before.charAt(0) == '"' ? "'" : "\"";
    StringBuilder selectFirst = new StringBuilder(before).insert(1, "</selection>").insert(0, "<selection><caret>");
    StringBuilder expectedFirst = new StringBuilder(expected).insert(1, "<caret>");
    doTest(typeChar, selectFirst.toString(), expectedFirst.toString());

    StringBuilder selectLast = new StringBuilder(before).insert(before.length() - 1, "<selection><caret>").append("</selection>");
    doTest(typeChar, selectLast.toString(), expected + "<caret>");
  }

  public void testUpdatePairQuote() {
    doUpdateQuoteTest("''", "\"\"");
    doUpdateQuoteTest("'aa'", "\"aa\"");
  }

  public void testUpdatePairQuoteFixEscaping() {
    doUpdateQuoteTest("'aa\\'bb'", "\"aa'bb\"");
    doUpdateQuoteTest("'AA\\'\\'BB\\\\\\'CC\\nDD\"\"EE\\\"FF'", "\"AA''BB\\\\'CC\\nDD\\\"\\\"EE\\\"FF\"");
    doUpdateQuoteTest("\"AA\\\"BB'CC\\'DD\"", "'AA\"BB\\'CC\\'DD'");
  }

  public void testMathExpression() {
    doTest("<",
           "a <selection><caret>></selection>= b",
           "a <<caret>= b");
  }

  public void testMathExpression2() {
    doTest("<",
           "a <selection><caret>>=</selection> b",
           "a <<caret> b");
  }
  private void doTest(@NotNull final String cs, @NotNull String before, @NotNull String expected) {
    myFixture.configureByText(FileTypes.PLAIN_TEXT, before);
    EditorActionManager.getInstance();
    final TypedAction typedAction = TypedAction.getInstance();

    performAction(myFixture.getProject(), () -> {
      for (int i = 0, max = cs.length(); i < max; i++) {
        final char c = cs.charAt(i);
        typedAction.actionPerformed(myFixture.getEditor(), c, ((EditorEx)myFixture.getEditor()).getDataContext());
      }
    });
    myFixture.checkResult(expected);
  }

  public void testRuby7852ErrantEditor() {
    myFixture.configureByText(FileTypes.PLAIN_TEXT, "\"aaa\"\nbbb\n\n");
    myFixture.getEditor().getCaretModel().moveToOffset(0);
    myFixture.getEditor().getSelectionModel().setSelection(0, 5);
    EditorActionManager.getInstance();
    final TypedAction typedAction = TypedAction.getInstance();
    performAction(myFixture.getProject(),
                  () -> typedAction.actionPerformed(myFixture.getEditor(), '\'', ((EditorEx)myFixture.getEditor()).getDataContext()));
    myFixture.getEditor().getSelectionModel().removeSelection();
    myFixture.checkResult("'aaa'\nbbb\n\n");

    myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getLineStartOffset(3));
    performAction(myFixture.getProject(), () -> {
      typedAction.actionPerformed(myFixture.getEditor(), 'A', ((EditorEx)myFixture.getEditor()).getDataContext());
      typedAction.actionPerformed(myFixture.getEditor(), 'B', ((EditorEx)myFixture.getEditor()).getDataContext());
    });
    myFixture.checkResult("'aaa'\nbbb\n\nAB");
  }
}
