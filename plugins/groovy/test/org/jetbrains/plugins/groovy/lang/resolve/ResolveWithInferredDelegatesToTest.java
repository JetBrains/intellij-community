// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter;

public class ResolveWithInferredDelegatesToTest extends GroovyResolveTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST;

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return projectDescriptor;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).setValue(true);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).resetToDefault();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testInferredDelegation() {
    assertScript("""
      def foo(closure) {
        closure.delegate = 1
      }

      foo {
        byt<caret>eValue()
      }
      """, "java.lang.Integer");
  }

  public void testInferredDelegationFromDgm() {
    assertScript("""
      def foo(closure) {
        1.with closure
      }

      foo {
        byt<caret>eValue()
      }""", "java.lang.Integer");
  }

  public void testInferredDelegationFromTypeParameter() {
    assertScript("""
      class A<T> {

        def foo(closure) {
          (null as T).with closure
        }
      }

      (new A<Integer>()).foo {
        byt<caret>eValue()
      }""", "java.lang.Integer");
  }

  @SuppressWarnings("SameParameterValue")
  private void assertScript(String text, String resolvedClass) {
    final var resolved = resolveByText(text, PsiMethod.class);
    final String containingClass = resolved.getContainingClass().getQualifiedName();
    assertEquals(resolvedClass, containingClass);
  }
}