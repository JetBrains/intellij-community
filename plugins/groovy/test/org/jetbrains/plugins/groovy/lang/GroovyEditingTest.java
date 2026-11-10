// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class GroovyEditingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "editing/");
  }

  private void doTest(@Nullable String before, final String chars, @Nullable String after) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    if (before != null) {
      myFixture.configureByText(myFixture.getTestName(false) + ".groovy", before);
    }
    else {
      myFixture.configureByFile(myFixture.getTestName(false) + ".groovy");
    }

    myFixture.type(chars);

    if (after != null) {
      myFixture.checkResult(after);
    }
    else {
      myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.groovy");
    }
  }

  private void doTest(final String chars) {
    doTest(null, chars, null);
  }

  @Test
  public void testCodeBlockRightBrace() { doTest("{"); }

  @Test
  public void testInterpolationInsideStringRightBrace() { doTest("{"); }

  @Test
  public void testStructuralInterpolationInsideStringRightBrace() { doTest("{"); }

  @Test
  public void testEnterInMultilineString() { doTest("\n"); }

  @Test
  public void testEnterInStringInRefExpr() { doTest("\n"); }

  @Test
  public void testEnterInGStringInRefExpr() { doTest("\n"); }

  @Test
  public void testPairAngleBracketAfterClassName() { doTest("<"); }

  @Test
  public void testPairAngleBracketAfterClassNameOvertype() { doTest(">"); }

  @Test
  public void testPairAngleBracketAfterClassNameBackspace() { doTest("\b"); }

  @Test
  public void testNoPairLess() { doTest("<"); }

  @Test
  public void testTripleString() {
    doTest("", "'''", "'''<caret>'''");
  }

  @Test
  public void testTripleGString() {
    doTest("", "\"\"\"", "\"\"\"<caret>\"\"\"");
  }

  @Test
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

  @Test
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

  @Test
  public void testQuote() {
    doTest("""
               print <caret>
               """, "'", """
               print '<caret>'
               """);
  }

  @Test
  public void testDoubleQuote() {
    doTest("""
               print <caret>
               """, "\"", """
               print "<caret>"
               """);
  }

  @Test
  public void testTripleQuote() {
    doTest("""
               print ''<caret>
               """, "'", """
               print '''<caret>'''
               """);
  }

  @Test
  public void testDontInsertTripleQuote1() {
    doTest("""
               print '''<caret>'''
               """, "'", """
               print ''''<caret>''
               """);
  }

  @Test
  public void testDontInsertTripleQuote2() {
    doTest("""
               print ''' ''<caret>'
               """, "'", """
               print ''' '''<caret>
               """);
  }

  @Test
  public void testDontInsertTripleQuote3() {
    doTest("""
               print ""\" ""<caret>
               """, "\"", """
               print ""\" ""\"<caret>
               """);
  }

  @Test
  public void testDontInsertTripleQuote4() {
    doTest("""
               print ""\" ${} ""<caret>
               """, "\"", """
               print ""\" ${} ""\"<caret>
               """);
  }

  @Test
  public void testSkipQuoteAtLiteralEnd1() {
    doTest("""
               print ""\" <caret>""\"
               """, "\"", """
               print ""\" "<caret>""
               """);
  }

  @Test
  public void testSkipQuoteAtLiteralEnd2() {
    doTest("""
               print ""\" "<caret>""
               """, "\"", """
               print ""\" ""<caret>"
               """);
  }

  @Test
  public void testSkipQuoteAtLiteralEnd3() {
    doTest("""
               print ""\" ""<caret>"
               """, "\"", """
               print ""\" ""\"<caret>
               """);
  }

  @Test
  public void testSkipQuoteAtLiteralEnd4() {
    doTest("""
               print ''' <caret>'''
               """, "'", """
               print ''' '<caret>''
               """);
  }

  @Test
  public void testSkipQuoteAtLiteralEnd5() {
    doTest("""
               print ''' '<caret>''
               """, "'", """
               print ''' ''<caret>'
               """);
  }

  @Test
  public void testSkipQuoteAtLiteralEnd6() {
    doTest("""
               print ''' ''<caret>'
               """, "'", """
               print ''' '''<caret>
               """);
  }

  @Test
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

  @Test
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

  @Test
  public void testParenBeforeString() {
    doTest("foo <caret>''", "(", "foo (<caret>''");
    doTest("foo <caret>''''''", "(", "foo (<caret>''''''");
    doTest("foo <caret>\"\"", "(", "foo (<caret>\"\"");
    doTest("foo <caret>\"\"\"\"\"\"", "(", "foo (<caret>\"\"\"\"\"\"");
    doTest("foo <caret>\"$a\"", "(", "foo (<caret>\"$a\"");
  }

  @Test
  public void testBackspace() {
    doTest("'<caret>'", "\b", "<caret>");
    doTest("''<caret>", "\b", "'<caret>");
    doTest("\"<caret>\"", "\b", "<caret>");
    doTest("\"\"<caret>", "\b", "\"<caret>");
    doTest("'''<caret>'''", "\b", "''<caret>");
    doTest("\"\"\"<caret>\"\"\"", "\b", "\"\"<caret>");
  }

  @Test
  public void test_backslash_before_closing_quote() {
    doTest("'''<caret>'''", "\\", "'''\\<selection>\\</selection>'''");
    doTest("'''    <caret>'''", "\\", "'''    \\<selection>\\</selection>'''");
    doTest("'''\n\n<caret>'''", "\\", "'''\n\n\\<selection>\\</selection>'''");
    doTest("'<caret>'", "\\", "'\\<selection>\\</selection>'");
    doTest("'    <caret>'", "\\", "'    \\<selection>\\</selection>'");
  }

  @Test
  public void testKeepMultilineStringTrailingSpaces() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".groovy");
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    String stripSpaces = editorSettings.getStripTrailingSpaces();
    try {
      editorSettings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE);
      EdtTestUtil.runInEdtAndWait(() -> {
        Document doc = myFixture.getEditor().getDocument();
        EditorTestUtil.performTypingAction(myFixture.getEditor(), ' ');
        PsiDocumentManager.getInstance(myFixture.getProject()).commitDocument(doc);
        FileDocumentManager.getInstance().saveDocument(doc);
      });
    }
    finally {
      editorSettings.setStripTrailingSpaces(stripSpaces);
    }

    myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.groovy");
  }
}
