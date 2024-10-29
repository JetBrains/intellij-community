// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.modifiers;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;

import static com.intellij.psi.PsiModifier.*;

public class GrConstructorVisibilityTest extends GrVisibilityTestBase {
  public void testPackageScope() {
    PsiClass clazz = addClass("""
                                class A {
                                  @PackageScope
                                  A() {}
                                }
                                """);
    PsiMethod constructor = clazz.getMethods()[0];
    GrVisibilityTestBase.assertVisibility(constructor, PACKAGE_LOCAL);
  }

  public void testPackageScopeWithExplicitVisibility() {
    PsiClass clazz = addClass("""
                                class A {
                                  @PackageScope
                                  private A() {}
                                }
                                """);
    PsiMethod constructor = clazz.getMethods()[0];
    GrVisibilityTestBase.assertVisibility(constructor, PRIVATE);
  }

  public void testPackageScopeNonEmpty() {
    PsiClass clazz = addClass("""
                                class A {
                                  @PackageScope([METHODS])
                                  A() {}
                                }
                                """);
    PsiMethod constructor = clazz.getMethods()[0];
    GrVisibilityTestBase.assertVisibility(constructor, PUBLIC);
  }

  public void testPackageScopeOnClass() {
    PsiClass clazz = addClass("""
                                @PackageScope([CONSTRUCTORS])
                                class A {
                                  A() {}
                                }
                                """);
    PsiMethod constructor = clazz.getMethods()[0];
    GrVisibilityTestBase.assertVisibility(constructor, PACKAGE_LOCAL);
  }

  public void testPackageScopeOnClassWithoutConstructors() {
    PsiClass clazz = addClass("""
                                @PackageScope
                                class A {
                                  A() {}
                                }
                                """);
    PsiMethod constructor = clazz.getMethods()[0];
    GrVisibilityTestBase.assertVisibility(constructor, PUBLIC);
  }
}
