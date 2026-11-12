// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.ResolveResult;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAnnotationMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixtureKt.groovyResolveFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Max Medvedev
 */
@TestApplication
public class ResolveAnnotationAttributeTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<GroovyResolveFixture> resolve = groovyResolveFixture(projectFixture);

  @Test
  public void testSimpleAttribute() {
    final PsiPolyVariantReference ref = (PsiPolyVariantReference)resolve.get().configureByText(
      """
        @interface A {
          String foo()
        }
        @A(f<caret>oo = '3')
        class A {}
        """);

    ReadAction.runBlocking(() -> {
      assertNotNull(ref.resolve());
    });
  }

  @Test
  public void testAliasAttribute() {
    final PsiPolyVariantReference ref = (PsiPolyVariantReference)resolve.get().configureByText(
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

    ReadAction.runBlocking(() -> {
      final PsiElement resolved = ref.resolve();
      assertNotNull(resolved);
      assertInstanceOf(GrMethod.class, resolved);
      assertEquals("A", ((GrMethod)resolved).getContainingClass().getQualifiedName());
    });
  }

  @Test
  public void testMultiAliasAttribute() {
    final PsiPolyVariantReference ref = (PsiPolyVariantReference)resolve.get().configureByText(
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

    ReadAction.runBlocking(() -> {
      final PsiElement resolved = ref.resolve();
      assertNull(resolved);

      for (ResolveResult result : ref.multiResolve(false)) {
        final PsiElement r = result.getElement();

        assertInstanceOf(GrAnnotationMethod.class, r);
        //noinspection SimplifiableAssertion
        assertFalse("Alias".equals(((GrAnnotationMethod)r).getContainingClass().getQualifiedName()));
      }
    });
  }
}
