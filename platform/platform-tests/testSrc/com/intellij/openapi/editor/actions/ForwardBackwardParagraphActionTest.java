package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.impl.AbstractEditorTest;

public class ForwardBackwardParagraphActionTest extends AbstractEditorTest {
  public void testForwardFromNonEmptyLine() {
    doTestForward("""
                    ab<caret>c

                    def

                    """,

                  """
                    abc
                    <caret>
                    def

                    """);
  }

  public void testForwardFromEmptyLine() {
    doTestForward("""
                    <caret>

                    def

                    """,

                  """


                    def
                    <caret>
                    """);
  }

  public void testForwardWhenNoMoreBlankLines() {
    doTestForward("ab<caret>c\n" +
                  "def",

                  "abc\n" +
                  "def<caret>");
  }

  public void testForwardWhenTargetLineContainsSpaces() {
    doTestForward("""
                    <caret>abc
                      \s
                    """,

                  """
                    abc
                    <caret>  \s
                    """);
  }

  public void testBackwardFromNonEmptyLine() {
    doTestBackward("""

                     abc

                     de<caret>f""",

                   """

                     abc
                     <caret>
                     def""");
  }

  public void testBackwardFromEmptyLine() {
    doTestBackward("""

                     abc

                     <caret>
                     """,

                   """
                     <caret>
                     abc


                     """);
  }

  public void testBackwardWhenNoMoreBlankLines() {
    doTestBackward("abc\n" +
                   "de<caret>f",

                   "<caret>abc\n" +
                   "def");
  }

  public void testBackwardWhenTargetLineContainsSpaces() {
    doTestBackward("""
                      \s
                     abc

                     <caret>
                     """,

                   """
                      \s
                     <caret>abc


                     """);
  }

  public void testBackwardWhenPreviousLineContainsSpaces() {
    doTestBackward("  \n" +
                   "ab<caret>c",

                   "  \n" +
                   "<caret>abc");
  }

  public void testBackwardAtLineStartWhenPreviousLineContainsSpaces() {
    doTestBackward("""

                     ttt
                      \s
                     <caret>abc""",

                   """
                     <caret>
                     ttt
                      \s
                     abc""");
  }

  public void testForwardWithSelection() {
    doTestForwardWithSelection("""
                                 ab<caret>c

                                 def

                                 """,

                               """
                                 ab<selection>c
                                 <caret></selection>
                                 def

                                 """);
  }

  public void testBackwardWithSelection() {
    doTestBackwardWithSelection("""

                                  abc

                                  de<caret>f""",

                                """

                                  abc
                                  <selection><caret>
                                  de</selection>f""");
  }

  private void doTestForward(String initialText, String resultText) {
    doTest(initialText, resultText, IdeActions.ACTION_EDITOR_FORWARD_PARAGRAPH);
  }

  private void doTestBackward(String initialText, String resultText) {
    doTest(initialText, resultText, IdeActions.ACTION_EDITOR_BACKWARD_PARAGRAPH);
  }

  private void doTestForwardWithSelection(String initialText, String resultText) {
    doTest(initialText, resultText, IdeActions.ACTION_EDITOR_FORWARD_PARAGRAPH_WITH_SELECTION);
  }

  private void doTestBackwardWithSelection(String initialText, String resultText) {
    doTest(initialText, resultText, IdeActions.ACTION_EDITOR_BACKWARD_PARAGRAPH_WITH_SELECTION);
  }

  private void doTest(String initialText, String resultText, String action) {
    initText(initialText);
    executeAction(action);
    checkResultByText(resultText);
  }
}