// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.inspections

import com.intellij.codeInspection.InspectionProfileEntry
import org.jetbrains.plugins.groovy.codeInspection.confusing.GroovyGStringKeyInspection
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase

class GroovyGStringKeyInspectionTest extends GrHighlightingTestBase {
  @Override
  InspectionProfileEntry[] getCustomInspections() { [new GroovyGStringKeyInspection()] }

  void testMapLiteral() {
    doTestHighlighting('''
      def key = 'key'
      [<warning>"${key}"</warning>: 'value', (key): "${key}"]
    ''')
  }
  void testMapLiteralGStringFromClosure() {
    doTestHighlighting('''
      def key = 'foo'
      [<warning>({"${key}"}())</warning>: 'bar']
    ''')
  }

  void testSeveralElementsInMapLiteral() {
    doTestHighlighting('''
      def key = 'foo'
      [<warning>"${key}"</warning>: 'bar', <warning>"${key}2"</warning>: 'bar2']
    ''')
  }

  void testCategoryInMapLiteral() {
    doTestHighlighting('''
      class GStringCategory {
        static GString gstring(String str) {
            "${str}"
        }
      }
      use (GStringCategory) {
        [<warning>('fo'.gstring())</warning> : 'bar']
      }
    ''')
  }

  void testGStringPutCall() {
    doTestHighlighting('''
      def key = 'foo'
      def map = [:]
      map.put(<warning>"${key}"</warning>, "${key}")
    ''')
  }

  void testGStringPutCallSkipParentheses() {
    doTestHighlighting('''
      def key = 'foo'
      def map = [:]
      map.put <warning>"${key}"</warning>, 'bar'
    ''')
  }

  void testGStringOverloadedPutCall() {
    doTestHighlighting('''
      public class StrangeMap extends HashMap<String, String> {
        public void put(GString str, int k) {
        }
      }
      new StrangeMap().put("${key}", 1)
    ''')
  }

  void testPutAtLiteralCall() {
    doTestHighlighting('''
      def key = 'foo'
      [:]."${key}"='bar'
    ''')
  }

  void 'test do not highlight null'() {
    doTestHighlighting '[(null):1]'
  }
}