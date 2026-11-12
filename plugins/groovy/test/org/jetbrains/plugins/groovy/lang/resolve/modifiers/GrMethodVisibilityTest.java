// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.modifiers;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.Test;

import static com.intellij.psi.PsiModifier.PACKAGE_LOCAL;
import static com.intellij.psi.PsiModifier.PROTECTED;
import static com.intellij.psi.PsiModifier.PUBLIC;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class GrMethodVisibilityTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Test
  public void testPackageScope() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = GrVisibilityTestUtil.addClass(myFixture, """
                                class A {
                                  @PackageScope
                                  def foo() {}
                                }
                                """);
    ReadAction.runBlocking(() -> {
      PsiMethod method = clazz.getMethods()[0];
      GrVisibilityTestUtil.assertVisibility(method, PACKAGE_LOCAL);
    });
  }

  @Test
  public void testPackageScopeWithExplicitVisibility() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = GrVisibilityTestUtil.addClass(myFixture, """
                                class A {
                                  @PackageScope
                                  protected foo() {}
                                }
                                """);
    ReadAction.runBlocking(() -> {
      PsiMethod method = clazz.getMethods()[0];
      GrVisibilityTestUtil.assertVisibility(method, PROTECTED);
    });
  }

  @Test
  public void testPackageScopeNonEmpty() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = GrVisibilityTestUtil.addClass(myFixture, """
                                class A {
                                  @PackageScope([METHODS])
                                  def foo() {}
                                }
                                """);
    ReadAction.runBlocking(() -> {
      PsiMethod method = clazz.getMethods()[0];
      GrVisibilityTestUtil.assertVisibility(method, PUBLIC);
    });
  }

  @Test
  public void testPackageScopeOnClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = GrVisibilityTestUtil.addClass(myFixture, """
                                @PackageScope([METHODS])
                                class A {
                                  def foo() {}
                                }
                                """);
    ReadAction.runBlocking(() -> {
      PsiMethod method = clazz.getMethods()[0];
      GrVisibilityTestUtil.assertVisibility(method, PACKAGE_LOCAL);
    });
  }

  @Test
  public void testPackageScopeOnClassWithoutMethods() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = GrVisibilityTestUtil.addClass(myFixture, """
                                @PackageScope
                                class A {
                                  def foo() {}
                                }
                                """);
    ReadAction.runBlocking(() -> {
      PsiMethod method = clazz.getMethods()[0];
      GrVisibilityTestUtil.assertVisibility(method, PUBLIC);
    });
  }
}
