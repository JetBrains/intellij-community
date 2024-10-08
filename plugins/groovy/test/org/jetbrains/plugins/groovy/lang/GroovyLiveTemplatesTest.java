// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.template.impl.TemplateImpl
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.codeInsight.template.impl.actions.ListTemplatesAction
import com.intellij.openapi.editor.Editor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.util.TestUtils
class GroovyLiveTemplatesTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "liveTemplates/"
  }

  void testJavaTemplatesWorkInGroovyContext() throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".groovy")
    expandTemplate(myFixture.getEditor())
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy")
  }

  void testSout() {
    myFixture.configureByText("a.groovy", "sout<caret>")
    expandTemplate(myFixture.getEditor())
    myFixture.checkResult("println <caret>")
  }

  void testSoutv() {
    myFixture.configureByText("a.groovy", "def x = 2\nsoutv<caret>")
    expandTemplate(myFixture.getEditor())
    myFixture.checkResult("def x = 2\nprintln \"x = \$x\"")
  }

  void testSoutp() {
    myFixture.configureByText("a.groovy", """
void usage(int num, boolean someBoolean, List<String> args){
  soutp<caret>
}
""")
    expandTemplate(myFixture.getEditor())
    myFixture.checkResult '''
void usage(int num, boolean someBoolean, List<String> args){
    println "num = $num, someBoolean = $someBoolean, args = $args"
}
'''
  }

  static void expandTemplate(final Editor editor) {
    new ListTemplatesAction().actionPerformedImpl(editor.getProject(), editor)
    ((LookupImpl)LookupManager.getActiveLookup(editor)).finishLookup(Lookup.NORMAL_SELECT_CHAR)
  }

  void testGroovyStatementContext() throws Exception {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("inst", "Groovy")
    assertFalse(isApplicable("class Foo {{ if (a <caret>inst) }}", template))
    assertTrue(isApplicable("class Foo {{ <caret>inst }}", template))
    assertTrue(isApplicable("<caret>inst", template))
    assertFalse(isApplicable("class Foo {{ return (<caret>inst) }}", template))
    assertFalse(isApplicable("class Foo {{ return a <caret>inst) }}", template))
    assertTrue(isApplicable("<caret>a.b()", template))
    assertTrue(isApplicable("<caret>a()", template))
  }

  void testGroovyExpressionContext() throws Exception {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("lst", "Groovy")
    assertFalse(isApplicable("class Foo {{ if (a <caret>toar) }}", template))
    assertTrue(isApplicable("class Foo {{ <caret>xxx }}", template))
    assertTrue(isApplicable("<caret>xxx", template))
    assertTrue(isApplicable("class Foo {{ return (<caret>aaa) }}", template))
    assertFalse(isApplicable("class Foo {{ return (xxx <caret>yyy) }}", template))
  }

  void testGroovyDeclarationContext() throws Exception {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("psvm", "Groovy")
    assertFalse(isApplicable("class Foo {{ <caret>xxx }}", template))
    assertFalse(isApplicable("class Foo {{ <caret>xxx }}", template))
    assertFalse(isApplicable("class Foo {{ if (a <caret>xxx) }}", template))
    assertFalse(isApplicable("class Foo {{ return (<caret>xxx) }}", template))
    assertTrue(isApplicable("class Foo { <caret>xxx }", template))
    assertFalse(isApplicable("class Foo { int <caret>xxx }", template))
    assertFalse(isApplicable("class Foo { /* <caret>xxx */ }", template))
    assertTrue(isApplicable("class Foo {}\n<caret>xxx", template))
    assertTrue(isApplicable("<caret>xxx", template))

    assertTrue(isApplicable("class Foo { void foo(<caret>xxx) {} }", template))
    assertTrue(isApplicable("class Foo { void foo(<caret>xxx String bar ) {} }", template))
    assertTrue(isApplicable("class Foo { void foo(<caret>xxx String bar, int goo ) {} }", template))
    assertTrue(isApplicable("class Foo { void foo(String bar, <caret>xxx int goo ) {} }", template))
    assertTrue(isApplicable("class Foo { void foo(String bar, <caret>xxx goo ) {} }", template))
    assertTrue(isApplicable("class Foo { <caret>xxx void foo(String bar, xxx goo ) {} }", template))
    assertTrue(isApplicable("class Foo { void foo(<caret>String[] bar) {} }", template))
    assertTrue(isApplicable("class Foo { <caret>xxx String[] foo(String bar, xxx goo ) {} }", template))
  }

  private boolean isApplicable(String text, TemplateImpl inst) throws IOException {
    myFixture.configureByText("a.groovy", text)
    return TemplateManagerImpl.isApplicable(myFixture.getFile(), myFixture.getEditor().getCaretModel().getOffset(), inst)
  }

}
