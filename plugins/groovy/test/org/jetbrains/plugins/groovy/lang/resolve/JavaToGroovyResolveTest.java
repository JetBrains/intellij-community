// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.testFramework.UsefulTestCase;
import junit.framework.TestCase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.util.TestUtils;

public class JavaToGroovyResolveTest extends GroovyResolveTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "resolve/javaToGroovy/";
  }

  public void testField1() throws Exception {
    PsiReference ref = configureByFile("field1/A.java");
    PsiElement resolved = ref.resolve();
    TestCase.assertTrue(resolved instanceof GrField);
  }

  public void testAccessorRefToProperty() throws Exception {
    PsiReference ref = configureByFile("accessorRefToProperty/A.java");
    PsiElement resolved = ref.resolve();
    TestCase.assertTrue(resolved instanceof GrAccessorMethod);
  }

  public void testMethod1() throws Exception {
    PsiJavaReference ref = (PsiJavaReference)configureByFile("method1/A.java");
    JavaResolveResult resolveResult = ref.advancedResolve(false);
    TestCase.assertTrue(resolveResult.getElement() instanceof GrMethod);
    TestCase.assertTrue(resolveResult.isValidResult());
  }

  public void testScriptMain() throws Exception {
    PsiJavaReference ref = (PsiJavaReference)configureByFile("scriptMain/A.java");
    JavaResolveResult resolveResult = ref.advancedResolve(false);
    UsefulTestCase.assertInstanceOf(resolveResult.getElement(), LightMethodBuilder.class);
    TestCase.assertTrue(resolveResult.isValidResult());
  }

  public void testScriptMethod() throws Exception {
    PsiJavaReference ref = (PsiJavaReference)configureByFile("scriptMethod/A.java");
    JavaResolveResult resolveResult = ref.advancedResolve(false);
    TestCase.assertTrue(resolveResult.getElement() instanceof GrMethod);
    TestCase.assertTrue(resolveResult.isValidResult());
  }

  public void testNoDGM() throws Exception {
    PsiJavaReference ref = (PsiJavaReference)configureByFile("noDGM/A.java");
    TestCase.assertNull(ref.advancedResolve(false).getElement());
  }

  public void testPrivateTopLevelClass() {
    myFixture.addFileToProject("Foo.groovy", "private class Foo{}");

    PsiReference ref = configureByText("A.java", """
      class A {
        void foo() {
          Object o = new Fo<caret>o();
        }
      }
      """);
    TestCase.assertNotNull(ref.resolve());
  }

  public void testScriptMethods() {
    myFixture.addFileToProject("Foo.groovy", """
      void foo(int x = 5) {}
      void foo(String s) {}
      """);

    final PsiReference ref = configureByText("A.java", """
      class A {
        void bar() {
          new Foo().fo<caret>o()
        }
      }
      """);

    UsefulTestCase.assertInstanceOf(ref.resolve(), GrReflectedMethod.class);
  }
}
