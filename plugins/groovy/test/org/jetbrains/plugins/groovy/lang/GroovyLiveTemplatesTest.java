// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.codeInsight.template.impl.actions.ListTemplatesAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import junit.framework.TestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

public class GroovyLiveTemplatesTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "liveTemplates/";
  }

  public void testJavaTemplatesWorkInGroovyContext() {
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
    myFixture.checkResult("def x = 2\nprintln \"x = $x\"");
  }

  public void testSoutp() {
    myFixture.configureByText("a.groovy", """
      
      void usage(int num, boolean someBoolean, List<String> args){
        soutp<caret>
      }
      """);
    expandTemplate(myFixture.getEditor());
    myFixture.checkResult("""
                            
                            void usage(int num, boolean someBoolean, List<String> args){
                                println "num = $num, someBoolean = $someBoolean, args = $args"
                            }
                            """);
  }

  public static void expandTemplate(final Editor editor) {
    new ListTemplatesAction().actionPerformedImpl(editor.getProject(), editor);
    ((LookupImpl)LookupManager.getActiveLookup(editor)).finishLookup(Lookup.NORMAL_SELECT_CHAR);
  }

  public void testGroovyStatementContext() throws Exception {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("inst", "Groovy");
    TestCase.assertFalse(isApplicable("class Foo {{ if (a <caret>inst) }}", template));
    TestCase.assertTrue(isApplicable("class Foo {{ <caret>inst }}", template));
    TestCase.assertTrue(isApplicable("<caret>inst", template));
    TestCase.assertFalse(isApplicable("class Foo {{ return (<caret>inst) }}", template));
    TestCase.assertFalse(isApplicable("class Foo {{ return a <caret>inst) }}", template));
    TestCase.assertTrue(isApplicable("<caret>a.b()", template));
    TestCase.assertTrue(isApplicable("<caret>a()", template));
  }

  public void testGroovyExpressionContext() throws Exception {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("lst", "Groovy");
    TestCase.assertFalse(isApplicable("class Foo {{ if (a <caret>toar) }}", template));
    TestCase.assertTrue(isApplicable("class Foo {{ <caret>xxx }}", template));
    TestCase.assertTrue(isApplicable("<caret>xxx", template));
    TestCase.assertTrue(isApplicable("class Foo {{ return (<caret>aaa) }}", template));
    TestCase.assertFalse(isApplicable("class Foo {{ return (xxx <caret>yyy) }}", template));
  }

  public void testGroovyDeclarationContext() throws Exception {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("psvm", "Groovy");
    TestCase.assertFalse(isApplicable("class Foo {{ <caret>xxx }}", template));
    TestCase.assertFalse(isApplicable("class Foo {{ <caret>xxx }}", template));
    TestCase.assertFalse(isApplicable("class Foo {{ if (a <caret>xxx) }}", template));
    TestCase.assertFalse(isApplicable("class Foo {{ return (<caret>xxx) }}", template));
    TestCase.assertTrue(isApplicable("class Foo { <caret>xxx }", template));
    TestCase.assertFalse(isApplicable("class Foo { int <caret>xxx }", template));
    TestCase.assertFalse(isApplicable("class Foo { /* <caret>xxx */ }", template));
    TestCase.assertTrue(isApplicable("class Foo {}\n<caret>xxx", template));
    TestCase.assertTrue(isApplicable("<caret>xxx", template));

    TestCase.assertTrue(isApplicable("class Foo { void foo(<caret>xxx) {} }", template));
    TestCase.assertTrue(isApplicable("class Foo { void foo(<caret>xxx String bar ) {} }", template));
    TestCase.assertTrue(isApplicable("class Foo { void foo(<caret>xxx String bar, int goo ) {} }", template));
    TestCase.assertTrue(isApplicable("class Foo { void foo(String bar, <caret>xxx int goo ) {} }", template));
    TestCase.assertTrue(isApplicable("class Foo { void foo(String bar, <caret>xxx goo ) {} }", template));
    TestCase.assertTrue(isApplicable("class Foo { <caret>xxx void foo(String bar, xxx goo ) {} }", template));
    TestCase.assertTrue(isApplicable("class Foo { void foo(<caret>String[] bar) {} }", template));
    TestCase.assertTrue(isApplicable("class Foo { <caret>xxx String[] foo(String bar, xxx goo ) {} }", template));
  }

  private boolean isApplicable(String text, TemplateImpl inst) {
    myFixture.configureByText("a.groovy", text);
    return TemplateManagerImpl.isApplicable(myFixture.getFile(), myFixture.getEditor().getCaretModel().getOffset(), inst);
  }
}
