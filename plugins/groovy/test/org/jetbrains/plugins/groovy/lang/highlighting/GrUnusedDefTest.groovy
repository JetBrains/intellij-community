// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection
import org.jetbrains.plugins.groovy.codeInspection.confusing.GrUnusedIncDecInspection
import org.jetbrains.plugins.groovy.codeInspection.unusedDef.UnusedDefInspection

/**
 * @author Max Medvedev
 */
class GrUnusedDefTest extends GrHighlightingTestBase {
  @Override
  InspectionProfileEntry[] getCustomInspections() {
    [new UnusedDefInspection(),
     new GrUnusedIncDecInspection(),
     new GroovyUnusedDeclarationInspection(),
     new UnusedDeclarationInspectionBase(true)]
  }

  void testUnusedVariable() { doTest() }

  void testDefinitionUsedInClosure() { doTest() }

  void testDefinitionUsedInClosure2() { doTest() }

  void testDefinitionUsedInSwitchCase() { doTest() }

  void testUnusedDefinitionForMethodMissing() { doTest() }

  void testPrefixIncrementCfa() { doTest() }

  void testIfIncrementElseReturn() { doTest() }

  void testSwitchControlFlow() { doTest() }

  void testUsageInInjection() { doTest() }

  void testUnusedDefsForArgs() { doTest() }

  void testUsedDefBeforeTry1() { doTest() }

  void testUsedDefBeforeTry2() { doTest() }

  void testUnusedInc() { doTest() }

  void testUsedInCatch() { doTest() }

  void testGloballyUnusedSymbols() { doTest() }

  void testGloballyUnusedInnerMethods() {
    myFixture.addClass 'package junit.framework; public class TestCase {}'
    doTest()
  }

  void testUnusedParameter() { doTest() }

  void testSuppressUnusedMethod() {
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
    testHighlighting '''
      def <warning descr="Method foo is unused">foo</warning>(xxx) {
        if ((xxx = 5) || xxx) {
          <warning descr="Assignment is not used">xxx</warning>=4
        }
      }

      def <warning descr="Method foxo is unused">foxo</warning>(doo) {
        def xxx = 'asdf'
        if (!doo) {
          println xxx
          <warning descr="Assignment is not used">xxx</warning>=5
        }
      }
    '''
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

  void 'test method referenced via incapplicable call is used'() {
    testHighlighting '''\
static boolean fsdasdfsgsdsfadfgs(a, b) { a == b }
def bar() { fsdasdfsgsdsfadfgs("s") }
bar()
'''
  }

  void 'test delegate'() {
    fixture.addClass '''
package groovy.lang;
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Delegate {}
'''

    testHighlighting '''\
class Foo {
  @Delegate
  Integer i
  @Delegate
  String bar() {}
}

new Foo()
'''
  }

  void 'test "unused" suppresses warning'() {
    testHighlighting '''\
@SuppressWarnings("unused")
class Aaaa {} 
'''
  }

  void 'test suppress with "unused"'() {
    testHighlighting '''\
class <caret><warning descr="Class Aaaa is unused">Aaaa</warning> {} 
'''
    def action = fixture.findSingleIntention 'Suppress'
    fixture.launchAction(action)

    fixture.checkResult '''\
@SuppressWarnings("unused")
class <caret>Aaaa {} 
'''
  }
}
