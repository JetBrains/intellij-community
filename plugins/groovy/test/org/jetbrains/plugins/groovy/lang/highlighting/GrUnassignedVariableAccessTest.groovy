// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.codeInspection.unassignedVariable.UnassignedVariableAccessInspection

/**
 * @author Max Medvedev
 */
class GrUnassignedVariableAccessTest extends GrHighlightingTestBase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_3_0

  @Override
  InspectionProfileEntry[] getCustomInspections() {
    [new UnassignedVariableAccessInspection()] as InspectionProfileEntry[]
  }

  void testUnassigned1() { doTest() }

  void testUnassigned2() { doTest() }

  void testUnassigned3() { doTest() }

  void testUnassigned4() { doTest() }

  void testUnassignedTryFinally() { doTest() }

  void testVarIsNotInitialized() {
    testHighlighting '''\
def xxx() {
  def category = null
  for (def update : updateIds) {
    def p = update

    if (something) {
      category = p
    }

    print p
  }
}
'''
  }

  void 'test simple'() {
    testHighlighting '''\
def bar() {
  def p
  print <warning descr="Variable 'p' might not be assigned">p</warning>
}
'''
  }

  void 'test assigned after read in loop'() {
    testHighlighting '''\
def xxx() {
  def p
  for (def update : updateIds) {
    print <warning descr="Variable 'p' might not be assigned">p</warning>
    p = 1 
  }
}
'''
  }

  void testUnassignedAccessInCheck() {
    def inspection = new UnassignedVariableAccessInspection()
    inspection.myIgnoreBooleanExpressions = true

    myFixture.configureByText('_.groovy', '''\
def foo
if (foo) print 'fooo!!!'

def bar
if (bar!=null) print 'foo!!!'

def baz
if (<warning descr="Variable 'baz' might not be assigned">baz</warning> + 2) print "fooooo!"
''')
    myFixture.enableInspections(inspection)
    myFixture.testHighlighting(true, false, true)
  }

  void testVarNotAssigned() { doTest() }

  void testMultipleVarNotAssigned() { doTest() }

  void testForLoopWithNestedEndlessLoop() { doTest() }

  void testVariableAssignedOutsideForLoop() { doTest() }

  void testTryResource() { doTest() }
}
