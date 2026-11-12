// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixtureKt.grHighlightingFixture;

/**
 * @author Max Medvedev
 */
@TestApplication
public class GrAssignabilityClosureToSamTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_2);
  private final TestFixture<GrHighlightingFixture> highlighting =
    grHighlightingFixture(projectFixture, GrHighlightingFixture.getHighlightingTestDataPath(), () -> new GroovyAssignabilityCheckInspection[]{new GroovyAssignabilityCheckInspection()});

  @Test
  public void testAssignability() {
    highlighting.get().doTestHighlighting("""
                         interface A {
                           def foo()
                         }
                         interface B {}

                         A a = {print 1}
                         A a1 = {->print 1}
                         A a2 = {String s->print 1}
                         B <warning>b</warning> = {print 2}
                         """);
  }

  @Test
  public void testAmbiguous() {
    boolean registryValue = Registry.is(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE);
    Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).setValue(true);
    try {
      highlighting.get().doTestHighlighting("""
                           interface SAM1 { def foo(String s)}
                           interface SAM2 { def bar(Integer i)}

                           def method(x, SAM1 s1){s1.foo(x)}
                           def method(x, SAM2 s2){s2.bar(x)}

                           method <warning>(1)</warning>   {it}  // fails because SAM1 and SAM2 are seen as equal
                           method <warning>("1")</warning> {it}  // fails because SAM1 and SAM2 are seen as equal

                           """);
    }
    finally {
      Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).setValue(registryValue);
    }
  }

  @Test
  public void testGenerics() {
    highlighting.get().doTestHighlighting("""
                         interface A<T> {
                           def foo(T t)
                         }

                         A<String> a1 = {print 1}
                         A<String> a2 = {String s -> print 1}
                         A<String> a3 = {int s -> print 1}
                         A a4 = {int s -> print 1}
                         A a7 = {int s, String y -> print 1}
                         A a5 = { print 1}
                         A a6 = {x -> print 1}
                         """);
  }

  @Test
  public void testGenerics2() {
    highlighting.get().doTestHighlighting("""
                         interface A<T> {
                           def foo(T t)
                         }

                         interface B extends A<String> {}

                         B b1 = {print 1}
                         B b2 = {String s -> print 1}
                         B b3 = {int s -> print 1}
                         """);
  }
}
