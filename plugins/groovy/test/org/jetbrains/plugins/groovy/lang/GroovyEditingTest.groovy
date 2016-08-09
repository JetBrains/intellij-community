/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author peter
 */
public class GroovyEditingTest extends LightCodeInsightFixtureTestCase {
  final String basePath = TestUtils.testDataPath + "editing/"

  private void doTest(@Nullable String before = null, final String chars, @Nullable String after = null) {
    if (before != null) {
      myFixture.configureByText(getTestName(false) + '.groovy', before)
    }
    else {
      myFixture.configureByFile(getTestName(false) + ".groovy");
    }

    myFixture.type(chars);

    if (after != null) {
      myFixture.checkResult(after)
    }
    else {
      myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
    }
  }

  public void testCodeBlockRightBrace() throws Throwable { doTest('{'); }
  public void testInterpolationInsideStringRightBrace() throws Throwable { doTest('{'); }
  public void testStructuralInterpolationInsideStringRightBrace() throws Throwable { doTest('{'); }
  public void testEnterInMultilineString() throws Throwable { doTest('\n'); }
  public void testEnterInStringInRefExpr() throws Throwable {doTest('\n');}
  public void testEnterInGStringInRefExpr() throws Throwable {doTest('\n');}
  public void testPairAngleBracketAfterClassName() throws Throwable {doTest('<');}
  public void testPairAngleBracketAfterClassNameOvertype() throws Throwable {doTest('>');}
  public void testPairAngleBracketAfterClassNameBackspace() throws Throwable {doTest('\b');}
  public void testNoPairLess() throws Throwable {doTest('<');}

  public void testTripleString() {
    doTest('', "'''", "'''<caret>'''")
  }

  public void testTripleGString() {
    doTest('', '"""', '"""<caret>"""')
  }

  public void "test pair brace after doc with mismatch"() {
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
  
  public void testKeepMultilineStringTrailingSpaces() throws Throwable {
    myFixture.configureByFile(getTestName(false) + '.groovy')
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    String stripSpaces = editorSettings.getStripTrailingSpaces();
    try {
      editorSettings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE);
      Document doc = getEditor().getDocument();
      EditorTestUtil.performTypingAction(getEditor(), (char)' ');
      PsiDocumentManager.getInstance(getProject()).commitDocument(doc);
      FileDocumentManager.getInstance().saveDocument(doc);
    }
    finally {
      editorSettings.setStripTrailingSpaces(stripSpaces);
    }
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }
}