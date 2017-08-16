package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.impl.AbstractEditorTest;

public class ForwardBackwardParagraphActionTest extends AbstractEditorTest {
  public void testForwardFromNonEmptyLine() throws Exception {
    doTestForward("ab<caret>c\n" +
                  "\n" +
                  "def\n" +
                  "\n",

                  "abc\n" +
                  "<caret>\n" +
                  "def\n" +
                  "\n");
  }

  public void testForwardFromEmptyLine() throws Exception {
    doTestForward("<caret>\n" +
                  "\n" +
                  "def\n" +
                  "\n",

                  "\n" +
                  "\n" +
                  "def\n" +
                  "<caret>\n");
  }

  public void testForwardWhenNoMoreBlankLines() throws Exception {
    doTestForward("ab<caret>c\n" +
                  "def",

                  "abc\n" +
                  "def<caret>");
  }

  public void testForwardWhenTargetLineContainsSpaces() throws Exception {
    doTestForward("<caret>abc\n" +
                  "   \n",

                  "abc\n" +
                  "<caret>   \n");
  }

  public void testBackwardFromNonEmptyLine() throws Exception {
    doTestBackward("\n" +
                   "abc\n" +
                   "\n" +
                   "de<caret>f",

                   "\n" +
                   "abc\n" +
                   "<caret>\n" +
                   "def");
  }

  public void testBackwardFromEmptyLine() throws Exception {
    doTestBackward("\n" +
                   "abc\n" +
                   "\n" +
                   "<caret>\n",

                   "<caret>\n" +
                   "abc\n" +
                   "\n" +
                   "\n");
  }

  public void testBackwardWhenNoMoreBlankLines() throws Exception {
    doTestBackward("abc\n" +
                   "de<caret>f",

                   "<caret>abc\n" +
                   "def");
  }

  public void testBackwardWhenTargetLineContainsSpaces() throws Exception {
    doTestBackward("  \n" +
                   "abc\n" +
                   "\n" +
                   "<caret>\n",

                   "  \n" +
                   "<caret>abc\n" +
                   "\n" +
                   "\n");
  }

  public void testBackwardWhenPreviousLineContainsSpaces() throws Exception {
    doTestBackward("  \n" +
                   "ab<caret>c",

                   "  \n" +
                   "<caret>abc");
  }

  public void testBackwardAtLineStartWhenPreviousLineContainsSpaces() throws Exception {
    doTestBackward("\n" +
                   "ttt\n" +
                   "  \n" +
                   "<caret>abc",

                   "<caret>\n" +
                   "ttt\n" +
                   "  \n" +
                   "abc");
  }

  private void doTestForward(String initialText, String resultText) {
    initText(initialText);
    executeAction(IdeActions.ACTION_EDITOR_FORWARD_PARAGRAPH);
    checkResultByText(resultText);
  }

  private void doTestBackward(String initialText, String resultText) {
    initText(initialText);
    executeAction(IdeActions.ACTION_EDITOR_BACKWARD_PARAGRAPH);
    checkResultByText(resultText);
  }
}