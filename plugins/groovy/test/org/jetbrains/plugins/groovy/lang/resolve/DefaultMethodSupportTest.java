// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod;
import org.junit.Assert;

public class DefaultMethodSupportTest extends GroovyResolveTestCase {
  public void testSuperReferenceWithQualifier() {
    PsiMethod method = resolveByText(
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
    Assert.assertEquals("A", method.getContainingClass().getName());
  }

  public void testSuperReferenceWithQualifier2() {
    PsiMethod method = resolveByText(
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
    Assert.assertEquals("B", method.getContainingClass().getName());
  }

  public void testClashingMethods() {
    GrTraitMethod method = resolveByText(
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
    Assert.assertEquals("B", method.getPrototype().getContainingClass().getName());
  }

  public void testDefaultMethodFromAsOperator1() {
    resolveByText(
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

  public void testDefaultMethodFromAsOperator2() {
    resolveByText(
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

  public void testDefaultMethodFromAssigning() {
    resolveByText(
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

  public void testDefaultMethodFromJavaInterface() {
    myFixture.addClass(
      """
        interface IServiceJava<T> {
            default void save(T entity) {
                System.out.println(entity);
            }
        }
        """);
    resolveByText(
      """
        class TestGenericGroovy implements IServiceJava<String> {
          void save(String entity) {
            super.sa<caret>ve(entity)
          }
        }
        """, PsiMethod.class);
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return projectDescriptor;
  }

  private final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_3_0;
}
