// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.inspections;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.confusing.GroovyGStringKeyInspection;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixtureKt.grHighlightingFixture;

@TestApplication
public class GroovyGStringKeyInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<GrHighlightingFixture> highlighting =
    grHighlightingFixture(projectFixture, GrHighlightingFixture.getHighlightingTestDataPath(), () -> new GroovyGStringKeyInspection[]{new GroovyGStringKeyInspection()});

  @Test
  public void testMapLiteral() {
    highlighting.get().doTestHighlighting("""
                         def key = 'key'
                         [<warning>"${key}"</warning>: 'value', (key): "${key}"]
                         """);
  }

  @Test
  public void testMapLiteralGStringFromClosure() {
    highlighting.get().doTestHighlighting("""
                         def key = 'foo'
                         [<warning>({"${key}"}())</warning>: 'bar']
                         """);
  }

  @Test
  public void testSeveralElementsInMapLiteral() {
    highlighting.get().doTestHighlighting("""
                         def key = 'foo'
                         [<warning>"${key}"</warning>: 'bar', <warning>"${key}2"</warning>: 'bar2']
                         """);
  }

  @Test
  public void testCategoryInMapLiteral() {
    highlighting.get().doTestHighlighting("""
                         class GStringCategory {
                           static GString gstring(String str) {
                               "${str}"
                           }
                         }
                         use (GStringCategory) {
                           [<warning>('fo'.gstring())</warning> : 'bar']
                         }
                         """);
  }

  @Test
  public void testGStringPutCall() {
    highlighting.get().doTestHighlighting("""
                         def key = 'foo'
                         def map = [:]
                         map.put(<warning>"${key}"</warning>, "${key}")
                         """);
  }

  @Test
  public void testGStringPutCallSkipParentheses() {
    highlighting.get().doTestHighlighting("""
                         def key = 'foo'
                         def map = [:]
                         map.put <warning>"${key}"</warning>, 'bar'
                         """);
  }

  @Test
  public void testGStringOverloadedPutCall() {
    highlighting.get().doTestHighlighting("""
                         public class StrangeMap extends HashMap<String, String> {
                           public void put(GString str, int k) {
                           }
                         }
                         new StrangeMap().put("${key}", 1)
                         """);
  }

  @Test
  public void testPutAtLiteralCall() {
    highlighting.get().doTestHighlighting("""
                         def key = 'foo'
                         [:]."${key}"='bar'
                         """);
  }

  @Test
  public void test_do_not_highlight_null() {
    highlighting.get().doTestHighlighting("[(null):1]");
  }
}
