// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.structuralsearch.plugin.ui.StructuralSearchTemplateBuilder;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class SSTemplateBuilderTest extends LightJavaCodeInsightFixtureTestCase {

  public void testClassTemplate() {
    doTest("class foo extends bar, next implements xxx",
           "class foo extends $Class2$, $Class3$ implements $Class4$");
  }

  public void testStatement() {
    doTest("i=1", "$Var1$=1");
  }

  public void testMethodCall() {
    Template template = doTest("foo()", "foo()");
    assertEquals(1, template.getSegmentsCount());
  }

  public void testStartingWhitespace() {
    doTest("    List<Integer> list = null;", "    $Class1$<$Class2$> list = null;");
  }

  public void testInnerWhiteSpace() {
    doTest("try {\n" +
           "    List<Integer> list = null;} finally {}",
           "try {\n" +
           "    $Class1$<$Class2$> list = null;} finally {}");
  }

  public void testEmpty() {
    doTest("", "");
  }

  private Template doTest(String text, String expected) {
    PsiFile psiFile = myFixture.configureByText(JavaFileType.INSTANCE, text);
    TemplateBuilderImpl builder = (TemplateBuilderImpl)new StructuralSearchTemplateBuilder(getFile()).buildTemplate();
    Template template = WriteCommandAction.writeCommandAction(getProject()).compute(() -> {
      Template inlineTemplate = builder.buildInlineTemplate();
      TemplateManager.getInstance(getProject()).startTemplate(getEditor(), inlineTemplate);
      return inlineTemplate;
    });
    assertEquals(expected, psiFile.getText());
    return template;
  }
}
