/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.template.impl.TemplateImpl
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.codeInsight.template.impl.actions.ListTemplatesAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author peter
 */
public class GroovyLiveTemplatesTest extends LightCodeInsightFixtureTestCase{
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "liveTemplates/";
  }

  public void testJavaTemplatesWorkInGroovyContext() throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    expandTemplate(myFixture.getEditor());
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  public void testSout() {
    myFixture.configureByText("a.groovy", "sout<caret>");
    expandTemplate(myFixture.getEditor());
    myFixture.checkResult("println <caret>");
  }

  public void testSoutv() {
    myFixture.configureByText("a.groovy", "def x = 2\nsoutv<caret>");
    expandTemplate(myFixture.getEditor());
    myFixture.checkResult("def x = 2\nprintln \"x = \$x\"");
  }

  public void testSoutp() {
    myFixture.configureByText("a.groovy", """
void usage(int num, boolean someBoolean, List<String> args){
  soutp<caret>
}
""");
    expandTemplate(myFixture.getEditor());
    myFixture.checkResult '''
void usage(int num, boolean someBoolean, List<String> args){
    println "num = [$num], someBoolean = [$someBoolean], args = [$args]"
}
'''
  }

  public static void expandTemplate(final Editor editor) {
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      void run() {
        new ListTemplatesAction().actionPerformedImpl(editor.getProject(), editor);
        ((LookupImpl)LookupManager.getActiveLookup(editor)).finishLookup(Lookup.NORMAL_SELECT_CHAR);
      }
    })
  }

  public void testGroovyStatementContext() throws Exception {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("inst", "other");
    assertFalse(isApplicable("class Foo {{ if (a <caret>inst) }}", template));
    assertTrue(isApplicable("class Foo {{ <caret>inst }}", template));
    assertTrue(isApplicable("<caret>inst", template));
    assertFalse(isApplicable("class Foo {{ return (<caret>inst) }}", template));
    assertFalse(isApplicable("class Foo {{ return a <caret>inst) }}", template));
    assertTrue(isApplicable("<caret>a.b()", template));
    assertTrue(isApplicable("<caret>a()", template));
  }

  public void testGroovyExpressionContext() throws Exception {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("lst", "other");
    assertFalse(isApplicable("class Foo {{ if (a <caret>toar) }}", template));
    assertTrue(isApplicable("class Foo {{ <caret>xxx }}", template));
    assertTrue(isApplicable("<caret>xxx", template));
    assertTrue(isApplicable("class Foo {{ return (<caret>aaa) }}", template));
    assertFalse(isApplicable("class Foo {{ return (xxx <caret>yyy) }}", template));
  }

  public void testGroovyDeclarationContext() throws Exception {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("psvm", "other");
    assertFalse(isApplicable("class Foo {{ <caret>xxx }}", template));
    assertFalse(isApplicable("class Foo {{ <caret>xxx }}", template));
    assertFalse(isApplicable("class Foo {{ if (a <caret>xxx) }}", template));
    assertFalse(isApplicable("class Foo {{ return (<caret>xxx) }}", template));
    assertTrue(isApplicable("class Foo { <caret>xxx }", template));
    assertFalse(isApplicable("class Foo { int <caret>xxx }", template));
    assertFalse(isApplicable("class Foo { /* <caret>xxx */ }", template));
    assertTrue(isApplicable("class Foo {}\n<caret>xxx", template));
    assertTrue(isApplicable("<caret>xxx", template));

    assertTrue(isApplicable("class Foo { void foo(<caret>xxx) {} }", template));
    assertTrue(isApplicable("class Foo { void foo(<caret>xxx String bar ) {} }", template));
    assertTrue(isApplicable("class Foo { void foo(<caret>xxx String bar, int goo ) {} }", template));
    assertTrue(isApplicable("class Foo { void foo(String bar, <caret>xxx int goo ) {} }", template));
    assertTrue(isApplicable("class Foo { void foo(String bar, <caret>xxx goo ) {} }", template));
    assertTrue(isApplicable("class Foo { <caret>xxx void foo(String bar, xxx goo ) {} }", template));
    assertTrue(isApplicable("class Foo { void foo(<caret>String[] bar) {} }", template));
    assertTrue(isApplicable("class Foo { <caret>xxx String[] foo(String bar, xxx goo ) {} }", template));
  }

  private boolean isApplicable(String text, TemplateImpl inst) throws IOException {
    myFixture.configureByText("a.groovy", text);
    return TemplateManagerImpl.isApplicable(myFixture.getFile(), myFixture.getEditor().getCaretModel().getOffset(), inst);
  }

}
