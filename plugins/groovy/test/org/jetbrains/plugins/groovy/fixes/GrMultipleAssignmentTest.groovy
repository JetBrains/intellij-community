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
    def list = bar()
    def (a, b) = [list[0], list[1]]
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