// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.modifiers;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.Test;

import static com.intellij.psi.PsiModifier.PACKAGE_LOCAL;
import static com.intellij.psi.PsiModifier.PUBLIC;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class GrClassVisibilityTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Test
  public void testPackageScope() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = GrVisibilityTestUtil.addClass(myFixture, """
                                @PackageScope
                                class A {}
                                """);
    GrVisibilityTestUtil.assertVisibility(clazz, PACKAGE_LOCAL);
  }

  @Test
  public void testPackageScopeWithExplicitVisibility() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = GrVisibilityTestUtil.addClass(myFixture, """
                                @PackageScope
                                public class A {}
                                """);
    GrVisibilityTestUtil.assertVisibility(clazz, PUBLIC);
  }

  @Test
  public void testPackageScopeEmptyValue() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = GrVisibilityTestUtil.addClass(myFixture, """
                                @PackageScope(value = [])
                                class A {}
                                """);
    GrVisibilityTestUtil.assertVisibility(clazz, PACKAGE_LOCAL);
  }

  @Test
  public void testPackageScopeClassValue() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = GrVisibilityTestUtil.addClass(myFixture, """
                                @PackageScope(value = [CLASS])
                                class A {}
                                """);
    GrVisibilityTestUtil.assertVisibility(clazz, PACKAGE_LOCAL);
  }

  @Test
  public void testPackageScopeNonClassValue() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = GrVisibilityTestUtil.addClass(myFixture, """
                                @PackageScope(value = [FIELDS])
                                class A {}
                                """);
    GrVisibilityTestUtil.assertVisibility(clazz, PUBLIC);
  }

  @Test
  public void testPackageScopeInnerClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = GrVisibilityTestUtil.addClass(myFixture, """
                                @PackageScope()
                                class A {
                                  class Inner {}
                                }
                                """);
    ReadAction.runBlocking(() -> {
      PsiClass inner = clazz.getInnerClasses()[0];
      GrVisibilityTestUtil.assertVisibility(inner, PUBLIC);
    });
  }

  @Test
  public void testPackageScopeClassValueInnerClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = GrVisibilityTestUtil.addClass(myFixture, """
                                @PackageScope(CLASS)
                                class A {
                                  class Inner {}
                                }
                                """);
    ReadAction.runBlocking(() -> {
      PsiClass inner = clazz.getInnerClasses()[0];
      GrVisibilityTestUtil.assertVisibility(inner, PUBLIC);
    });
  }

  @Test
  public void testInnerClassPackageScope() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = GrVisibilityTestUtil.addClass(myFixture, """
                                class A {
                                  @PackageScope
                                  class Inner {}
                                }
                                """);
    PsiClass inner = clazz.getInnerClasses()[0];
    GrVisibilityTestUtil.assertVisibility(inner, PACKAGE_LOCAL);
  }
}
