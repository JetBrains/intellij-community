// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.jetbrains.plugins.groovy.util.ResolveTest;
import org.junit.Assert;
import org.junit.Test;

public class CategorySupportTest extends GroovyLatestTest implements ResolveTest {
  @Test
  public void staticCategoryMethodOutsideOfCategory() {
    GrReflectedMethod resolved = resolveTest(
      """
        @Category(String) class C { def foo() {} }
        C.<caret>foo("")
        """, GrReflectedMethod.class);
    Assert.assertTrue(resolved.getModifierList().hasModifierProperty(PsiModifier.STATIC));
  }

  @Test
  public void categoryMethodWithinCategory() {
    resolveTest(
      """
        @Category(String)
        class C {
          def foo() {}
          def usage() { <caret>foo() }
        }
        """, GrGdkMethod.class);
  }

  @Test
  public void explicitCategoryMethodWithinCategory() {
    resolveTest(
      """
        @Category(String)
        class C {
          static def foo(String s) {}
          def usage() { <caret>foo() }
        }
        """, GrGdkMethod.class);
  }

  @Test
  public void propertyWithinCategoryWithExplicitMethod() {
    resolveTest(
      """
        @Category(String)
        class C {
          static def foo(String s) {}
          def usage() { <caret>foo }
        }
        """, null);
  }
}
