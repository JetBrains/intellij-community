// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.inspections;

import com.intellij.codeInspection.InspectionProfileEntry;
import org.jetbrains.plugins.groovy.codeInspection.confusing.GroovyGStringKeyInspection;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase;

public class GroovyGStringKeyInspectionTest extends GrHighlightingTestBase {
  @Override
  public InspectionProfileEntry[] getCustomInspections() { return new GroovyGStringKeyInspection[]{new GroovyGStringKeyInspection()}; }

  public void testMapLiteral() {
    doTestHighlighting("""
                         def key = 'key'
                         [<warning>"${key}"</warning>: 'value', (key): "${key}"]
                         """);
  }

  public void testMapLiteralGStringFromClosure() {
    doTestHighlighting("""
                         def key = 'foo'
                         [<warning>({"${key}"}())</warning>: 'bar']
                         """);
  }

  public void testSeveralElementsInMapLiteral() {
    doTestHighlighting("""
                         def key = 'foo'
                         [<warning>"${key}"</warning>: 'bar', <warning>"${key}2"</warning>: 'bar2']
                         """);
  }

  public void testCategoryInMapLiteral() {
    doTestHighlighting("""
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

  public void testGStringPutCall() {
    doTestHighlighting("""
                         def key = 'foo'
                         def map = [:]
                         map.put(<warning>"${key}"</warning>, "${key}")
                         """);
  }

  public void testGStringPutCallSkipParentheses() {
    doTestHighlighting("""
                         def key = 'foo'
                         def map = [:]
                         map.put <warning>"${key}"</warning>, 'bar'
                         """);
  }

  public void testGStringOverloadedPutCall() {
    doTestHighlighting("""
                         public class StrangeMap extends HashMap<String, String> {
                           public void put(GString str, int k) {
                           }
                         }
                         new StrangeMap().put("${key}", 1)
                         """);
  }

  public void testPutAtLiteralCall() {
    doTestHighlighting("""
                         def key = 'foo'
                         [:]."${key}"='bar'
                         """);
  }

  public void test_do_not_highlight_null() {
    doTestHighlighting("[(null):1]");
  }
}
