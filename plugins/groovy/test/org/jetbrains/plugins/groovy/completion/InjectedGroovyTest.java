// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlText;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import junit.framework.TestCase;
import org.intellij.plugins.intelliLang.inject.TemporaryPlacesRegistry;
import org.jetbrains.plugins.groovy.GroovyLanguage;

public class InjectedGroovyTest extends LightJavaCodeInsightFixtureTestCase {
  public void testTabMethodParentheses() {
    myFixture.configureByText("a.xml", """
      <groovy>
      String s = "foo"
      s.codePo<caret>charAt(0)
      </groovy>""");

    XmlText host =
      PsiTreeUtil.findElementOfClassAtOffset(myFixture.getFile(), myFixture.getEditor().getCaretModel().getOffset(), XmlText.class, false);
    TemporaryPlacesRegistry.getInstance(getProject()).getLanguageInjectionSupport().addInjectionInPlace(GroovyLanguage.INSTANCE,
                                                                                                        (PsiLanguageInjectionHost)host);

    myFixture.completeBasic();
    myFixture.type("\t");
    myFixture.checkResult("""
                            <groovy>
                            String s = "foo"
                            s.codePointAt(<caret>0)
                            </groovy>""");
  }

  public void testIntelliLangInjections() {
    myFixture.addClass("package groovy.lang; public class GroovyShell { public void evaluate(String s) { }}");
    final PsiFile psiFile = myFixture.configureByText("script.groovy", "new groovy.lang.GroovyShell().evaluate(\"s = new String()\")");
    TestCase.assertNotNull(psiFile);

    int offset = psiFile.getText().indexOf("\"") + 1;
    TestCase.assertNotNull(psiFile.findElementAt(offset));
    assertNotNull(InjectedLanguageUtilBase.findInjectedPsiNoCommit(psiFile, offset));
  }

  public void testRegexInjections() {
    myFixture.addClass("package groovy.lang; public class GroovyShell { public void evaluate(String s) { }}");
    final PsiFile psiFile = myFixture.configureByText("script.groovy",
                                                      "new groovy.lang.GroovyShell().evaluate(/ blah-blah-blah \\ language won't be injected here /)");
    TestCase.assertNotNull(psiFile);

    assertNotNull(InjectedLanguageUtilBase.findInjectedPsiNoCommit(psiFile, psiFile.getText().indexOf("blah") + 1));
    assertNotNull(InjectedLanguageUtilBase.findInjectedPsiNoCommit(psiFile, psiFile.getText().indexOf("injected") + 1));
  }

  public void testResolveAnnotationsInInjectedCode() {
    myFixture.addClass("package foo; @interface Bar{}");

    myFixture.addClass("package groovy.lang; public class GroovyShell { public void evaluate(String s) { }}");
    final PsiFile psiFile = myFixture.configureByText("script.groovy", """
      
      new groovy.lang.GroovyShell().evaluate('''
      import foo.Bar
      
      @Ba<caret>r
      def abc = null
      ''')
      """);
    TestCase.assertNotNull(psiFile);
    PsiReference ref = psiFile.findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
    assert ref.resolve() instanceof PsiClass;
    assert ((PsiClass)ref.resolve()).getQualifiedName().equals("foo.Bar");
  }

  public void testResolveAnnotationsInInjectedCodeInMethodCall() {
    myFixture.addClass("package foo; @interface Bar{}");

    myFixture.addClass("package groovy.lang; public class GroovyShell { public void evaluate(String s) { }}");
    final PsiFile psiFile = myFixture.configureByText("script.groovy", """
      
      new groovy.lang.GroovyShell().evaluate '''
      import foo.Bar
      
      @Ba<caret>r
      def abc = null
      '''
      """);
    TestCase.assertNotNull(psiFile);
    PsiReference ref = psiFile.findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
    assertTrue(ref.resolve() instanceof PsiClass);
    assertEquals("foo.Bar", ((PsiClass)ref.resolve()).getQualifiedName());
  }
}
