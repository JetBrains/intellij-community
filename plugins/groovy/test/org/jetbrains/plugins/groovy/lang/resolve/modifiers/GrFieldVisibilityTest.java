// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.modifiers;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.Test;

import static com.intellij.psi.PsiModifier.PACKAGE_LOCAL;
import static com.intellij.psi.PsiModifier.PRIVATE;
import static com.intellij.psi.PsiModifier.PROTECTED;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class GrFieldVisibilityTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Test
  public void testPropertyField() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = GrVisibilityTestUtil.addClass(myFixture, """
                                class A {
                                  def foo
                                }
                                """);
    ReadAction.runBlocking(() -> {
      PsiField field = clazz.getFields()[0];
      GrVisibilityTestUtil.assertVisibility(field, PRIVATE);
    });
  }

  @Test
  public void testPackageScope() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = GrVisibilityTestUtil.addClass(myFixture, """
                                class A {
                                  @PackageScope
                                  def foo
                                }
                                """);
    ReadAction.runBlocking(() -> {
      PsiField field = clazz.getFields()[0];
      GrVisibilityTestUtil.assertVisibility(field, PACKAGE_LOCAL);
    });
  }

  @Test
  public void testPackageScopeWithExplicitVisibility() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = GrVisibilityTestUtil.addClass(myFixture, """
                                class A {
                                  @PackageScope
                                  protected foo
                                }
                                """);
    ReadAction.runBlocking(() -> {
      PsiField field = clazz.getFields()[0];
      GrVisibilityTestUtil.assertVisibility(field, PROTECTED);
    });
  }

  @Test
  public void testPackageScopeNonEmpty() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = GrVisibilityTestUtil.addClass(myFixture, """
                                class A {
                                  @PackageScope([FIELDS])
                                  def foo
                                }
                                """);
    ReadAction.runBlocking(() -> {
      PsiField field = clazz.getFields()[0];
      GrVisibilityTestUtil.assertVisibility(field, PRIVATE);
    });
  }

  @Test
  public void testPackageScopeOnClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = GrVisibilityTestUtil.addClass(myFixture, """
                                @PackageScope([FIELDS])
                                class A {
                                  def foo
                                }
                                """);
    ReadAction.runBlocking(() -> {
      PsiField field = clazz.getFields()[0];
      GrVisibilityTestUtil.assertVisibility(field, PACKAGE_LOCAL);
    });
  }

  @Test
  public void testPackageScopeOnClassWithoutFields() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = GrVisibilityTestUtil.addClass(myFixture, """
                                @PackageScope
                                class A {
                                  def foo
                                }
                                """);
    ReadAction.runBlocking(() -> {
      PsiField field = clazz.getFields()[0];
      GrVisibilityTestUtil.assertVisibility(field, PRIVATE);
    });
  }
}
