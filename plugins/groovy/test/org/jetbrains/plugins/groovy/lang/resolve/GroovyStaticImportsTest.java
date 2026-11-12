// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class GroovyStaticImportsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  protected GroovyFile configureByText(String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return (GroovyFile)myFixture.configureByText("_.groovy", text);
  }

  protected GroovyResolveResult[] multiResolveByText(String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GroovyFile file = configureByText(text);
    return ReadAction.computeBlocking(() -> {
      PsiReference reference = file.findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
      if (reference instanceof GroovyReference groovyReference) {
        return groovyReference.multiResolve(false);
      }
      else {
        fail("Cannot find reference");
        return GroovyResolveResult.EMPTY_ARRAY;
      }
    });
  }

  protected GroovyResolveResult advancedResolveByText(String text) {
    GroovyResolveResult[] results = multiResolveByText(text);
    assertEquals(1, results.length);
    return results[0];
  }

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("foo/bar/Baz.groovy", """
      package foo.bar;
      class Baz {
        static def getSomeProp() { null }
        static void getSomeProp(a) {} // not really a getter

        static void setSomeProp(a) {}
        static void setSomeProp() {}  // not really a setter

        static boolean isSomeProp() { true }
        static void isSomeProp(v) {}  // not really a boolean getter

        static Object someProp() { null }
        static Object someProp(p) { null }

        static private someProp       // field
        static class someProp {}

        static groovyProp             // property with private field and a getter/setter
        static final finalGroovyProp  // property with private field and a getter
        static methodWithDefaultParams(a,b,c=1) {}
      }
      """);
  }

  @Test
  public void testStaticImportReference() {

    GroovyResolveResult[] results = multiResolveByText("import static foo.bar.Baz.<caret>someProp");
    assertEquals(10, results.length); // field is not valid result, but groovy ignores it

    results = multiResolveByText("import static foo.bar.Baz.<caret>getSomeProp");
    assertEquals(2, results.length);// both getter and getter-like method are included

    results = multiResolveByText("import static foo.bar.Baz.<caret>groovyProp");
    assertEquals(1, results.length);// getter and setter collapsed into property
    PsiElement element = results[0].getElement();
    assertTrue(element instanceof GrField);

    results = multiResolveByText("import static foo.bar.Baz.<caret>getGroovyProp");
    assertEquals(1, results.length);// getter only
    element = results[0].getElement();
    assertTrue( element instanceof GrAccessorMethod);

    results = multiResolveByText("import static foo.bar.Baz.<caret>finalGroovyProp");
    assertEquals(1, results.length);// getter collapsed into property
    element = results[0].getElement();
    assertTrue( element instanceof GrField);

    results = multiResolveByText("import static foo.bar.Baz.<caret>getFinalGroovyProp");
    assertEquals(1, results.length);// getter only
    element = results[0].getElement();
    assertTrue( element instanceof GrAccessorMethod);

    results = multiResolveByText("import static foo.bar.Baz.<caret>methodWithDefaultParams");
    assertEquals(1, results.length);// collapsed into base
    element = results[0].getElement();
    assertTrue( element instanceof GrMethod && !(element instanceof GrReflectedMethod));
  }

  @Test
  public void testResolveRValueReference() {
    GroovyResolveResult result = advancedResolveByText("""
                                                         import static foo.bar.Baz.someProp
                                                         <caret>someProp
                                                         """);
    PsiElement element = result.getElement();
    assertTrue( element instanceof PsiClass);
    assertEquals("someProp", ((PsiClass)element).getName());
  }

  @Test
  public void testResolveRValueReferenceStarImport() {
    GroovyResolveResult result = advancedResolveByText("""
                                                         import static foo.bar.Baz.*
                                                         <caret>someProp
                                                         """);
    ReadAction.runBlocking(() -> {
      PsiElement element = result.getElement();
      assertTrue( element instanceof PsiMethod);
      PsiMethod psiMethod = (PsiMethod)element;
      assertEquals("getSomeProp", psiMethod.getName());
      assertEquals(0, psiMethod.getParameterList().getParametersCount());
    });
  }

  @Test
  public void testResolveLValueReference() {
    GroovyResolveResult result = advancedResolveByText("""
                                                         import static foo.bar.Baz.someProp
                                                         <caret>someProp = 1
                                                         """);
    PsiElement element = result.getElement();
    assertTrue( element instanceof PsiClass);
    assertEquals("someProp", ((PsiClass)element).getName());
  }

  @Test
  public void testResolveLValueReferenceStarImport() {
    GroovyResolveResult result = advancedResolveByText("""
                                                         import static foo.bar.Baz.*
                                                         <caret>someProp = 1
                                                         """);
    ReadAction.runBlocking(() -> {
      PsiElement element = result.getElement();
      assertTrue( element instanceof PsiMethod);
      PsiMethod psiMethod = (PsiMethod)element;
      assertEquals("setSomeProp", psiMethod.getName());
      assertEquals(1, psiMethod.getParameterList().getParametersCount());
    });
  }

  @Test
  public void testResolveCallToGetter() {
    GroovyResolveResult result = advancedResolveByText("""
                                                         import static foo.bar.Baz.someProp
                                                         <caret>getSomeProp()
                                                         """);
    PsiElement element = result.getElement();
    assertTrue( element instanceof PsiMethod);
    PsiMethod psiMethod = (PsiMethod)element;
    assertEquals("getSomeProp", psiMethod.getName());
    assertEquals(0, psiMethod.getParameterList().getParametersCount());
  }

  @Test
  public void testResolveCallToNotAGetter() {
    GroovyResolveResult result = advancedResolveByText("""
                                                         import static foo.bar.Baz.someProp
                                                         <caret>getSomeProp(2)
                                                         """);
    PsiElement element = result.getElement();
    assertTrue( element instanceof PsiMethod);
    PsiMethod psiMethod = (PsiMethod)element;
    assertEquals("getSomeProp", psiMethod.getName());
    assertEquals(1, psiMethod.getParameterList().getParametersCount());
  }

  @Test
  public void testResolveCallToBooleanGetter() {
    GroovyResolveResult result = advancedResolveByText("""
                                                         import static foo.bar.Baz.someProp
                                                         <caret>isSomeProp()
                                                         """);
    PsiElement element = result.getElement();
    assertTrue( element instanceof PsiMethod);
    PsiMethod psiMethod = (PsiMethod)element;
    assertEquals("isSomeProp", psiMethod.getName());
    assertEquals(0, psiMethod.getParameterList().getParametersCount());
  }

  @Test
  public void testResolveCallToBooleanNotAGetter() {
    GroovyResolveResult result = advancedResolveByText("""
                                                         import static foo.bar.Baz.someProp
                                                         <caret>isSomeProp(3)
                                                         """);
    PsiElement element = result.getElement();
    assertTrue( element instanceof PsiMethod);
    PsiMethod psiMethod = (PsiMethod)element;
    assertEquals("isSomeProp", psiMethod.getName());
    assertEquals(1, psiMethod.getParameterList().getParametersCount());
  }

  @Test
  public void testResolveCallToSetter() {
    GroovyResolveResult result = advancedResolveByText("""
                                                         import static foo.bar.Baz.someProp
                                                         <caret>setSomeProp(4)
                                                         """);
    PsiElement element = result.getElement();
    assertTrue( element instanceof PsiMethod);
    PsiMethod psiMethod = (PsiMethod)element;
    assertEquals("setSomeProp", psiMethod.getName());
    assertEquals(1, psiMethod.getParameterList().getParametersCount());
  }

  @Test
  public void testResolveCallToNotASetter() {
    GroovyResolveResult result = advancedResolveByText("""
                                                         import static foo.bar.Baz.someProp
                                                         <caret>setSomeProp()
                                                         """);
    PsiElement element = result.getElement();
    assertTrue( element instanceof PsiMethod);
    PsiMethod psiMethod = (PsiMethod)element;
    assertEquals("setSomeProp", psiMethod.getName());
    assertEquals(0, psiMethod.getParameterList().getParametersCount());
  }

  @Test
  public void testResolveMethodCall() {
    GroovyResolveResult result = advancedResolveByText("""
                                                         import static foo.bar.Baz.someProp
                                                         <caret>someProp()
                                                         """);
    PsiElement element = result.getElement();
    assertTrue( element instanceof PsiMethod);
    PsiMethod psiMethod = (PsiMethod)element;
    assertEquals("someProp", psiMethod.getName());
    assertEquals(0, psiMethod.getParameterList().getParametersCount());
  }

  @Test
  public void testResolveAnotherMethodCall() {
    GroovyResolveResult result = advancedResolveByText("""
                                                         import static foo.bar.Baz.someProp
                                                         <caret>someProp(5)
                                                         """);
    PsiElement element = result.getElement();
    assertTrue( element instanceof PsiMethod);
    PsiMethod psiMethod = (PsiMethod)element;
    assertEquals("someProp", psiMethod.getName());
    assertEquals(1, psiMethod.getParameterList().getParametersCount());
  }

  @Test
  public void testResolveInnerClass() {
    GroovyResolveResult result = advancedResolveByText("""
                                                         import static foo.bar.Baz.someProp
                                                         new <caret>someProp()
                                                         """);
    PsiElement element = result.getElement();
    assertTrue( element instanceof PsiClass);
    PsiClass psiClass = (PsiClass)element;
    assertEquals("someProp", psiClass.getName());
    assertEquals("foo.bar.Baz", psiClass.getContainingClass().getQualifiedName());
  }

  @Test
  public void testResolveToAField() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("foo/bar/ClassWithoutPropertyMethods.groovy", """
      package foo.bar;
      
      class ClassWithoutPropertyMethods {
        static Object someProp() { null }
        static Object someProp(p) { null }
        public static someProp
      }
      """);
    GroovyResolveResult result = advancedResolveByText("""
                                                         import static foo.bar.ClassWithoutPropertyMethods.someProp
                                                         <caret>someProp
                                                         """);
    ReadAction.runBlocking(() -> {
      PsiElement element = result.getElement();
      assertTrue( element instanceof PsiField);
      PsiField psiField = (PsiField)element;
      assertEquals("someProp", psiField.getName());
      assertEquals("foo.bar.ClassWithoutPropertyMethods", psiField.getContainingClass().getQualifiedName());
    });
  }

  @Test
  public void testResolvePropertyViaStaticGetterImportWithCaches() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(GrUnresolvedAccessInspection.class);
    configureByText("import static foo.bar.Baz.getSomeProp; someProp");
    myFixture.checkHighlighting();
  }
}
