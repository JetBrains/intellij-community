// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.codeInsight.template.impl.actions.ListTemplatesAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class GroovyLiveTemplatesTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "liveTemplates/");
  }

  @Test
  public void testJavaTemplatesWorkInGroovyContext() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".groovy");
    expandTemplate(myFixture.getEditor());
    myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.groovy");
  }

  @Test
  public void testSout() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", "sout<caret>");
    expandTemplate(myFixture.getEditor());
    myFixture.checkResult("println <caret>");
  }

  @Test
  public void testSoutv() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", "def x = 2\nsoutv<caret>");
    expandTemplate(myFixture.getEditor());
    myFixture.checkResult("def x = 2\nprintln \"x = $x\"");
  }

  @Test
  public void testSoutp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", """
    
      void usage(int num, boolean someBoolean, List<String> args){
        soutp<caret>
      }
      """);
    expandTemplate(myFixture.getEditor());
    myFixture.checkResult("""
                          
                            void usage(int num, boolean someBoolean, List<String> args){
                                println "num = " + num + ", someBoolean = " + someBoolean + ", args = " + args
                            }
                            """);
  }

  public static void expandTemplate(final Editor editor) {
    EdtTestUtil.runInEdtAndWait(() -> {
      new ListTemplatesAction().actionPerformedImpl(editor.getProject(), editor);
      ((LookupImpl)LookupManager.getActiveLookup(editor)).finishLookup(Lookup.NORMAL_SELECT_CHAR);
    });
  }

  @Test
  public void testGroovyStatementContext() throws Exception {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("inst", "Groovy");
    assertFalse(isApplicable("class Foo {{ if (a <caret>inst) }}", template));
    assertTrue(isApplicable("class Foo {{ <caret>inst }}", template));
    assertTrue(isApplicable("<caret>inst", template));
    assertFalse(isApplicable("class Foo {{ return (<caret>inst) }}", template));
    assertFalse(isApplicable("class Foo {{ return a <caret>inst) }}", template));
    assertTrue(isApplicable("<caret>a.b()", template));
    assertTrue(isApplicable("<caret>a()", template));
  }

  @Test
  public void testGroovyExpressionContext() throws Exception {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("lst", "Groovy");
    assertFalse(isApplicable("class Foo {{ if (a <caret>toar) }}", template));
    assertTrue(isApplicable("class Foo {{ <caret>xxx }}", template));
    assertTrue(isApplicable("<caret>xxx", template));
    assertTrue(isApplicable("class Foo {{ return (<caret>aaa) }}", template));
    assertFalse(isApplicable("class Foo {{ return (xxx <caret>yyy) }}", template));
  }

  @Test
  public void testGroovyDeclarationContext() throws Exception {
    final TemplateImpl template = TemplateSettings.getInstance().getTemplate("psvm", "Groovy");
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

  private boolean isApplicable(String text, TemplateImpl inst) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", text);
    return ReadAction.computeBlocking(() -> TemplateManagerImpl.isApplicable(myFixture.getFile(), myFixture.getEditor().getCaretModel().getOffset(), inst));
  }
}
