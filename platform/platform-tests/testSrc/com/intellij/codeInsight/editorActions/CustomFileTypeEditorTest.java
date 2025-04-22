// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import junit.framework.TestCase;

import static com.intellij.testFramework.EditorTestUtil.BACKSPACE_FAKE_CHAR;

/**
 * @author Maxim.Mossienko
 */
public class CustomFileTypeEditorTest extends BasePlatformTestCase {

  @Override
  protected String getBasePath() {
    return "platform/platform-tests/testData/editor/actionsInCustomFileType";
  }

  @Override
  protected boolean isCommunity() {
    return true;
  }

  private void _testBlockNavigation(String test, String ext) {
    myFixture.configureByFile(test + "." + ext);
    performEndBlockAction();
    myFixture.checkResultByFile(test + "_after." + ext);

    myFixture.configureByFile(test + "_after." + ext);
    performStartBlockAction();
    myFixture.checkResultByFile(test + "." + ext);
  }

  private void performStartBlockAction() {
    EditorActionHandler actionHandler = new CodeBlockStartAction().getHandler();
    actionHandler.execute(myFixture.getEditor(), myFixture.getEditor().getCaretModel().getPrimaryCaret(),
                          ((EditorEx)myFixture.getEditor()).getDataContext());
  }

  private void performEndBlockAction() {
    EditorActionHandler actionHandler = new CodeBlockEndAction().getHandler();
    actionHandler.execute(myFixture.getEditor(), myFixture.getEditor().getCaretModel().getPrimaryCaret(),
                          ((EditorEx)myFixture.getEditor()).getDataContext());
  }

  public void testBlockNavigation() {
    _testBlockNavigation("blockNavigation","cs");
  }

  public void testInsertDeleteQuotes() {
    myFixture.configureByFile("InsertDeleteQuote.cs");
    EditorTestUtil.performTypingAction(myFixture.getEditor(), '"');
    myFixture.checkResultByFile("InsertDeleteQuote_after.cs");

    myFixture.configureByFile("InsertDeleteQuote_after.cs");
    EditorTestUtil.performTypingAction(myFixture.getEditor(), BACKSPACE_FAKE_CHAR);
    myFixture.checkResultByFile("InsertDeleteQuote.cs");

    FileType extension = FileTypeManager.getInstance().getFileTypeByExtension("pl");
    TestCase.assertTrue("Test is not set up correctly:" + extension, extension instanceof AbstractFileType);
    myFixture.configureByFile("InsertDeleteQuote.pl");
    EditorTestUtil.performTypingAction(myFixture.getEditor(), '"');
    myFixture.checkResultByFile("InsertDeleteQuote_after.pl");

    myFixture.configureByFile("InsertDeleteQuote_after.pl");
    EditorTestUtil.performTypingAction(myFixture.getEditor(), BACKSPACE_FAKE_CHAR);
    myFixture.checkResultByFile("InsertDeleteQuote.pl");

    myFixture.configureByFile("InsertDeleteQuote.aj");
    EditorTestUtil.performTypingAction(myFixture.getEditor(), '"');
    myFixture.checkResultByFile("InsertDeleteQuote_after.aj");

    myFixture.configureByFile("InsertDeleteQuote_after.aj");
    EditorTestUtil.performTypingAction(myFixture.getEditor(), BACKSPACE_FAKE_CHAR);
    myFixture.checkResultByFile("InsertDeleteQuote.aj");
  }

  public void testInsertDeleteBracket() {
    myFixture.configureByFile("InsertDeleteBracket.cs");
    EditorTestUtil.performTypingAction(myFixture.getEditor(), '[');
    myFixture.checkResultByFile("InsertDeleteBracket_after.cs");

    myFixture.configureByFile("InsertDeleteBracket_after.cs");
    EditorTestUtil.performTypingAction(myFixture.getEditor(), BACKSPACE_FAKE_CHAR);
    myFixture.checkResultByFile("InsertDeleteBracket.cs");

    myFixture.configureByFile("InsertDeleteBracket_after.cs");
    EditorTestUtil.performTypingAction(myFixture.getEditor(), ']');
    myFixture.checkResultByFile("InsertDeleteBracket_after2.cs");
  }

