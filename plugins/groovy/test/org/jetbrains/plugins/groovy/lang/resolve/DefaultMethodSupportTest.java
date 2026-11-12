// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixtureKt.groovyResolveFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class DefaultMethodSupportTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_3_0);
  private final TestFixture<GroovyResolveFixture> resolve = groovyResolveFixture(projectFixture);

  @Test
  public void testSuperReferenceWithQualifier() {
    PsiMethod method = resolve.get().resolveByText(
      """
        interface A {
            default String exec() { 'A' }
        }
        interface B {
            default String exec() { 'B' }
        }
        
        class C implements A,B {
            String exec() {A.super.exe<caret>c() }
        }
        """, PsiMethod.class);
    ReadAction.runBlocking(() -> {
      assertEquals("A", method.getContainingClass().getName());
    });
  }

  @Test
  public void testSuperReferenceWithQualifier2() {
    PsiMethod method = resolve.get().resolveByText(
      """
        interface A {
            default String exec() { 'A' }
        }
        interface B {
            default String exec() { 'B' }
        }
        
        class C implements A, B {
            String exec() {B.super.exe<caret>c() }
        }
        """, PsiMethod.class);
    ReadAction.runBlocking(() -> {
      assertEquals("B", method.getContainingClass().getName());
    });
  }

  @Test
  public void testClashingMethods() {
    GrTraitMethod method = resolve.get().resolveByText(
      """
        interface A {
            default String exec() { 'A' }
        }
        interface B {
            default String exec() { 'B' }
        }
        
        class C implements A, B {
            String foo() {exe<caret>c() }
        }
        """, GrTraitMethod.class);
    ReadAction.runBlocking(() -> {
      assertEquals("B", method.getPrototype().getContainingClass().getName());
    });
  }

  @Test
  public void testDefaultMethodFromAsOperator1() {
    resolve.get().resolveByText(
      """
        interface A {
          default foo(){}
        }
        class B {
          def bar() {}
        }
        
        def v = new B() as A
        v.fo<caret>o()
        """, PsiMethod.class);
  }

  @Test
  public void testDefaultMethodFromAsOperator2() {
    resolve.get().resolveByText(
      """
        interface A {
          default foo(){}
        }
        class B {
          def bar() {}
        }
        
        def v = new B() as A
        v.ba<caret>r()
        """, PsiMethod.class);
  }

  @Test
  public void testDefaultMethodFromAssigning() {
    resolve.get().resolveByText(
      """
        interface I {
            int foo()
            default int bar() {
                2
            }
        }
        
        I i = {3}
        i.ba<caret>r()
        """, PsiMethod.class);
  }

  @Test
  public void testDefaultMethodFromJavaInterface() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    myFixture.addClass(
      """
        interface IServiceJava<T> {
            default void save(T entity) {
                System.out.println(entity);
            }
        }
        """);
    resolve.get().resolveByText(
      """
        class TestGenericGroovy implements IServiceJava<String> {
          void save(String entity) {
            super.sa<caret>ve(entity)
          }
        }
        """, PsiMethod.class);
  }
}
