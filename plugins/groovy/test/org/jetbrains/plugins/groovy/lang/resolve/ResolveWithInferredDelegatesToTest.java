// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixtureKt.groovyResolveFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class ResolveWithInferredDelegatesToTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<GroovyResolveFixture> resolve = groovyResolveFixture(projectFixture);

  @BeforeEach
  void setUp() {
    Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).setValue(true);
  }

  @AfterEach
  void tearDown() {
    Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).resetToDefault();
  }

  @Test
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

  @Test
  public void testInferredDelegationFromDgm() {
    assertScript("""
      def foo(closure) {
        1.with closure
      }

      foo {
        byt<caret>eValue()
      }""", "java.lang.Integer");
  }

  @Test
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
    final var resolved = resolve.get().resolveByText(text, PsiMethod.class);
    ReadAction.runBlocking(() -> {
      final String containingClass = resolved.getContainingClass().getQualifiedName();
      assertEquals(resolvedClass, containingClass);
    });
  }
}