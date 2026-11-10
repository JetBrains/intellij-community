// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlText;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.plugins.intelliLang.inject.TemporaryPlacesRegistry;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class InjectedGroovyTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testTabMethodParentheses() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.xml", """
      <groovy>
      String s = "foo"
      s.codePo<caret>charAt(0)
      </groovy>""");

    XmlText host =
      ReadAction.computeBlocking(() -> PsiTreeUtil.findElementOfClassAtOffset(myFixture.getFile(), myFixture.getEditor().getCaretModel().getOffset(), XmlText.class, false));
    EdtTestUtil.runInEdtAndWait(() -> TemporaryPlacesRegistry.getInstance(myFixture.getProject()).getLanguageInjectionSupport()
      .addInjectionInPlace(GroovyLanguage.INSTANCE, (PsiLanguageInjectionHost)host));

    myFixture.completeBasic();
    myFixture.type("\t");
    myFixture.checkResult("""
                            <groovy>
                            String s = "foo"
                            s.codePointAt(<caret>0)
                            </groovy>""");
  }

  @Test
  public void testIntelliLangInjections() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package groovy.lang; public class GroovyShell { public void evaluate(String s) { }}");
    final PsiFile psiFile = myFixture.configureByText("script.groovy", "new groovy.lang.GroovyShell().evaluate(\"s = new String()\")");
    assertNotNull(psiFile);

    ReadAction.runBlocking(() -> {
      int offset = psiFile.getText().indexOf("\"") + 1;
      assertNotNull(psiFile.findElementAt(offset));
      assertNotNull(InjectedLanguageUtilBase.findInjectedPsiNoCommit(psiFile, offset));
    });
  }

  @Test
  public void testRegexInjections() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package groovy.lang; public class GroovyShell { public void evaluate(String s) { }}");
    final PsiFile psiFile = myFixture.configureByText("script.groovy",
                                                      "new groovy.lang.GroovyShell().evaluate(/ blah-blah-blah \\ language won't be injected here /)");
    assertNotNull(psiFile);

    ReadAction.runBlocking(() -> {
      assertNotNull(InjectedLanguageUtilBase.findInjectedPsiNoCommit(psiFile, psiFile.getText().indexOf("blah") + 1));
      assertNotNull(InjectedLanguageUtilBase.findInjectedPsiNoCommit(psiFile, psiFile.getText().indexOf("injected") + 1));
    });
  }

  @Test
  public void testResolveAnnotationsInInjectedCode() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; @interface Bar{}");

    myFixture.addClass("package groovy.lang; public class GroovyShell { public void evaluate(String s) { }}");
    final PsiFile psiFile = myFixture.configureByText("script.groovy", """
    
      new groovy.lang.GroovyShell().evaluate('''
      import foo.Bar
    
      @Ba<caret>r
      def abc = null
      ''')
      """);
    assertNotNull(psiFile);
    ReadAction.runBlocking(() -> {
      PsiReference ref = psiFile.findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
      assertInstanceOf(PsiClass.class, ref.resolve());
      assertEquals("foo.Bar", ((PsiClass)ref.resolve()).getQualifiedName());
    });
  }

  @Test
  public void testResolveAnnotationsInInjectedCodeInMethodCall() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; @interface Bar{}");

    myFixture.addClass("package groovy.lang; public class GroovyShell { public void evaluate(String s) { }}");
    final PsiFile psiFile = myFixture.configureByText("script.groovy", """
    
      new groovy.lang.GroovyShell().evaluate '''
      import foo.Bar
    
      @Ba<caret>r
      def abc = null
      '''
      """);
    assertNotNull(psiFile);
    ReadAction.runBlocking(() -> {
      PsiReference ref = psiFile.findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
      assertTrue(ref.resolve() instanceof PsiClass);
      assertEquals("foo.Bar", ((PsiClass)ref.resolve()).getQualifiedName());
    });
  }
}