  public void testInsertDeleteParenth() {
    myFixture.configureByFile("InsertDeleteParenth.cs");
    EditorTestUtil.performTypingAction(myFixture.getEditor(), '(');
    myFixture.checkResultByFile("InsertDeleteParenth_after.cs");

    myFixture.configureByFile("InsertDeleteParenth_after.cs");
    EditorTestUtil.performTypingAction(myFixture.getEditor(), BACKSPACE_FAKE_CHAR);
    myFixture.checkResultByFile("InsertDeleteParenth.cs");

    myFixture.configureByFile("InsertDeleteParenth_after.cs");
    EditorTestUtil.performTypingAction(myFixture.getEditor(), ')');
    myFixture.checkResultByFile("InsertDeleteParenth_after2.cs");

    myFixture.configureByFile("InsertDeleteParenth2_2.cs");
    EditorTestUtil.performTypingAction(myFixture.getEditor(), '(');
    myFixture.checkResultByFile("InsertDeleteParenth2_2_after.cs");

    myFixture.configureByFile("InsertDeleteParenth2.cs");
    EditorTestUtil.performTypingAction(myFixture.getEditor(), '(');
    myFixture.checkResultByFile("InsertDeleteParenth2_after.cs");
  }

  private void checkTyping(String fileName, String before, char typed, String after) {
    myFixture.configureByText(fileName, before);
    EditorTestUtil.performTypingAction(myFixture.getEditor(), typed);
    myFixture.checkResult(after);
  }

  public void testParenthesesBeforeNonWs() {
    checkTyping("a.cs", "<caret>a", '(', "(<caret>a");
    checkTyping("a.cs", "<caret>@a", '(', "(<caret>@a");
    checkTyping("a.cs", "<caret>(a", '(', "(<caret>(a");
    checkTyping("a.cs", "<caret>[a", '(', "(<caret>[a");
    checkTyping("a.cs", "<caret> (a", '(', "(<caret>) (a");
    checkTyping("a.cs", "(<caret>)", '(', "((<caret>))");
    checkTyping("a.cs", "(<caret>,)", '(', "((<caret>),)");

    checkTyping("a.cs", "<caret>a",   '[', "[<caret>a");
    checkTyping("a.cs", "<caret>@a",  '[', "[<caret>@a");
    checkTyping("a.cs", "<caret>(a",  '[', "[<caret>(a");
    checkTyping("a.cs", "<caret>[a",  '[', "[<caret>[a");
    checkTyping("a.cs", "<caret> (a", '[', "[<caret>] (a");
  }

  public void testQuoteBeforeNonWs() {
    checkTyping("a.cs", "<caret>a", '"', "\"<caret>a");
    checkTyping("a.cs", "<caret> ", '"', "\"<caret>\" ");

    checkTyping("a.cs", "<caret>a", '\'', "'<caret>a");
    checkTyping("a.cs", "<caret> ", '\'', "'<caret>' ");
  }

  public void testReplaceQuoteDontSurroundSelection() {
    CodeInsightSettings.runWithTemporarySettings(settings -> {
      settings.SURROUND_SELECTION_ON_QUOTE_TYPED = false;
      settings.AUTOINSERT_PAIR_QUOTE = false;
      checkTyping("a.cs", "<caret><selection>\"</selection>a\"", '\'', "'<caret>a\"");
      checkTyping("a.cs", "<caret><selection>\"</selection>a'", '\'', "'<caret>a'");
      checkTyping("a.cs", "\"a<caret><selection>\"</selection>", '\'', "\"a'<caret>");
      checkTyping("a.cs", "'a<caret><selection>\"</selection>", '\'', "'a'<caret>");

      checkTyping("a.cs", "<caret><selection>'</selection>a\"", '\"', "\"<caret>a\"");
      checkTyping("a.cs", "<caret><selection>'</selection>a'", '\"', "\"<caret>a'");
      checkTyping("a.cs", "\"a<caret><selection>'</selection>", '\"', "\"a\"<caret>");
      checkTyping("a.cs", "'a<caret><selection>'</selection>", '\"', "'a\"<caret>");
      return null;
    });
  }

  public void testReplaceQuoteDontInsertPair() {
    CodeInsightSettings.runWithTemporarySettings(settings -> {
      settings.AUTOINSERT_PAIR_QUOTE = false;
      checkTyping("a.cs", "<caret><selection>\"</selection>a\"", '\'', "'<selection><caret>\"</selection>'a\"");
      checkTyping("a.cs", "<caret><selection>\"</selection>a'", '\'', "'<selection><caret>\"</selection>'a'");
      checkTyping("a.cs", "\"a<caret><selection>\"</selection>", '\'', "\"a'<selection><caret>\"</selection>'");
      checkTyping("a.cs", "'a<caret><selection>\"</selection>", '\'', "'a'<selection><caret>\"</selection>'");
      checkTyping("a.cs", "<caret><selection>'</selection>a\"", '\"', "\"<selection><caret>'</selection>\"a\"");
      checkTyping("a.cs", "<caret><selection>'</selection>a'", '\"', "\"<selection><caret>'</selection>\"a'");
      checkTyping("a.cs", "\"a<caret><selection>'</selection>", '\"', "\"a\"<selection><caret>'</selection>\"");
      checkTyping("a.cs", "'a<caret><selection>'</selection>", '\"', "'a\"<selection><caret>'</selection>\"");
      return null;
    });
  }

