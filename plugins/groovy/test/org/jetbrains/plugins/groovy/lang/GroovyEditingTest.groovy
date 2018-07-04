// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author peter
 */
class GroovyEditingTest extends LightCodeInsightFixtureTestCase {
  final String basePath = TestUtils.testDataPath + "editing/"

  private void doTest(@Nullable String before = null, final String chars, @Nullable String after = null) {
    if (before != null) {
      myFixture.configureByText(getTestName(false) + '.groovy', before)
    }
    else {
      myFixture.configureByFile(getTestName(false) + ".groovy")
    }

    myFixture.type(chars)

    if (after != null) {
      myFixture.checkResult(after)
    }
    else {
      myFixture.checkResultByFile(getTestName(false) + "_after.groovy")
    }
  }

  void testCodeBlockRightBrace() throws Throwable { doTest('{') }

  void testInterpolationInsideStringRightBrace() throws Throwable { doTest('{') }

  void testStructuralInterpolationInsideStringRightBrace() throws Throwable { doTest('{') }

  void testEnterInMultilineString() throws Throwable { doTest('\n') }

  void testEnterInStringInRefExpr() throws Throwable { doTest('\n') }

  void testEnterInGStringInRefExpr() throws Throwable { doTest('\n') }

  void testPairAngleBracketAfterClassName() throws Throwable { doTest('<') }

  void testPairAngleBracketAfterClassNameOvertype() throws Throwable { doTest('>') }

  void testPairAngleBracketAfterClassNameBackspace() throws Throwable { doTest('\b') }

  void testNoPairLess() throws Throwable { doTest('<') }

  void testTripleString() {
    doTest('', "'''", "'''<caret>'''")
  }

  void testTripleGString() {
    doTest('', '"""', '"""<caret>"""')
  }

  void "test pair brace after doc with mismatch"() {
    doTest('''
class Foo {
  /**
   * @param o  closure to run in {@code ant.zip{ .. }} context
   */
  void getProject( Object o ) <caret>
}
''', '{', '''
class Foo {
  /**
   * @param o  closure to run in {@code ant.zip{ .. }} context
   */
  void getProject( Object o ) {<caret>}
}
''')
  }

  void testRPareth() {
    doTest('''
@Anno(
    foo
    <caret>
''', ')', '''
@Anno(
    foo
)<caret>
''')
  }

  void testQuote() {
    doTest('''\
print <caret>
''', "'", '''\
print '<caret>'
''')
  }

  void testDoubleQuote() {
    doTest('''\
print <caret>
''', '"', '''\
print "<caret>"
''')
  }

  void testTripleQuote() {
    doTest("""\
print ''<caret>
""", "'", """\
print '''<caret>'''
""")
  }

  void testDontInsertTripleQuote1() {
    doTest("""\
print '''<caret>'''
""", "'", """\
print ''''<caret>''
""")
  }

  void testDontInsertTripleQuote2() {
    doTest("""\
print ''' ''<caret>'
""", "'", """\
print ''' '''<caret>
""")
  }

  void testDontInsertTripleQuote3() {
    doTest('''\
print """ ""<caret>
''', '"', '''\
print """ """<caret>
''')
  }

  void testDontInsertTripleQuote4() {
    doTest('''\
print """ ${} ""<caret>
''', '"', '''\
print """ ${} """<caret>
''')
  }

  void testSkipQuoteAtLiteralEnd1() {
    doTest('''\
print """ <caret>"""
''', '"', '''\
print """ "<caret>""
''')
  }

  void testSkipQuoteAtLiteralEnd2() {
    doTest('''\
print """ "<caret>""
''', '"', '''\
print """ ""<caret>"
''')
  }

  void testSkipQuoteAtLiteralEnd3() {
    doTest('''\
print """ ""<caret>"
''', '"', '''\
print """ """<caret>
''')
  }

  void testSkipQuoteAtLiteralEnd4() {
    doTest("""\
print ''' <caret>'''
""", "'", """\
print ''' '<caret>''
""")
  }

  void testSkipQuoteAtLiteralEnd5() {
    doTest("""\
print ''' '<caret>''
""", "'", """\
print ''' ''<caret>'
""")
  }

  void testSkipQuoteAtLiteralEnd6() {
    doTest("""\
print ''' ''<caret>'
""", "'", """\
print ''' '''<caret>
""")
  }

  void testGroovyDoc() {
    doTest('''\
/**<caret>
print 2
''', '\n', '''\
/**
 * <caret>
 */
print 2
''')
  }

  void testGroovyDoc2() {
    doTest('''\
/**<caret>
class A {}
''', '\n', '''\
/**
 * <caret>
 */
class A {}
''')
  }

  void testParenBeforeString() {
    doTest "foo <caret>''", '(', "foo (<caret>''"
    doTest "foo <caret>''''''", '(', "foo (<caret>''''''"
    doTest 'foo <caret>""', '(', 'foo (<caret>""'
    doTest 'foo <caret>""""""', '(', 'foo (<caret>""""""'
    doTest 'foo <caret>"$a"', '(', 'foo (<caret>"$a"'
  }

  void testBackspace() {
    doTest(/'<caret>'/, '\b', /<caret>/)
    doTest(/''<caret>/, '\b', /'<caret>/)
    doTest(/"<caret>"/, '\b', /<caret>/)
    doTest(/""<caret>/, '\b', /"<caret>/)
    doTest(/'''<caret>'''/, '\b', /''<caret>/)
    doTest(/"""<caret>"""/, '\b', /""<caret>/)
  }

  /*
  todo uncomment when implemented
  void testRegex1() {
    doTest('<caret>', '/', '/<caret>/')
  }

  void testRegex2() {
    doTest('/a<caret>/', '/', '/a/<caret>')
  }

  void testDollarRegex1() {
    doTest('$<caret>', '/', '$/<caret>/$')
  }*/

  void testKeepMultilineStringTrailingSpaces() throws Throwable {
    myFixture.configureByFile(getTestName(false) + '.groovy')
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance()
    String stripSpaces = editorSettings.getStripTrailingSpaces()
    try {
      editorSettings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE)
      Document doc = getEditor().getDocument()
      EditorTestUtil.performTypingAction(getEditor(), (char)' ')
      PsiDocumentManager.getInstance(getProject()).commitDocument(doc)
      FileDocumentManager.getInstance().saveDocument(doc)
    }
    finally {
      editorSettings.setStripTrailingSpaces(stripSpaces)
    }
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy")
  }
}