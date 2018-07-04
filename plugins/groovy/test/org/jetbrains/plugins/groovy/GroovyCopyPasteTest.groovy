// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
/**
 * @author peter
 */
class GroovyCopyPasteTest extends LightCodeInsightFixtureTestCase {
  private int myAddImportsOld

  @Override
  protected void setUp() throws Exception {
    super.setUp()

    CodeInsightSettings settings = CodeInsightSettings.getInstance()
    myAddImportsOld = settings.ADD_IMPORTS_ON_PASTE
    settings.ADD_IMPORTS_ON_PASTE = CodeInsightSettings.YES
  }

  @Override
  protected void tearDown() throws Exception {
    CodeInsightSettings settings = CodeInsightSettings.getInstance()
    settings.ADD_IMPORTS_ON_PASTE = myAddImportsOld
    super.tearDown()
  }

  private void doTest(String fromText, String toText, String expected) {
    myFixture.configureByText 'fromFileName.groovy', fromText
    myFixture.performEditorAction IdeActions.ACTION_COPY
    myFixture.configureByText 'b.groovy', toText
    myFixture.performEditorAction IdeActions.ACTION_PASTE
    myFixture.checkResult expected
  }

  void testEscapeSlashesInRegex() {
    doTest '<selection>a/b</selection>', 'def x = /smth<caret>/', 'def x = /smtha\\/b<caret>/'
  }

  void testEscapeSlashesInRegexFromRegex() {
    doTest 'def x = / <selection>a\\/b</selection>/', 'def x = /smth<caret>/', 'def x = /smtha\\/b<caret>/'
  }

  void testDontEscapeSymbolsInRegex(){
    doTest '''def x = <selection>a/b</selection>''', 'def x = /<caret> /', '''def x = /a\\/b /'''
  }

  void testEscapeDollarInGString() {
    doTest '''def x = '<selection>$a</selection>b/''', 'def x = "smth<caret>h"', 'def x = "smth$a<caret>h"'
  }

  void testEscapeDollarInGString2() {
    doTest '''def x = '<selection>${a}</selection>b/''', 'def x = "smth<caret>h"', 'def x = "smth${a}<caret>h"'
  }

  void testRestoreImports() {
    myFixture.addClass("package foo; public class Foo {}")

    doTest '''import foo.*; <selection>Foo f</selection>''', '<caret>', '''import foo.Foo

Foo f'''
  }

  void testPasteMultilineIntoMultilineGString() throws Exception {
    doTest '<selection>a/b\nc/d</selection>', 'def x = """smth<caret>"""', 'def x = """smtha/b\nc/d<caret>"""'
  }

  void testPasteMultilineIntoString() throws Exception {
    doTest '<selection>a\nd</selection>', "def x = 'smth<caret>'", "def x = 'smtha\\n' +\n        'd<caret>'"
  }

  void testPasteMultilineIntoGString() throws Exception {
    doTest '<selection>a\nd</selection>', 'def x = "smth<caret>"', 'def x = "smtha\\n" +\n        "d<caret>"'
  }

  void testGStringEolReplace() throws Exception {
    doTest '''<selection>first
second
</selection>''',
            '''def x = """
<selection>foo
</selection>"""''',
            '''def x = """
first
second
<caret>"""'''
  }

  void testPasteInGStringContent() {
    doTest 'def a = <selection>5\\6</selection>', 'def x = "<caret> "', 'def x = "5\\\\6 "'
  }

  void testPasteLFInGString() {
    doTest '<selection>bar\nbaz</selection>', '''
"""
$foo
<caret>
"""
''', '''
"""
$foo
bar
baz<caret>
"""
'''
  }

  void testPasteQuotestoTripleGString() {
    doTest('<selection>"bar" + 2</selection>', '''
"""
$foo
<caret>
"""
''', '''
"""
$foo
"bar" + 2<caret>
"""
''')
  }

  void testPasteAfterGStringInjection() {
    doTest("print '<selection>\$</selection>'", '''
print "${foo}<caret>"
''', '''
print "${foo}\\$<caret>"
''')
  }

  void testPasteBeforeGStringInjection() {
    doTest("print '<selection>\$</selection>'", '''
print "<caret>${foo}"
''', '''
print "\\$<caret>${foo}"
''')
  }

