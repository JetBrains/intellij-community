// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.fixes

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase

class GrMultipleAssignmentTest extends GrHighlightingTestBase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  InspectionProfileEntry[] getCustomInspections() { [new GroovyAssignabilityCheckInspection()] }

  @Override
  void setUp() throws Exception {
    super.setUp()
  }

  void testReplaceListWithLiteral() {
    doTest '''
import groovy.transform.CompileStatic

@CompileStatic
def foo() {
    def list = [1, 2]
    def (a, b) = l<caret>ist
}
''', '''
import groovy.transform.CompileStatic

@CompileStatic
def foo() {
    def list = [1, 2]
    def (a, b) = [list[0], list[1]]
}
'''
  }

  void testReplaceCallWithLiteral() {
    doTest '''
import groovy.transform.CompileStatic

@CompileStatic
def foo() {
    def (a, b) = bar()
}

def bar() { [] }
''', '''
import groovy.transform.CompileStatic

@CompileStatic
def foo() {
    def objects = bar()
    def (a, b) = [objects[0], objects[1]]
}

def bar() { [] }
'''
  }



  private void doTest(String before, String after) {
    fixture.with {
      configureByText '_.groovy', before
      enableInspections(customInspections)
      def fixes = getAllQuickFixes('_.groovy')
      assert fixes.size() == 1: before
      launchAction fixes.first()
      checkResult after
    }
  }

}