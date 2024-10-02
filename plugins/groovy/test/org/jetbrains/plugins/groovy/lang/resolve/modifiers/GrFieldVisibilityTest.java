// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.modifiers;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;

import static com.intellij.psi.PsiModifier.*;

public class GrFieldVisibilityTest extends GrVisibilityTestBase {
  public void testPropertyField() {
    PsiClass clazz = addClass("""
                                class A {
                                  def foo
                                }
                                """);
    PsiField field = clazz.getFields()[0];
    GrVisibilityTestBase.assertVisibility(field, PRIVATE);
  }

  public void testPackageScope() {
    PsiClass clazz = addClass("""
                                class A {
                                  @PackageScope
                                  def foo
                                }
                                """);
    PsiField field = clazz.getFields()[0];
    GrVisibilityTestBase.assertVisibility(field, PACKAGE_LOCAL);
  }

  public void testPackageScopeWithExplicitVisibility() {
    PsiClass clazz = addClass("""
                                class A {
                                  @PackageScope
                                  protected foo
                                }
                                """);
    PsiField field = clazz.getFields()[0];
    GrVisibilityTestBase.assertVisibility(field, PROTECTED);
  }

  public void testPackageScopeNonEmpty() {
    PsiClass clazz = addClass("""
                                class A {
                                  @PackageScope([FIELDS])
                                  def foo
                                }
                                """);
    PsiField field = clazz.getFields()[0];
    GrVisibilityTestBase.assertVisibility(field, PRIVATE);
  }

  public void testPackageScopeOnClass() {
    PsiClass clazz = addClass("""
                                @PackageScope([FIELDS])
                                class A {
                                  def foo
                                }
                                """);
    PsiField field = clazz.getFields()[0];
    GrVisibilityTestBase.assertVisibility(field, PACKAGE_LOCAL);
  }

  public void testPackageScopeOnClassWithoutFields() {
    PsiClass clazz = addClass("""
                                @PackageScope
                                class A {
                                  def foo
                                }
                                """);
    PsiField field = clazz.getFields()[0];
    GrVisibilityTestBase.assertVisibility(field, PRIVATE);
  }
}
