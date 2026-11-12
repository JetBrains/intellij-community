// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.resolve.TypeInferenceFixtureKt.typeInferenceFixture;

@TestApplication
public class TypeInferenceFromOuterCallsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<TypeInferenceFixture> inference = typeInferenceFixture(projectFixture);

  @BeforeEach
  void setUp() {
    Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).setValue(true);
  }

  @AfterEach
  void tearDown() {
    Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).resetToDefault();
  }

  @Test
  public void test_outer_call_influence() {
    inference.get().doTest("""
             def foo(a) {
               <caret>a
             }
             
             foo(1)
             """, JAVA_LANG_INTEGER);
  }

  @Test
  public void test_implicit__ClosureParams() {
    inference.get().doTest("""
             def foo(a, c) {
               c(a)
             }
             
             foo(1) { <caret>it }
             """, JAVA_LANG_INTEGER);
  }

  @Test
  public void test_implicit__ClosureParams_with_nontrivial_substitutor() {
    inference.get().doTest("""
             class A<T> {
                 T t = null
                 def foo(c) {
                     c(t)
                 }
             }
             
             (new A<Integer>()).foo {
                 <caret>it
             }""", JAVA_LANG_INTEGER);
  }

  @Test
  public void test_implicit__ClosureParams_with_generified_method() {
    inference.get().doTest("""
             def foo(a, b) { b(a) }
             
             foo(1) {}
             foo('q') { <caret>it }
             """, JAVA_LANG_STRING);
  }

  @Test
  public void test_implicit__ClosureParams_with_two_parameters() {
    inference.get().doTest("""
             def foo(cl) {
                 cl(1, 'q')
             }
             
             foo { a, b ->
                 <caret>a
             }""", JAVA_LANG_INTEGER);
  }

  @Test
  public void test_DFA_priority_is_higher_than_signature_inference() {
    inference.get().doTest("""
             def foo(a) {
               if (a instanceof Integer) {
                 <caret>a
               }
             }
             
             foo(null as Number)
             """, JAVA_LANG_INTEGER);
  }
}
