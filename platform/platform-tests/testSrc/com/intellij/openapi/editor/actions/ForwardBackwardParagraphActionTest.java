package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.impl.AbstractEditorTest;

public class ForwardBackwardParagraphActionTest extends AbstractEditorTest {
  public void testForwardFromNonEmptyLine() {
    doTestForward("ab<caret>c\n" +
                  "\n" +
                  "def\n" +
                  "\n",

                  "abc\n" +
                  "<caret>\n" +
                  "def\n" +
                  "\n");
  }

  public void testForwardFromEmptyLine() {
    doTestForward("<caret>\n" +
                  "\n" +
                  "def\n" +
                  "\n",

                  "\n" +
                  "\n" +
                  "def\n" +
                  "<caret>\n");
  }

  public void testForwardWhenNoMoreBlankLines() {
    doTestForward("ab<caret>c\n" +
                  "def",

                  "abc\n" +
                  "def<caret>");
  }

  public void testForwardWhenTargetLineContainsSpaces() {
    doTestForward("<caret>abc\n" +
                  "   \n",

                  "abc\n" +
                  "<caret>   \n");
  }

  public void testBackwardFromNonEmptyLine() {
    doTestBackward("\n" +
                   "abc\n" +
                   "\n" +
                   "de<caret>f",

                   "\n" +
                   "abc\n" +
                   "<caret>\n" +
                   "def");
  }

  public void testBackwardFromEmptyLine() {
    doTestBackward("\n" +
                   "abc\n" +
                   "\n" +
                   "<caret>\n",

                   "<caret>\n" +
                   "abc\n" +
                   "\n" +
                   "\n");
  }

  public void testBackwardWhenNoMoreBlankLines() {
    doTestBackward("abc\n" +
                   "de<caret>f",

                   "<caret>abc\n" +
                   "def");
  }

  public void testBackwardWhenTargetLineContainsSpaces() {
    doTestBackward("  \n" +
                   "abc\n" +
                   "\n" +
                   "<caret>\n",

                   "  \n" +
                   "<caret>abc\n" +
                   "\n" +
                   "\n");
  }

  public void testBackwardWhenPreviousLineContainsSpaces() {
    doTestBackward("  \n" +
                   "ab<caret>c",

                   "  \n" +
                   "<caret>abc");
  }

  public void testBackwardAtLineStartWhenPreviousLineContainsSpaces() {
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