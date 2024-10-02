// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;

public class LambdaResolveTest extends GroovyResolveTestCase {
  public void testResolveUpperBoundTypeMethod() {
    PsiMethod method = resolveByText(
      """
        @groovy.transform.CompileStatic
        def filter(Collection<? extends Number> numbers) {
            numbers.findAll(it -> it.double<caret>Value())
        }
        """, PsiMethod.class);
    assert method.getContainingClass().getQualifiedName().equals("java.lang.Number");
  }

  public void testIntersect() {
    resolveByText(
      """
        class Base {
            void foo() {}
        }
        class D extends Base {}
        
        Closure cl
        boolean rand = Math.random() < 0.5
        if (rand)
            cl = (D  p) -> p
        else
            cl = (Base  p) -> p
        
        cl(new D()).<caret>foo()
        """, PsiMethod.class);
  }

  public void testInferPlusType() {
    resolveByText(
      """
        [[1, 2, 3], [2, 3], [0, 2]].
          collect((it) -> {it + [56]}).
          findAll(it -> {it.si<caret>ze() >= 3})
        """, PsiMethod.class);
  }

  public void testStringInjectionDontOverrideItParameter() {
    resolveByText(
      """
        [2, 3, 4].collect (it) -> {"${it.toBigDeci<caret>mal()}"}
        """, PsiMethod.class);
  }

  public void testRuntimeMixin() {
    resolveByText(
      """
        class ReentrantLock {}
        
        ReentrantLock.metaClass.withLock = (nestedCode) -> {}
        
        new ReentrantLock().withLock(()-> {
            withL<caret>ock(2)
        })
        """);
  }

  public void testMixin() {
    resolveByText(
      """
        def foo() {
            Integer.metaClass.abc = () -> { print 'something' }
            1.a<caret>bc()
        }
        """, PsiMethod.class);
  }

  public void testCurry() {
    resolveByText(
      """
        def c = (int i, j, k) -> i
        
        c.curry(3, 4).call(5).<caret>intValue()
        """, PsiMethod.class);
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return projectDescriptor;
  }

  private final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_3_0;
}
