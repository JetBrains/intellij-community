// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve


import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors

class SwitchExpressionTypeInferenceTest extends TypeInferenceTestBase {
  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_4_0

  void testSimple() {
    doTest '''
def xx = switch(10) {
    case 10 -> 10
}
x<caret>x
''', "java.lang.Integer"
  }


  void testLUB() {
    doTest """
class A {}
class B extends A {}
class C extends A {}
def xx = switch(10) {
  case 10 -> new B()
  case 20 -> new C()
}
x<caret>x
""", "A"
  }

  void testBlock() {
    doTest """
class A {}
class B extends A {}
class C extends A {}
def xx = switch(10) {
  case 10 -> {
    new B()
  }
  case 20 -> new C()
}
x<caret>x
""", "A"
  }

  void testYield() {
    doTest """
def xx = switch(10) {
    case 10 -> yield 10
}
x<caret>x
""", "java.lang.Integer"
  }


  void testYieldInBlock() {
    doTest """
def xx = switch(10) {
    case 10 -> {
      yield 10
    }
}
x<caret>x
""", "java.lang.Integer"
  }

  void testConditionalYield() {
    doTest """
class A {}
class B extends A {}
class C extends A {}
def xx = switch(10) {
    case 10 -> {
      if (true) {
        yield new B()
      } else {
        yield new C()
      }
    }
}
x<caret>x
""", "A"
  }

  void testFlowThroughSwitch() {
    doTest """
class A {}
class B extends A {}
class C extends A {}

def bb = 0

def x = switch (a) {
    case 20 -> bb = new B()
    default -> bb = new C()
}
b<caret>b""", 'A'
  }

  void testNestedYields() {
    doTest """
def xx = switch (10)  {
    case 1..10 -> {
        def r = switch (40) {
            case 20 -> yield ""
        }
    }
    default -> yield 50
}
x<caret>x""", 'java.lang.Integer'
  }

  void testTypeSwitching() {
    doTest """
def xx

def _ = switch (xx) {
    case String -> x<caret>x
}""", 'java.lang.String'
  }

  void testSwitchOnSeveralTypes() {
    doTest """
class A {}
class B extends A {}
class C extends A {}

def xx

def _ = switch (xx) {
    case B, C -> x<caret>x
}""", 'A'
  }

  void testMatchInOtherBranch() {
    doTest """
def foo(xx) {
    def _ = switch (xx) {
        case String  -> xx.charAt(10)
        case Integer -> x<caret>x
    }
}""", 'java.lang.Integer'
  }

  void testMatchOnTypesInSwitchStatement() {
    doTest """
def foo(xx) {
    switch (xx) {
        case String  -> xx.charAt(10)
        case Integer -> x<caret>x
    }
}""", 'java.lang.Integer'
  }

  void testMatchingWithColons() {
    doTest """
class A {}
class B extends A {}
class C extends A {}

def foo(xx) {
    def _ = switch (xx) {
        case B: 
        case C:
          yield x<caret>x
    }
}""", 'A'
  }

}
