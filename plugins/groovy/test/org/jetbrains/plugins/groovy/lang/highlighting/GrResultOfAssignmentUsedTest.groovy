// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyResultOfAssignmentUsedInspection

class GrResultOfAssignmentUsedTest extends GrHighlightingTestBase {
  final inspection = new GroovyResultOfAssignmentUsedInspection()

  @Override
  InspectionProfileEntry[] getCustomInspections() { [inspection] }

  final warnStart = '<warning descr="Usage of assignment expression result">'
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
