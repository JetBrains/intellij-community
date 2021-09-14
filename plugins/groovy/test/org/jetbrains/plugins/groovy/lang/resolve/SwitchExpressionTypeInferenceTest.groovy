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


  void _testLUB() {
    doTest """
class A {}
class B extends A {}
class C extends A {}
def xx = switch(10) {
  case 10 -> new B()
  case 20 -> new C()
}""", "A"
  }

  void _testBlock() {
    doTest """
class A {}
class B extends A {}
class C extends A {}
def xx = switch(10) {
  case 10 -> {
    new B()
  }
  case 20 -> new C()
}""", "A"
  }

  void _testYield() {
    doTest """
def xx = switch(10) {
    case 10 -> yield 10
}
x<caret>x
""", "java.lang.Integer"
  }


  void _testYieldInBlock() {
    doTest """
def xx = switch(10) {
    case 10 -> {
      yield 10
    }
}
x<caret>x
""", "java.lang.Integer"
  }

  void _testYieldBeforeLastStatement() {
    doTest """
def xx = switch(10) {
    case 10 -> {
      yield 10
      "20"
    }
}
x<caret>x
""", "java.lang.Integer"
  }

  void _testConditionalYield() {
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
      "20"
    }
}
x<caret>x
""", "A"
  }
}
