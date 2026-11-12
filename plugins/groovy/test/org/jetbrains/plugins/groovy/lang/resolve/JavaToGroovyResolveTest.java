// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixtureKt.groovyResolveFixture;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JavaToGroovyResolveTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<GroovyResolveFixture> resolve =
    groovyResolveFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "resolve/javaToGroovy/");

  @Test
  public void testField1() throws Exception {
    PsiReference ref = resolve.get().configureByFile("field1/A.java");
    ReadAction.runBlocking(() -> {
      PsiElement resolved = ref.resolve();
      assertTrue(resolved instanceof GrField);
    });
  }

  @Test
  public void testAccessorRefToProperty() throws Exception {
    PsiReference ref = resolve.get().configureByFile("accessorRefToProperty/A.java");
    ReadAction.runBlocking(() -> {
      PsiElement resolved = ref.resolve();
      assertTrue(resolved instanceof GrAccessorMethod);
    });
  }

  @Test
  public void testMethod1() throws Exception {
    PsiJavaReference ref = (PsiJavaReference)resolve.get().configureByFile("method1/A.java");
    ReadAction.runBlocking(() -> {
      JavaResolveResult resolveResult = ref.advancedResolve(false);
      assertTrue(resolveResult.getElement() instanceof GrMethod);
      assertTrue(resolveResult.isValidResult());
    });
  }

  @Test
  public void testScriptMain() throws Exception {
    PsiJavaReference ref = (PsiJavaReference)resolve.get().configureByFile("scriptMain/A.java");
    ReadAction.runBlocking(() -> {
      JavaResolveResult resolveResult = ref.advancedResolve(false);
      assertInstanceOf(LightMethodBuilder.class, resolveResult.getElement());
      assertTrue(resolveResult.isValidResult());
    });
  }

  @Test
  public void testScriptMethod() throws Exception {
    PsiJavaReference ref = (PsiJavaReference)resolve.get().configureByFile("scriptMethod/A.java");
    ReadAction.runBlocking(() -> {
      JavaResolveResult resolveResult = ref.advancedResolve(false);
      assertTrue(resolveResult.getElement() instanceof GrMethod);
      assertTrue(resolveResult.isValidResult());
    });
  }

  @Test
  public void testNoDGM() throws Exception {
    PsiJavaReference ref = (PsiJavaReference)resolve.get().configureByFile("noDGM/A.java");
    ReadAction.runBlocking(() -> {
      assertNull(ref.advancedResolve(false).getElement());
    });
  }

  @Test
  public void testPrivateTopLevelClass() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    myFixture.addFileToProject("Foo.groovy", "private class Foo{}");

    PsiReference ref = resolve.get().configureByText("A.java", """
      class A {
        void foo() {
          Object o = new Fo<caret>o();
        }
      }
      """);
    ReadAction.runBlocking(() -> {
      assertNotNull(ref.resolve());
    });
  }

  @Test
  public void testScriptMethods() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    myFixture.addFileToProject("Foo.groovy", """
      void foo(int x = 5) {}
      void foo(String s) {}
      """);

    final PsiReference ref = resolve.get().configureByText("A.java", """
      class A {
        void bar() {
          new Foo().fo<caret>o()
        }
      }
      """);

    ReadAction.runBlocking(() -> {
      assertInstanceOf(GrReflectedMethod.class, ref.resolve());
    });
  }
}
