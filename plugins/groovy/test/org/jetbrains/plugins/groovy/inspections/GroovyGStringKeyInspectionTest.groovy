/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.inspections

import com.intellij.codeInspection.InspectionProfileEntry
import org.jetbrains.plugins.groovy.codeInspection.confusing.GroovyGStringKeyInspection
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase

class GroovyGStringKeyInspectionTest extends GrHighlightingTestBase {
  @Override
  InspectionProfileEntry[] getCustomInspections() { [new GroovyGStringKeyInspection()] }

  void testMapLiteral() {
    testHighlighting('''
      def key = 'key'
      [<warning>"${key}"</warning>: 'value', (key): "${key}"]
    ''')
  }
  void testMapLiteralGStringFromClosure() {
    testHighlighting('''
      def key = 'foo'
      [<warning>({"${key}"}())</warning>: 'bar']
    ''')
  }

  void testSeveralElementsInMapLiteral() {
    testHighlighting('''
      def key = 'foo'
      [<warning>"${key}"</warning>: 'bar', <warning>"${key}2"</warning>: 'bar2']
    ''')
  }

  void testCategoryInMapLiteral() {
    testHighlighting('''
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
    testHighlighting('''
      def key = 'foo'
      def map = [:]
      map.put(<warning>"${key}"</warning>, "${key}")
    ''')
  }

  void testGStringPutCallSkipParentheses() {
    testHighlighting('''
      def key = 'foo'
      def map = [:]
      map.put <warning>"${key}"</warning>, 'bar'
    ''')
  }

  void testGStringOverloadedPutCall() {
    testHighlighting('''
      public class StrangeMap extends HashMap<String, String> {
        public void put(GString str, int k) {
        }
      }
      new StrangeMap().put("${key}", 1)
    ''')
  }

  void testPutAtLiteralCall() {
    testHighlighting('''
      def key = 'foo'
      [:]."${key}"='bar'
    ''')
  }

  void 'test do not highlight null'() {
    testHighlighting '[(null):1]'
  }
}