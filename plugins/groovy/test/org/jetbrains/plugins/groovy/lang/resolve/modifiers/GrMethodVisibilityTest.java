// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.modifiers;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;

import static com.intellij.psi.PsiModifier.*;

public class GrMethodVisibilityTest extends GrVisibilityTestBase {
  public void testPackageScope() {
    PsiClass clazz = addClass("""
                                class A {
                                  @PackageScope
                                  def foo() {}
                                }
                                """);
    PsiMethod method = clazz.getMethods()[0];
    GrVisibilityTestBase.assertVisibility(method, PACKAGE_LOCAL);
  }

  public void testPackageScopeWithExplicitVisibility() {
    PsiClass clazz = addClass("""
                                class A {
                                  @PackageScope
                                  protected foo() {}
                                }
                                """);
    PsiMethod method = clazz.getMethods()[0];
    GrVisibilityTestBase.assertVisibility(method, PROTECTED);
  }

  public void testPackageScopeNonEmpty() {
    PsiClass clazz = addClass("""
                                class A {
                                  @PackageScope([METHODS])
                                  def foo() {}
                                }
                                """);
    PsiMethod method = clazz.getMethods()[0];
    GrVisibilityTestBase.assertVisibility(method, PUBLIC);
  }

  public void testPackageScopeOnClass() {
    PsiClass clazz = addClass("""
                                @PackageScope([METHODS])
                                class A {
                                  def foo() {}
                                }
                                """);
    PsiMethod method = clazz.getMethods()[0];
    GrVisibilityTestBase.assertVisibility(method, PACKAGE_LOCAL);
  }

  public void testPackageScopeOnClassWithoutMethods() {
    PsiClass clazz = addClass("""
                                @PackageScope
                                class A {
                                  def foo() {}
                                }
                                """);
    PsiMethod method = clazz.getMethods()[0];
    GrVisibilityTestBase.assertVisibility(method, PUBLIC);
  }
}
