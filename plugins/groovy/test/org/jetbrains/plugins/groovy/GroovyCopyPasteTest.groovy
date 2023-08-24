// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
class GroovyCopyPasteTest extends LightJavaCodeInsightFixtureTestCase {
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

  void testRestoreImports() {
    myFixture.addClass("package foo; public class Foo {}")

    doTest '''import foo.*; <selection>Foo f</selection>''', '<caret>', '''import foo.Foo

Foo f'''
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

  void testPasteFakeDiamond() {
    doTest("<selection>void foo(Map<> a) {}</selection>",
"""
class A {\n
    <caret>
}
""", """
class A {\n
    void foo(Map<> a) {}
}
""")
  }
}
