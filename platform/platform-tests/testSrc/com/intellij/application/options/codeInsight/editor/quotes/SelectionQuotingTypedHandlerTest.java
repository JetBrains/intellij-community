/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.application.options.codeInsight.editor.quotes;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author oleg
 */
public class SelectionQuotingTypedHandlerTest extends LightPlatformCodeInsightFixtureTestCase {

  private boolean myPrevValue;

 /**
   * Perfoms an action as write action
   *
   * @param project Project
   * @param action  Runnable to be executed
   */
  public static void performAction(final Project project, final Runnable action) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        CommandProcessor.getInstance().executeCommand(project, action, "test command", null);
      }
    });
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myPrevValue = CodeInsightSettings.getInstance().SURROUND_SELECTION_ON_QUOTE_TYPED;
    CodeInsightSettings.getInstance().SURROUND_SELECTION_ON_QUOTE_TYPED = true;
  }

  @Override
  protected void tearDown() throws Exception {
    CodeInsightSettings.getInstance().SURROUND_SELECTION_ON_QUOTE_TYPED = myPrevValue;
    super.tearDown();
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
    doTest("\'", "<selection><caret>\"aaa\"</selection>\nbbb\n\n", "'aaa'\nbbb\n\n");
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
           "aa\"<caret>a \"<selection><caret>bbb</selection>\" c\"<selection><caret>cc</selection>\"");
  }

  private void doTest(@NotNull final String cs, @NotNull String before, @NotNull String expected) {
    myFixture.configureByText(FileTypes.PLAIN_TEXT, before);
    final TypedAction typedAction = EditorActionManager.getInstance().getTypedAction();

    performAction(myFixture.getProject(), new Runnable() {
      @Override
      public void run() {
        for (int i = 0, max = cs.length(); i < max; i++) {
          final char c = cs.charAt(i);
          typedAction.actionPerformed(myFixture.getEditor(), c, ((EditorEx)myFixture.getEditor()).getDataContext());
        }
      }
    });
    myFixture.checkResult(expected);
  }

  public void testRuby7852ErrantEditor() {
    myFixture.configureByText(FileTypes.PLAIN_TEXT, "\"aaa\"\nbbb\n\n");
    myFixture.getEditor().getCaretModel().moveToOffset(0);
    myFixture.getEditor().getSelectionModel().setSelection(0, 5);
    final TypedAction typedAction = EditorActionManager.getInstance().getTypedAction();
    performAction(myFixture.getProject(), new Runnable() {
      @Override
      public void run() {
        typedAction.actionPerformed(myFixture.getEditor(), '\'', ((EditorEx)myFixture.getEditor()).getDataContext());
      }
    });
    myFixture.getEditor().getSelectionModel().removeSelection();
    myFixture.checkResult("'aaa'\nbbb\n\n");

    myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getLineStartOffset(3));
    performAction(myFixture.getProject(), new Runnable() {
      @Override
      public void run() {
        typedAction.actionPerformed(myFixture.getEditor(), 'A', ((EditorEx)myFixture.getEditor()).getDataContext());
        typedAction.actionPerformed(myFixture.getEditor(), 'B', ((EditorEx)myFixture.getEditor()).getDataContext());
      }
    });
    myFixture.checkResult("'aaa'\nbbb\n\nAB");
  }
}
