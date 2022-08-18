// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    doTestHighlighting """
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
    doTestHighlighting """
      if ((${warnStart}a = b${warnEnd}) == null) {
      }
    """

    doTestHighlighting """
      while (${warnStart}a = b${warnEnd}) {
      }
    """

    doTestHighlighting """
      for (i = 0; ${warnStart}a = b${warnEnd}; i++) {
      }
    """

    doTestHighlighting """
      System.out.println(${warnStart}a = b${warnEnd})
    """

    doTestHighlighting """
      (${warnStart}a = b${warnEnd}).each {}
    """
  }

  void testInspectClosuresOption_isTrue() {
    inspection.inspectClosures = true
    doTestHighlighting 'a = 1 '
    doTestHighlighting "a(0, { ${warnStart}b = 1${warnEnd} }, 2)"
    doTestHighlighting "def a = { ${warnStart}b = 1${warnEnd} }"
    doTestHighlighting "a(0, ${warnStart}b = 1${warnEnd}, 2)"
    doTestHighlighting "def a() { ${warnStart}b = 1${warnEnd} }"
    inspection.inspectClosures = false
  }

  void testInspectClosuresOption_isFalse() {
    inspection.inspectClosures = false
    doTestHighlighting 'a = 1 '
    doTestHighlighting 'a(0, { b = 1 }, 2)'
    doTestHighlighting "def a = { b = 1 }"
    doTestHighlighting "a(0, ${warnStart}b = 1${warnEnd}, 2)"
    doTestHighlighting "def a() { ${warnStart}b = 1${warnEnd} }"
  }
}
