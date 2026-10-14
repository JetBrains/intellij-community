package com.intellij.openapi.editor.actions;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.impl.EditorModelFixture;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
public class ForwardBackwardParagraphActionTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;


  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
  }

  @Test
  public void testForwardFromNonEmptyLine() {
    myFixture.runTest(() -> {
      doTestForward("""
                      ab<caret>c

                      def

                      """,

                    """
                      abc
                      <caret>
                      def

                      """);
    });
  }

  @Test
  public void testForwardFromEmptyLine() {
    myFixture.runTest(() -> {
      doTestForward("""
                      <caret>

                      def

                      """,

                    """


                      def
                      <caret>
                      """);
    });
  }

  @Test
  public void testForwardWhenNoMoreBlankLines() {
    myFixture.runTest(() -> {
      doTestForward("ab<caret>c\n" +
                    "def",

                    "abc\n" +
                    "def<caret>");
    });
  }

  @Test
  public void testForwardWhenTargetLineContainsSpaces() {
    myFixture.runTest(() -> {
      doTestForward("""
                      <caret>abc
                        \s
                      """,

                    """
                      abc
                      <caret>  \s
                      """);
    });
  }

  @Test
  public void testBackwardFromNonEmptyLine() {
    myFixture.runTest(() -> {
      doTestBackward("""

                       abc

                       de<caret>f""",

                     """

                       abc
                       <caret>
                       def""");
    });
  }

  @Test
  public void testBackwardFromEmptyLine() {
    myFixture.runTest(() -> {
      doTestBackward("""

                       abc

                       <caret>
                       """,

                     """
                       <caret>
                       abc


                       """);
    });
  }

  @Test
  public void testBackwardWhenNoMoreBlankLines() {
    myFixture.runTest(() -> {
      doTestBackward("abc\n" +
                     "de<caret>f",

                     "<caret>abc\n" +
                     "def");
    });
  }

  @Test
  public void testBackwardWhenTargetLineContainsSpaces() {
    myFixture.runTest(() -> {
      doTestBackward("""
                        \s
                       abc

                       <caret>
                       """,

                     """
                        \s
                       <caret>abc


                       """);
    });
  }

  @Test
  public void testBackwardWhenPreviousLineContainsSpaces() {
    myFixture.runTest(() -> {
      doTestBackward("  \n" +
                     "ab<caret>c",

                     "  \n" +
                     "<caret>abc");
    });
  }

  @Test
  public void testBackwardAtLineStartWhenPreviousLineContainsSpaces() {
    myFixture.runTest(() -> {
      doTestBackward("""

                       ttt
                        \s
                       <caret>abc""",

                     """
                       <caret>
                       ttt
                        \s
                       abc""");
    });
  }

  @Test
  public void testForwardWithSelection() {
    myFixture.runTest(() -> {
      doTestForwardWithSelection("""
                                   ab<caret>c

                                   def

                                   """,

                                 """
                                   ab<selection>c
                                   <caret></selection>
                                   def

                                   """);
    });
  }

  @Test
  public void testBackwardWithSelection() {
    myFixture.runTest(() -> {
      doTestBackwardWithSelection("""

                                    abc

                                    de<caret>f""",

                                  """

                                    abc
                                    <selection><caret>
                                    de</selection>f""");
    });
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
    myFixture.initText(initialText);
    myFixture.executeAction(action);
    myFixture.checkResultByText(resultText);
  }
}
