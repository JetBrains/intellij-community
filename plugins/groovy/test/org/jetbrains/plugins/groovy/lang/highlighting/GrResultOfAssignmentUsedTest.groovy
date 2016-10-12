/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyResultOfAssignmentUsedInspection

class GrResultOfAssignmentUsedTest extends GrHighlightingTestBase {
  final inspection = new GroovyResultOfAssignmentUsedInspection()

  @Override
  InspectionProfileEntry[] getCustomInspections() { [inspection] }

  final warnStart = '<warning descr="Result of assignment expression used">'
  final warnEnd = '</warning>'

  void testUsedVar() {
    testHighlighting """
      def foo(a) {
        if ((${warnStart}a = 5${warnEnd}) || a) {
          ${warnStart}a = 4${warnEnd}
        }
      }

      def foo2(a) {
        def b = 'b'
        if (!a) {
          println b
          b = 5
        }                            
        return 0 // make b = 5 not a return statement
      }

      def bar(a) {
        print ((${warnStart}a = 5${warnEnd})?:a)
      }

      def a(b) {
        if (2 && (${warnStart}b = 5${warnEnd})) {
          b
        }
      }
    """
  }

  void testResultOfAssignmentUsedInspection() {
    testHighlighting """
      if ((${warnStart}a = b${warnEnd}) == null) {
      }
    """

    testHighlighting """
      while (${warnStart}a = b${warnEnd}) {
      }
    """

    testHighlighting """
      for (i = 0; ${warnStart}a = b${warnEnd}; i++) {
      }
    """

    testHighlighting """
      System.out.println(${warnStart}a = b${warnEnd})
    """

    testHighlighting """
      (${warnStart}a = b${warnEnd}).each {}
    """
  }

  void testInspectClosuresOption_isTrue() {
    inspection.inspectClosures = true
    testHighlighting 'a = 1 '
    testHighlighting "a(0, { ${warnStart}b = 1${warnEnd} }, 2)"
    testHighlighting "def a = { ${warnStart}b = 1${warnEnd} }"
    testHighlighting "a(0, ${warnStart}b = 1${warnEnd}, 2)"
    testHighlighting "def a() { ${warnStart}b = 1${warnEnd} }"
    inspection.inspectClosures = false
  }

  void testInspectClosuresOption_isFalse() {
    inspection.inspectClosures = false
    testHighlighting 'a = 1 '
    testHighlighting 'a(0, { b = 1 }, 2)'
    testHighlighting "def a = { b = 1 }"
    testHighlighting "a(0, ${warnStart}b = 1${warnEnd}, 2)"
    testHighlighting "def a() { ${warnStart}b = 1${warnEnd} }"
  }
}
