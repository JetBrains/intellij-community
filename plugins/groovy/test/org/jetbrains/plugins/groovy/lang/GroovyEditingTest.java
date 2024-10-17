// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.util.TestUtils;

public class GroovyEditingTest extends LightJavaCodeInsightFixtureTestCase {
  private void doTest(@Nullable String before, final String chars, @Nullable String after) {
    if (before != null) {
      myFixture.configureByText(getTestName(false) + ".groovy", before);
    }
    else {
      myFixture.configureByFile(getTestName(false) + ".groovy");
    }

    myFixture.type(chars);

    if (after != null) {
      myFixture.checkResult(after);
    }
    else {
      myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
    }
  }

  private void doTest(final String chars) {
    doTest(null, chars, null);
  }

  public void testCodeBlockRightBrace() { doTest("{"); }

  public void testInterpolationInsideStringRightBrace() { doTest("{"); }

  public void testStructuralInterpolationInsideStringRightBrace() { doTest("{"); }

  public void testEnterInMultilineString() { doTest("\n"); }

  public void testEnterInStringInRefExpr() { doTest("\n"); }

  public void testEnterInGStringInRefExpr() { doTest("\n"); }

  public void testPairAngleBracketAfterClassName() { doTest("<"); }

  public void testPairAngleBracketAfterClassNameOvertype() { doTest(">"); }

  public void testPairAngleBracketAfterClassNameBackspace() { doTest("\b"); }

  public void testNoPairLess() { doTest("<"); }

  public void testTripleString() {
    doTest("", "'''", "'''<caret>'''");
  }

  public void testTripleGString() {
    doTest("", "\"\"\"", "\"\"\"<caret>\"\"\"");
  }

  public void test_pair_brace_after_doc_with_mismatch() {
    doTest("""
             class Foo {
               /**
                * @param o  closure to run in {@code ant.zip{ .. }} context
                */
               void getProject( Object o ) <caret>
             }
             """, "{", """
               class Foo {
                 /**
                  * @param o  closure to run in {@code ant.zip{ .. }} context
                  */
                 void getProject( Object o ) {<caret>}
               }
               """);
  }

  public void testRPareth() {
    doTest("""
               @Anno(
                   foo
                   <caret>
               """, ")", """
               @Anno(
                   foo
               )<caret>
               """);
  }

  public void testQuote() {
    doTest("""
               print <caret>
               """, "'", """
               print '<caret>'
               """);
  }

  public void testDoubleQuote() {
    doTest("""
               print <caret>
               """, "\"", """
               print "<caret>"
               """);
  }

  public void testTripleQuote() {
    doTest("""
               print ''<caret>
               """, "'", """
               print '''<caret>'''
               """);
  }

  public void testDontInsertTripleQuote1() {
    doTest("""
               print '''<caret>'''
               """, "'", """
               print ''''<caret>''
               """);
  }

  public void testDontInsertTripleQuote2() {
    doTest("""
               print ''' ''<caret>'
               """, "'", """
               print ''' '''<caret>
               """);
  }

  public void testDontInsertTripleQuote3() {
    doTest("""
               print ""\" ""<caret>
               """, "\"", """
               print ""\" ""\"<caret>
               """);
  }

  public void testDontInsertTripleQuote4() {
    doTest("""
               print ""\" ${} ""<caret>
               """, "\"", """
               print ""\" ${} ""\"<caret>
               """);
  }

  public void testSkipQuoteAtLiteralEnd1() {
    doTest("""
               print ""\" <caret>""\"
               """, "\"", """
               print ""\" "<caret>""
               """);
  }

  public void testSkipQuoteAtLiteralEnd2() {
    doTest("""
               print ""\" "<caret>""
               """, "\"", """
               print ""\" ""<caret>"
               """);
  }

  public void testSkipQuoteAtLiteralEnd3() {
    doTest("""
               print ""\" ""<caret>"
               """, "\"", """
               print ""\" ""\"<caret>
               """);
  }

  public void testSkipQuoteAtLiteralEnd4() {
    doTest("""
               print ''' <caret>'''
               """, "'", """
               print ''' '<caret>''
               """);
  }

  public void testSkipQuoteAtLiteralEnd5() {
    doTest("""
               print ''' '<caret>''
               """, "'", """
               print ''' ''<caret>'
               """);
  }

  public void testSkipQuoteAtLiteralEnd6() {
    doTest("""
               print ''' ''<caret>'
               """, "'", """
               print ''' '''<caret>
               """);
  }

  public void testGroovyDoc() {
    doTest("""
               /**<caret>
               print 2
               """, "\n", """
               /**
                * <caret>
                */
               print 2
               """);
  }

  public void testGroovyDoc2() {
    doTest("""
               /**<caret>
               class A {}
               """, "\n", """
               /**
                * <caret>
                */
               class A {}
               """);
  }

  public void testParenBeforeString() {
    doTest("foo <caret>''", "(", "foo (<caret>''");
    doTest("foo <caret>''''''", "(", "foo (<caret>''''''");
    doTest("foo <caret>\"\"", "(", "foo (<caret>\"\"");
    doTest("foo <caret>\"\"\"\"\"\"", "(", "foo (<caret>\"\"\"\"\"\"");
    doTest("foo <caret>\"$a\"", "(", "foo (<caret>\"$a\"");
  }

  public void testBackspace() {
    doTest("'<caret>'", "\b", "<caret>");
    doTest("''<caret>", "\b", "'<caret>");
    doTest("\"<caret>\"", "\b", "<caret>");
    doTest("\"\"<caret>", "\b", "\"<caret>");
    doTest("'''<caret>'''", "\b", "''<caret>");
    doTest("\"\"\"<caret>\"\"\"", "\b", "\"\"<caret>");
  }

  public void test_backslash_before_closing_quote() {
    doTest("'''<caret>'''", "\\", "'''\\<selection>\\</selection>'''");
    doTest("'''    <caret>'''", "\\", "'''    \\<selection>\\</selection>'''");
    doTest("'''\n\n<caret>'''", "\\", "'''\n\n\\<selection>\\</selection>'''");
    doTest("'<caret>'", "\\", "'\\<selection>\\</selection>'");
    doTest("'    <caret>'", "\\", "'    \\<selection>\\</selection>'");
  }

  public void testKeepMultilineStringTrailingSpaces() {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    String stripSpaces = editorSettings.getStripTrailingSpaces();
    try {
      editorSettings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE);
      Document doc = getEditor().getDocument();
      EditorTestUtil.performTypingAction(getEditor(), ' ');
      PsiDocumentManager.getInstance(getProject()).commitDocument(doc);
      FileDocumentManager.getInstance().saveDocument(doc);
    }
    finally {
      editorSettings.setStripTrailingSpaces(stripSpaces);
    }

    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  @Contract(pure = true)
  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "editing/";
  }
}
