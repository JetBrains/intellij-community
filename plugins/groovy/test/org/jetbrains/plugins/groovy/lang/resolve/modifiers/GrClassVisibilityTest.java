// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.modifiers;

import com.intellij.psi.PsiClass;

import static com.intellij.psi.PsiModifier.PACKAGE_LOCAL;
import static com.intellij.psi.PsiModifier.PUBLIC;

public class GrClassVisibilityTest extends GrVisibilityTestBase {
  public void testPackageScope() {
    PsiClass clazz = addClass("""
                                @PackageScope
                                class A {}
                                """);
    GrVisibilityTestBase.assertVisibility(clazz, PACKAGE_LOCAL);
  }

  public void testPackageScopeWithExplicitVisibility() {
    PsiClass clazz = addClass("""
                                @PackageScope
                                public class A {}
                                """);
    GrVisibilityTestBase.assertVisibility(clazz, PUBLIC);
  }

  public void testPackageScopeEmptyValue() {
    PsiClass clazz = addClass("""
                                @PackageScope(value = [])
                                class A {}
                                """);
    GrVisibilityTestBase.assertVisibility(clazz, PACKAGE_LOCAL);
  }

  public void testPackageScopeClassValue() {
    PsiClass clazz = addClass("""
                                @PackageScope(value = [CLASS])
                                class A {}
                                """);
    GrVisibilityTestBase.assertVisibility(clazz, PACKAGE_LOCAL);
  }

  public void testPackageScopeNonClassValue() {
    PsiClass clazz = addClass("""
                                @PackageScope(value = [FIELDS])
                                class A {}
                                """);
    GrVisibilityTestBase.assertVisibility(clazz, PUBLIC);
  }

  public void testPackageScopeInnerClass() {
    PsiClass clazz = addClass("""
                                @PackageScope()
                                class A {
                                  class Inner {}
                                }
                                """);
    PsiClass inner = clazz.getInnerClasses()[0];
    GrVisibilityTestBase.assertVisibility(inner, PUBLIC);
  }

  public void testPackageScopeClassValueInnerClass() {
    PsiClass clazz = addClass("""
                                @PackageScope(CLASS)
                                class A {
                                  class Inner {}
                                }
                                """);
    PsiClass inner = clazz.getInnerClasses()[0];
    GrVisibilityTestBase.assertVisibility(inner, PUBLIC);
  }

  public void testInnerClassPackageScope() {
    PsiClass clazz = addClass("""
                                class A {
                                  @PackageScope
                                  class Inner {}
                                }
                                """);
    PsiClass inner = clazz.getInnerClasses()[0];
    GrVisibilityTestBase.assertVisibility(inner, PACKAGE_LOCAL);
  }
}
