// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.*;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.junit.Assert;

public class GroovyStaticImportsTest extends LightGroovyTestCase {
  protected GroovyFile configureByText(String text) {
    return (GroovyFile)getFixture().configureByText("_.groovy", text);
  }

  protected GroovyResolveResult[] multiResolveByText(String text) {
    GroovyFile file = configureByText(text);
    PsiReference reference = file.findReferenceAt(getFixture().getEditor().getCaretModel().getOffset());
    if (reference instanceof GroovyReference groovyReference) {
      return groovyReference.multiResolve(false);
    }
    else {
      Assert.fail("Cannot find reference");
      return GroovyResolveResult.EMPTY_ARRAY;
    }
  }

  protected GroovyResolveResult advancedResolveByText(String text) {
    GroovyResolveResult[] results = multiResolveByText(text);
    Assert.assertEquals(1, results.length);
    return results[0];
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    getFixture().addFileToProject("foo/bar/Baz.groovy", """
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

  public void testStaticImportReference() {

    GroovyResolveResult[] results = multiResolveByText("import static foo.bar.Baz.<caret>someProp");
    Assert.assertEquals(10, results.length); // field is not valid result, but groovy ignores it

    results = multiResolveByText("import static foo.bar.Baz.<caret>getSomeProp");
    Assert.assertEquals(2, results.length);// both getter and getter-like method are included

    results = multiResolveByText("import static foo.bar.Baz.<caret>groovyProp");
    Assert.assertEquals(1, results.length);// getter and setter collapsed into property
    PsiElement element = results[0].getElement();
    Assert.assertTrue(element instanceof GrField);

    results = multiResolveByText("import static foo.bar.Baz.<caret>getGroovyProp");
    Assert.assertEquals(1, results.length);// getter only
    element = results[0].getElement();
    Assert.assertTrue( element instanceof GrAccessorMethod);

    results = multiResolveByText("import static foo.bar.Baz.<caret>finalGroovyProp");
    Assert.assertEquals(1, results.length);// getter collapsed into property
    element = results[0].getElement();
    Assert.assertTrue( element instanceof GrField);

    results = multiResolveByText("import static foo.bar.Baz.<caret>getFinalGroovyProp");
    Assert.assertEquals(1, results.length);// getter only
    element = results[0].getElement();
    Assert.assertTrue( element instanceof GrAccessorMethod);

    results = multiResolveByText("import static foo.bar.Baz.<caret>methodWithDefaultParams");
    Assert.assertEquals(1, results.length);// collapsed into base
    element = results[0].getElement();
    Assert.assertTrue( element instanceof GrMethod && !(element instanceof GrReflectedMethod));
  }

  public void testResolveRValueReference() {
    GroovyResolveResult result = advancedResolveByText("""
                                                         import static foo.bar.Baz.someProp
                                                         <caret>someProp
                                                         """);
    PsiElement element = result.getElement();
    Assert.assertTrue( element instanceof PsiClass);
    Assert.assertEquals("someProp", ((PsiClass)element).getName());
  }

  public void testResolveRValueReferenceStarImport() {
    GroovyResolveResult result = advancedResolveByText("""
                                                         import static foo.bar.Baz.*
                                                         <caret>someProp
                                                         """);
    PsiElement element = result.getElement();
    Assert.assertTrue( element instanceof PsiMethod);
    PsiMethod psiMethod = (PsiMethod)element;
    Assert.assertEquals("getSomeProp", psiMethod.getName());
    Assert.assertEquals(0, psiMethod.getParameterList().getParametersCount());
  }

  public void testResolveLValueReference() {
    GroovyResolveResult result = advancedResolveByText("""
                                                         import static foo.bar.Baz.someProp
                                                         <caret>someProp = 1
                                                         """);
    PsiElement element = result.getElement();
    Assert.assertTrue( element instanceof PsiClass);
    Assert.assertEquals("someProp", ((PsiClass)element).getName());
  }

  public void testResolveLValueReferenceStarImport() {
    GroovyResolveResult result = advancedResolveByText("""
                                                         import static foo.bar.Baz.*
                                                         <caret>someProp = 1
                                                         """);
    PsiElement element = result.getElement();
    Assert.assertTrue( element instanceof PsiMethod);
    PsiMethod psiMethod = (PsiMethod)element;
    Assert.assertEquals("setSomeProp", psiMethod.getName());
    Assert.assertEquals(1, psiMethod.getParameterList().getParametersCount());
  }

  public void testResolveCallToGetter() {
    GroovyResolveResult result = advancedResolveByText("""
                                                         import static foo.bar.Baz.someProp
                                                         <caret>getSomeProp()
                                                         """);
    PsiElement element = result.getElement();
    Assert.assertTrue( element instanceof PsiMethod);
    PsiMethod psiMethod = (PsiMethod)element;
    Assert.assertEquals("getSomeProp", psiMethod.getName());
    Assert.assertEquals(0, psiMethod.getParameterList().getParametersCount());
  }

  public void testResolveCallToNotAGetter() {
    GroovyResolveResult result = advancedResolveByText("""
                                                         import static foo.bar.Baz.someProp
                                                         <caret>getSomeProp(2)
                                                         """);
    PsiElement element = result.getElement();
    Assert.assertTrue( element instanceof PsiMethod);
    PsiMethod psiMethod = (PsiMethod)element;
    Assert.assertEquals("getSomeProp", psiMethod.getName());
    Assert.assertEquals(1, psiMethod.getParameterList().getParametersCount());
  }

  public void testResolveCallToBooleanGetter() {
    GroovyResolveResult result = advancedResolveByText("""
                                                         import static foo.bar.Baz.someProp
                                                         <caret>isSomeProp()
                                                         """);
    PsiElement element = result.getElement();
    Assert.assertTrue( element instanceof PsiMethod);
    PsiMethod psiMethod = (PsiMethod)element;
    Assert.assertEquals("isSomeProp", psiMethod.getName());
    Assert.assertEquals(0, psiMethod.getParameterList().getParametersCount());
  }

  public void testResolveCallToBooleanNotAGetter() {
    GroovyResolveResult result = advancedResolveByText("""
                                                         import static foo.bar.Baz.someProp
                                                         <caret>isSomeProp(3)
                                                         """);
    PsiElement element = result.getElement();
    Assert.assertTrue( element instanceof PsiMethod);
    PsiMethod psiMethod = (PsiMethod)element;
    Assert.assertEquals("isSomeProp", psiMethod.getName());
    Assert.assertEquals(1, psiMethod.getParameterList().getParametersCount());
  }

  public void testResolveCallToSetter() {
    GroovyResolveResult result = advancedResolveByText("""
                                                         import static foo.bar.Baz.someProp
                                                         <caret>setSomeProp(4)
                                                         """);
    PsiElement element = result.getElement();
    Assert.assertTrue( element instanceof PsiMethod);
    PsiMethod psiMethod = (PsiMethod)element;
    Assert.assertEquals("setSomeProp", psiMethod.getName());
    Assert.assertEquals(1, psiMethod.getParameterList().getParametersCount());
  }

  public void testResolveCallToNotASetter() {
    GroovyResolveResult result = advancedResolveByText("""
                                                         import static foo.bar.Baz.someProp
                                                         <caret>setSomeProp()
                                                         """);
    PsiElement element = result.getElement();
    Assert.assertTrue( element instanceof PsiMethod);
    PsiMethod psiMethod = (PsiMethod)element;
    Assert.assertEquals("setSomeProp", psiMethod.getName());
    Assert.assertEquals(0, psiMethod.getParameterList().getParametersCount());
  }

  public void testResolveMethodCall() {
    GroovyResolveResult result = advancedResolveByText("""
                                                         import static foo.bar.Baz.someProp
                                                         <caret>someProp()
                                                         """);
    PsiElement element = result.getElement();
    Assert.assertTrue( element instanceof PsiMethod);
    PsiMethod psiMethod = (PsiMethod)element;
    Assert.assertEquals("someProp", psiMethod.getName());
    Assert.assertEquals(0, psiMethod.getParameterList().getParametersCount());
  }

  public void testResolveAnotherMethodCall() {
    GroovyResolveResult result = advancedResolveByText("""
                                                         import static foo.bar.Baz.someProp
                                                         <caret>someProp(5)
                                                         """);
    PsiElement element = result.getElement();
    Assert.assertTrue( element instanceof PsiMethod);
    PsiMethod psiMethod = (PsiMethod)element;
    Assert.assertEquals("someProp", psiMethod.getName());
    Assert.assertEquals(1, psiMethod.getParameterList().getParametersCount());
  }

  public void testResolveInnerClass() {
    GroovyResolveResult result = advancedResolveByText("""
                                                         import static foo.bar.Baz.someProp
                                                         new <caret>someProp()
                                                         """);
    PsiElement element = result.getElement();
    Assert.assertTrue( element instanceof PsiClass);
    PsiClass psiClass = (PsiClass)element;
    Assert.assertEquals("someProp", psiClass.getName());
    Assert.assertEquals("foo.bar.Baz", psiClass.getContainingClass().getQualifiedName());
  }

  public void testResolveToAField() {
    getFixture().addFileToProject("foo/bar/ClassWithoutPropertyMethods.groovy", """
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
    PsiElement element = result.getElement();
    Assert.assertTrue( element instanceof PsiField);
    PsiField psiField = (PsiField)element;
    Assert.assertEquals("someProp", psiField.getName());
    Assert.assertEquals("foo.bar.ClassWithoutPropertyMethods", psiField.getContainingClass().getQualifiedName());
  }

  public void testResolvePropertyViaStaticGetterImportWithCaches() {
    getFixture().enableInspections(GrUnresolvedAccessInspection.class);
    configureByText("import static foo.bar.Baz.getSomeProp; someProp");
    getFixture().checkHighlighting();
  }
}