  void testPasteEnumConstant() {
    myFixture.addClass('''\
package pack;
enum E {
  CONST
}
''')
    doTest('''\
import static pack.E.CONST
print <selection>CONST</selection>
''', '''\
print <caret>
''', '''\
import static pack.E.CONST

print CONST<caret>
''')
  }

  void testMultilinePasteIntoLineComment() {
    doTest("<selection>multiline\ntext</selection>",
           "class C {\n" +
           "    //<caret>\n" +
           "}",
           "class C {\n" +
           "    //multiline\n" +
           "    //text<caret>\n" +
           "}")
  }


  void 'test single-quoted string'() {
    doTest($/     <selection>'\\'</selection>/$, '', $/'\\'/$)
  }

  void 'test single-quoted string partial'() {
    doTest($/     <selection>'\\</selection>'/$, '', $/'\\/$)
  }

  void 'test single-quoted string content'() {
    doTest($/     '<selection>\\</selection>'/$, '', $/\/$)
  }


  void 'test double-quoted string'() {
    doTest($/     <selection>"\\"</selection>/$, '', $/"\\"/$)
  }

  void 'test double-quoted string partial'() {
    doTest($/     <selection>"\\</selection>"/$, '', $/"\\/$)
  }

  void 'test double-quoted string content'() {
    doTest($/     "<selection>\\</selection>"/$, '', $/\/$)
  }


  void 'test triple-single-quoted string'() {
    doTest($/     <selection>'''\\'''</selection>/$, '', $/'''\\'''/$)
  }

  void 'test triple-single-quoted string partial start quote 1'() {
    doTest($/     '<selection>''\\'''</selection>/$, '', $/''\\'''/$)
  }

  void 'test triple-single-quoted string partial start quote 2'() {
    doTest($/     ''<selection>'\\'''</selection>/$, '', $/'\\'''/$)
  }

  void 'test triple-single-quoted string partial start quote 3'() {
    doTest($/     '''<selection>\\'''</selection>/$, '', $/\\'''/$)
  }

  void 'test triple-single-quoted string partial end quote 1'() {
    doTest($/     <selection>'''\\</selection>'''/$, '', $/'''\\/$)
  }

  void 'test triple-single-quoted string partial end quote 2'() {
    doTest($/     <selection>'''\\'</selection>''/$, '', $/'''\\'/$)
  }

  void 'test triple-single-quoted string partial end quote 3'() {
    doTest($/     <selection>'''\\''</selection>'/$, '', $/'''\\''/$)
  }

  void 'test triple-single-quoted string content'() {
    doTest($/     '''<selection>\\</selection>'''/$, '', $/\/$)
  }


  void 'test triple-double-quoted string'() {
    doTest($/     <selection>"""\\"""</selection>/$, '', $/"""\\"""/$)
  }

  void 'test triple-double-quoted string partial start quote 1'() {
    doTest($/     "<selection>""\\"""</selection>/$, '', $/""\\"""/$)
  }

  void 'test triple-double-quoted string partial start quote 2'() {
    doTest($/     ""<selection>"\\"""</selection>/$, '', $/"\\"""/$)
  }

  void 'test triple-double-quoted string partial start quote 3'() {
    doTest($/     """<selection>\\"""</selection>/$, '', $/\\"""/$)
  }

  void 'test triple-double-quoted string partial end quote 1'() {
    doTest($/     <selection>"""\\</selection>"""/$, '', $/"""\\/$)
  }

  void 'test triple-double-quoted string partial end quote 2'() {
    doTest($/     <selection>"""\\"</selection>""/$, '', $/"""\\"/$)
  }

  void 'test triple-double-quoted string partial end quote 3'() {
    doTest($/     <selection>"""\\""</selection>"/$, '', $/"""\\""/$)
  }

  void 'test triple-double-quoted string content'() {
    doTest($/     """<selection>\\</selection>"""/$, '', $/\/$)
  }


  void 'test slashy string'() {
    doTest($/     <selection>/\//</selection>/$, '', $//\///$)
  }

  void 'test slashy string partial'() {
    doTest($/     <selection>/\/</selection>//$, '', $//\//$)
  }

  void 'test slashy string content'() {
    doTest($/     /<selection>\/</selection>//$, '', $///$)
  }
}
