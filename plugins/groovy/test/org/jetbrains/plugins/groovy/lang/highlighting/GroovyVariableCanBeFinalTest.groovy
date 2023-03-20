// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import org.jetbrains.plugins.groovy.codeInspection.dataflow.GroovyVariableCanBeFinalInspection

class GroovyVariableCanBeFinalTest extends GrHighlightingTestBase {

  @Override
  InspectionProfileEntry[] getCustomInspections() {
    return [new GroovyVariableCanBeFinalInspection()]
  }

  void testSimpleLocalVariable() {
    doTestHighlighting('''
def simpleLocalVariableTest() {
    def <warning descr="Variable 'a' can be final">a</warning> = 1
    final def b = a
    println b
}
''')
  }

  void testClosureParam() {
    doTestHighlighting('''
def closureParamTest() {
    { <warning descr="Parameter 'a' can be final">a</warning> -> print a }
}
''')
  }

  void testClosureOuterScope() {
    doTestHighlighting('''
def closureOuterScopeTest() {
    def <warning descr="Variable 'outer' can be final">outer</warning> = 1
    def outer2 = 2
    final def outer3 = 3
    return { final aa ->
        outer2 = 2
        print aa
        aa + outer2 + outer + outer3
    }
}

''')
  }

  void testAnonymousClass() {
    doTestHighlighting('''
Runnable foo() {
    def <warning descr="Variable 'a' can be final">a</warning> = 1
    def b = 2             // more than 1 assignment
    final def c = 3       // already final
    new Runnable() {
        @Override
        void run() {
            b = 3
            print a
            print b
            print c
        }
    }
}
''')
  }

  void testMethodParameter() {
    doTestHighlighting('''
def methodParameterTest(String <warning descr="Parameter 's' can be final">s</warning>, String ss, final String sss) {
    ss = s
    println ss
    println sss
}
''')
  }

  void testFor() {
    doTestHighlighting('''
def forTest() {
    for (int <warning descr="Variable 'i' can be final">i</warning> in 1..<10) {
        println i
    }
    for (def <warning descr="Variable 'a' can be final">a</warning> : arr) {
        print a
    }
    for (i = 0; i < 10; i++) {
        println i
    }
    for (final def a : arr) {
        print a
    }
}
''')
  }

  void testTernary() {
    doTestHighlighting('''
def ternaryTest() {
    def <warning descr="Variable 'ternary' can be final">ternary</warning> = rand() ? rand() : 500
    final f = ternary
    println f
}
''')
  }

  void testTernaryDeep() {
    doTestHighlighting('''
def deepTernaryTest() {
    def <warning descr="Variable 'ternary' can be final">ternary</warning> = rand() ? (rand() ? 100 : 500) : (rand() ? { -> } : 300)
    final f = ternary
    println f
}
''')
  }

  void testSwitch() {
    doTestHighlighting('''
def switchTest() {
    def <warning descr="Variable 'sw' can be final">sw</warning>
    switch (rand()) {
        case true:
            sw = 3
            break
        case false:
            sw = "I'm false"
            break
        default:
            throw new RuntimeException()
    }
    println sw
}
''')
  }

  void testDeepSwitch() {
    doTestHighlighting('''
def deepSwitchTest() {
    def <warning descr="Variable 'sw' can be final">sw</warning>
    switch (rand()) {
        case true:
            sw = 1;
            break
        case false:
            switch (rand()) {
                case true:
                    sw = "True";
                    break
                case false:
                    sw = " False ";
                    break
                default:
                    throw new RuntimeException()
            }
            break
        default:
            sw = 2;
            break
    }
    println sw
}
''')
  }

  void testIf() {
    doTestHighlighting('''
def ifTest() {
    def <warning descr="Variable 'a' can be final">a</warning>
    if (rand()) a = 1
    else a = "lol"
    final def b = a   //already final
    println b
}
''')
  }

  void testIfTriBranch() {
    doTestHighlighting('''
def triBranchIfTest() {
    def <warning descr="Variable 'a' can be final">a</warning>
    if (rand()) {
        a = 1
    } else if (rand()) {
        a = "lol"
    } else {
        a = "non-lol"
    }
    def <warning descr="Variable 'b' can be final">b</warning> = a
    println b
}
''')
  }

  void testIfDeep() {
    doTestHighlighting('''
def deepIfTest() {
    def <warning descr="Variable 'a' can be final">a</warning>
    if (rand()) {
        if (rand()) {
            a = 1
        } else {
            a = "lol"
        }
    } else {
        if (rand()) {
            a = "2"
        } else {
            a = "za"
        }
    }
    final def b = a // already final
    println b
}
''')
  }

  void testSameVariableName() {
    doTestHighlighting('''
 def testSameVariableName() {
    if (rand()) {
        def <warning descr="Variable 'a' can be final">a</warning> = 5
        print a
    }
    if (rand()) {
        def <warning descr="Variable 'a' can be final">a</warning> = 6
        print a
    }
}
''')
  }

  void testSeveralAssignments() {
    doTestHighlighting('''
def testSeveralAssignments() {
    def a = 1
    a = 2
    a = b
    print a
}
''')
  }
}
