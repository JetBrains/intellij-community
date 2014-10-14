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
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyResultOfAssignmentUsedInspection
import org.jetbrains.plugins.groovy.codeInspection.confusing.GrUnusedIncDecInspection
import org.jetbrains.plugins.groovy.codeInspection.unusedDef.UnusedDefInspection

/**
 * @author Max Medvedev
 */
class GrUnusedDefTest extends GrHighlightingTestBase {
  @Override
  InspectionProfileEntry[] getCustomInspections() {
    return [new UnusedDefInspection(), new GrUnusedIncDecInspection(), new GroovyUnusedDeclarationInspection(), new UnusedDeclarationInspectionBase(true), new GroovyResultOfAssignmentUsedInspection()] as InspectionProfileEntry[]
  }

  public void testUnusedVariable() { doTest() }

  public void testDefinitionUsedInClosure() { doTest() }

  public void testDefinitionUsedInClosure2() { doTest() }

  public void testDefinitionUsedInSwitchCase() { doTest() }

  public void testUnusedDefinitionForMethodMissing() { doTest()}

  public void testPrefixIncrementCfa() { doTest() }

  public void testIfIncrementElseReturn() { doTest() }

  public void testSwitchControlFlow() { doTest()}

  public void testUsageInInjection() { doTest() }

  public void testUnusedDefsForArgs() { doTest() }

  public void testUsedDefBeforeTry1() { doTest() }

  public void testUsedDefBeforeTry2() { doTest() }

  public void testUnusedInc() { doTest() }

  public void testUsedInCatch() { doTest() }

  public void testGloballyUnusedSymbols() { doTest() }

  public void testGloballyUnusedInnerMethods() {
    myFixture.addClass 'package junit.framework public class TestCase {}'
    doTest()
  }

  public void testUnusedParameter() { doTest() }

  public void testSuppressUnusedMethod() {
    testHighlighting('''\
class <warning descr="Class Foo is unused">Foo</warning> {
    @SuppressWarnings("GroovyUnusedDeclaration")
    static def foo(int x) {
        print 2
    }

    static def <warning descr="Method bar is unused">bar</warning>() {}
}
''')
  }

  void testUsedVar() {
    testHighlighting('''\
def <warning descr="Method foo is unused">foo</warning>(xxx) {
  if ((<warning descr="Result of assignment expression used">xxx = 5</warning>) || xxx) {
    <warning descr="Result of assignment expression used"><warning descr="Assignment is not used">xxx</warning>=4</warning>
  }
}

def <warning descr="Method foxo is unused">foxo</warning>(doo) {
  def xxx = 'asdf'
  if (!doo) {
    println xxx
    <warning descr="Result of assignment expression used"><warning descr="Assignment is not used">xxx</warning>=5</warning>
  }
}

def <warning descr="Method bar is unused">bar</warning>(xxx) {
  print ((<warning descr="Result of assignment expression used">xxx=5</warning>)?:xxx)
}

def <warning descr="Method a is unused">a</warning>(xxx) {
  if (2 && (<warning descr="Result of assignment expression used">xxx=5</warning>)) {
    xxx
  }
  else {
  }
}
''')
  }

  void testFallthroughInSwitch() {
    testHighlighting('''\
def <warning descr="Method f is unused">f</warning>(String foo, int mode) {
    switch (mode) {
        case 0: foo = foo.reverse()
        case 1: return foo
    }
}

def <warning descr="Method f2 is unused">f2</warning>(String foo, int mode) {
    switch (mode) {
        case 0: <warning descr="Assignment is not used">foo</warning> = foo.reverse()
        case 1: return 2
    }
}
''')
  }

  void testUnusedUnassignedVar() {
    testHighlighting('''\
def <warning descr="Variable is not used">abc</warning>
''')
  }

}
