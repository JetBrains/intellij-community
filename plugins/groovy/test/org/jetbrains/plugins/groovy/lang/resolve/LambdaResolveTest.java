// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixtureKt.groovyResolveFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class LambdaResolveTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_3_0);
  private final TestFixture<GroovyResolveFixture> resolve = groovyResolveFixture(projectFixture);

  @Test
  public void testResolveUpperBoundTypeMethod() {
    PsiMethod method = resolve.get().resolveByText(
      """
        @groovy.transform.CompileStatic
        def filter(Collection<? extends Number> numbers) {
            numbers.findAll(it -> it.double<caret>Value())
        }
        """, PsiMethod.class);
    ReadAction.runBlocking(() -> {
      assertEquals("java.lang.Number", method.getContainingClass().getQualifiedName());
    });
  }

  @Test
  public void testIntersect() {
    resolve.get().resolveByText(
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

  @Test
  public void testInferPlusType() {
    resolve.get().resolveByText(
      """
        [[1, 2, 3], [2, 3], [0, 2]].
          collect((it) -> {it + [56]}).
          findAll(it -> {it.si<caret>ze() >= 3})
        """, PsiMethod.class);
  }

  @Test
  public void testStringInjectionDontOverrideItParameter() {
    resolve.get().resolveByText(
      """
        [2, 3, 4].collect (it) -> {"${it.toBigDeci<caret>mal()}"}
        """, PsiMethod.class);
  }

  @Test
  public void testRuntimeMixin() {
    resolve.get().resolveByText(
      """
        class ReentrantLock {}
        
        ReentrantLock.metaClass.withLock = (nestedCode) -> {}
        
        new ReentrantLock().withLock(()-> {
            withL<caret>ock(2)
        })
        """);
  }

  @Test
  public void testMixin() {
    resolve.get().resolveByText(
      """
        def foo() {
            Integer.metaClass.abc = () -> { print 'something' }
            1.a<caret>bc()
        }
        """, PsiMethod.class);
  }

  @Test
  public void testCurry() {
    resolve.get().resolveByText(
      """
        def c = (int i, j, k) -> i
        
        c.curry(3, 4).call(5).<caret>intValue()
        """, PsiMethod.class);
  }
}
