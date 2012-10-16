/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import org.jetbrains.plugins.groovy.codeInspection.unassignedVariable.UnassignedVariableAccessInspection

/**
 * @author Max Medvedev
 */
class GrUnassignedVariableAccessTest extends GrHighlightingTestBase {
  @Override
  InspectionProfileEntry[] getCustomInspections() {
    [new UnassignedVariableAccessInspection()] as InspectionProfileEntry[]
  }

  public void testUnassigned1() { doTest() }

  public void testUnassigned2() { doTest() }

  public void testUnassigned3() { doTest() }

  public void testUnassigned4() { doTest() }

  public void testUnassignedTryFinally() { doTest() }


  void testVarIsNotInitialized() {
    testHighlighting('''\
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

def bar() {
  def p
  print <warning descr="Variable 'p' might not be assigned">p</warning>
}
''')
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

  public void testVarNotAssigned() { doTest() }

  public void testMultipleVarNotAssigned() { doTest() }

  public void testForLoopWithNestedEndlessLoop() {doTest()}
}
