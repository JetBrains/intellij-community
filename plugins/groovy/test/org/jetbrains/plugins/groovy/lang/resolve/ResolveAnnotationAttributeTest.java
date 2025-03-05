// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.ResolveResult;
import com.intellij.testFramework.UsefulTestCase;
import junit.framework.TestCase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAnnotationMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

/**
 * @author Max Medvedev
 */
public class ResolveAnnotationAttributeTest extends GroovyResolveTestCase {
  public void testSimpleAttribute() {
    final PsiPolyVariantReference ref = (PsiPolyVariantReference)configureByText(
      """
        @interface A {
          String foo()
        }
        @A(f<caret>oo = '3')
        class A {}
        """);

    TestCase.assertNotNull(ref.resolve());
  }

  public void testAliasAttribute() {
    final PsiPolyVariantReference ref = (PsiPolyVariantReference)configureByText(
      """
        @interface A {
          String foo()
        }
        
        @groovy.transform.AnnotationCollector([A])
        @interface Alias {
          String foo()
        }
        
        @Alias(f<caret>oo = '3')
        class X {}
        """);

    final PsiElement resolved = ref.resolve();
    TestCase.assertNotNull(resolved);
    UsefulTestCase.assertInstanceOf(resolved, GrMethod.class);
    assertEquals("A", ((GrMethod)resolved).getContainingClass().getQualifiedName());
  }

  public void testMultiAliasAttribute() {
    final PsiPolyVariantReference ref = (PsiPolyVariantReference)configureByText(
      """
        @interface A {
          String foo()
        }
        @interface B {
          String foo()
        }
        
        @groovy.transform.AnnotationCollector([A, B])
        @interface Alias {
          String foo()
        }
        
        @Alias(f<caret>oo = '3')
        class X {}
        """);

    final PsiElement resolved = ref.resolve();
    assertNull(resolved);

    for (ResolveResult result : ref.multiResolve(false)) {
      final PsiElement r = result.getElement();

      UsefulTestCase.assertInstanceOf(r, GrAnnotationMethod.class);
      //noinspection SimplifiableAssertion
      assertFalse("Alias".equals(((GrAnnotationMethod)r).getContainingClass().getQualifiedName()));
    }
  }
}