  public void testReplaceQuote() {
    checkTyping("a.cs", "<caret><selection>\"</selection>a\"", '\'', "'<caret>a'");
    checkTyping("a.cs", "<caret><selection>\"</selection>a'", '\'', "'<selection><caret>\"</selection>'a'");
    checkTyping("a.cs", "\"a<caret><selection>\"</selection>", '\'', "'a'<caret>");
    checkTyping("a.cs", "'a<caret><selection>\"</selection>", '\'', "'a'<selection><caret>\"</selection>'");
    checkTyping("a.cs", "<caret><selection>'</selection>a\"", '\"', "\"<selection><caret>'</selection>\"a\"");
    checkTyping("a.cs", "<caret><selection>'</selection>a'", '\"', "\"<caret>a\"");
    checkTyping("a.cs", "\"a<caret><selection>'</selection>", '\"', "\"a\"<selection><caret>'</selection>\"");
    checkTyping("a.cs", "'a<caret><selection>'</selection>", '\"', "\"a\"<caret>");
  }

  public void testNoPairedBracesInPlainText() {
    checkTyping("a.txt", "<caret>", '(', "(<caret>");
    checkTyping("a.txt", "{<caret>}", '}', "{}<caret>}");
  }

  public void testClosingBraceInPlainText() {
    myFixture.configureByText("a.txt", "<caret>");
    EditorTestUtil.performTypingAction(myFixture.getEditor(), '(');
    EditorTestUtil.performTypingAction(myFixture.getEditor(), ')');
    myFixture.checkResult("()<caret>");
  }

  public void testInsertBraceOnEnter() {
    myFixture.configureByFile("InsertBraceOnEnter.cs");
    EditorTestUtil.performTypingAction(myFixture.getEditor(), '\n');
    myFixture.checkResultByFile("InsertBraceOnEnter_after.cs");
  }

  public void testInsertBraceOnEnterJavaFx() {
    String testName = getTestName(false);
    myFixture.configureByFile(testName + ".fx");
    EditorTestUtil.performTypingAction(myFixture.getEditor(), '\n');
    myFixture.checkResultByFile(testName + "_after.fx");
  }

  public void testCpp() {
    EditorHighlighter highlighter = HighlighterFactory.createHighlighter(getProject(), "A.cpp");
    //                   0123456789012345678 9 0123 45 6 7
    highlighter.setText("#include try enum \"\\xff\\z\\\"xxx\"");
    HighlighterIterator iterator = highlighter.createIterator(2);
    TestCase.assertEquals(CustomHighlighterTokenType.KEYWORD_1, iterator.getTokenType());

    iterator = highlighter.createIterator(9);
    TestCase.assertEquals(CustomHighlighterTokenType.KEYWORD_2, iterator.getTokenType());

    iterator = highlighter.createIterator(15);
    TestCase.assertEquals(CustomHighlighterTokenType.KEYWORD_1, iterator.getTokenType());

    iterator = highlighter.createIterator(19);
    TestCase.assertEquals(StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN, iterator.getTokenType());

    iterator = highlighter.createIterator(23);
    TestCase.assertEquals(StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN, iterator.getTokenType());

    iterator = highlighter.createIterator(25);
    TestCase.assertEquals(StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN, iterator.getTokenType());

    iterator = highlighter.createIterator(27);
    TestCase.assertEquals(CustomHighlighterTokenType.STRING, iterator.getTokenType());
  }

  public void testHaskel() {
    EditorHighlighter highlighter = HighlighterFactory.createHighlighter(getProject(), "A.hs");
    //                   0123456789012345678 9 0123 45 6 7
    highlighter.setText("{-# #-} module");
    HighlighterIterator iterator = highlighter.createIterator(2);
    TestCase.assertEquals(CustomHighlighterTokenType.MULTI_LINE_COMMENT, iterator.getTokenType());

    iterator = highlighter.createIterator(12);
    TestCase.assertEquals(CustomHighlighterTokenType.KEYWORD_1, iterator.getTokenType());
  }
}
