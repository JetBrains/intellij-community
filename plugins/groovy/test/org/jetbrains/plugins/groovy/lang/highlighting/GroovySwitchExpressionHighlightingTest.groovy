// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.highlighting


import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrSwitchExhaustivenessCheckInspection
import org.jetbrains.plugins.groovy.util.HighlightingTest

@CompileStatic
class GroovySwitchExpressionHighlightingTest extends LightGroovyTestCase implements HighlightingTest {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_4_0

  void doTest(String text) {
    myFixture.configureByText("_.groovy", text)
    myFixture.doHighlighting()
    myFixture.checkResult(text)
  }

  void 'test no mixing arrows and colons'() {
    doTest '''
def x = switch (10) {
  case 20 <error>-></error> 10
  case 50<error>:</error>
    yield 5
}'''
  }

  void 'test require yield in colon-style switch expression'() {
    doTest '''
def x = switch (10) {
  <error>case</error> 20:
    40
}'''
  }

  void 'test forbid return in colon-style switch expression'() {
    doTest '''
def x = switch (10) {
  <error>case</error> 20:
    <error>return 40</error>
}'''
  }

  void 'test throw in colon-style switch expression'() {
    doTest '''
def x = switch (10) {
    case 20: 
        throw new IOException()
}'''
  }

  void 'test empty switch'() {
    doTest '''
def x = <error>switch</error> (10) {}'''
  }

  void 'test check boolean exhaustiveness'() {
    highlightingTest """
def foo(boolean b) {
  def x = switch (b) {
    case true -> 1
    case false -> 1
  }
}
""", GrSwitchExhaustivenessCheckInspection

    highlightingTest """
def foo(boolean b) {
  def x = <weak_warning>switch</weak_warning> (b) {
    case false -> 1
  }
}
""", GrSwitchExhaustivenessCheckInspection

    highlightingTest """
def foo(boolean b) {
  def x = <weak_warning>switch</weak_warning> (b) {
    case true -> 1
  }
}
""", GrSwitchExhaustivenessCheckInspection

    highlightingTest """
def foo(boolean b) {
  def x = switch (b) {
    default -> 1
  }
}
""", GrSwitchExhaustivenessCheckInspection
  }

  void 'test numerical range exhaustiveness'() {
    highlightingTest """
def foo(byte b) {
  def x = switch (b) {
    case -128..<0 -> 1
    case 0..127 -> 1
  }
}
""", GrSwitchExhaustivenessCheckInspection

    highlightingTest """
def foo(byte b) {
  def x = <weak_warning>switch</weak_warning> (b) {
    case -128..<0 -> 1
    case 0..126 -> 1
  }
}
""", GrSwitchExhaustivenessCheckInspection
  }

  void 'test incomplete enum'() {
    highlightingTest """
enum A { X, Y }

def foo(A b) {
  def x = <weak_warning>switch</weak_warning> (b) {
    case A.X -> 1
  }
}
""", GrSwitchExhaustivenessCheckInspection
  }

  void 'test complete enum'() {
    highlightingTest """
enum A { X, Y }

def foo(A b) {
  def x = switch (b) {
    case A.X -> 1
    case A.Y -> 2
  }
}
""", GrSwitchExhaustivenessCheckInspection
  }

  void 'test incomplete sealed class'() {
    highlightingTest """
sealed class A {}
class B extends A {}
class C extends A {}

def foo(A b) {
  def x = <weak_warning>switch</weak_warning> (b) {
    case B -> 1
  }
}
""", GrSwitchExhaustivenessCheckInspection
  }

  void 'test complete sealed class'() {
    highlightingTest """
abstract sealed class A {}
class B extends A {}
class C extends A {}

def foo(A b) {
  def x = switch (b) {
    case B -> 1
    case C -> 2
  }
}
""", GrSwitchExhaustivenessCheckInspection
  }

  void 'test complete matching on types'() {
    highlightingTest """
def foo(IOException b) {
  def x = switch (b) {
    case Throwable -> 1
  }
}
""", GrSwitchExhaustivenessCheckInspection
  }

  void 'test untyped condition'() {
    highlightingTest"""
def foo(b) {
  def x = <weak_warning>switch</weak_warning> (b) {
    case 10 -> 1
  }
}
""", GrSwitchExhaustivenessCheckInspection
  }

  void 'test plain switch statement is not highligted'() {
    highlightingTest """
void main(String[] args) {
       switch (10) {
           case 20:
               return
       }
    }"""
  }

  void 'test unmatched null'() {
    def inspection = new GrSwitchExhaustivenessCheckInspection()
    inspection.enableNullCheck()
    highlightingTest"""
enum A { B }
def foo(A b) {
  def x = <weak_warning>switch</weak_warning> (b) {
    case A.B -> 1
  }
}
""", inspection
  }

  void 'test explicitly matched null'() {
    def inspection = new GrSwitchExhaustivenessCheckInspection()
    inspection.enableNullCheck()
    highlightingTest"""
enum A { B }
def foo(A b) {
  def x = switch (b) {
    case A.B -> 1
    case null -> 2
  }
}
""", inspection
  }

  void 'test highlight with only one subclass matched'() {
    highlightingTest """
class A {

}

class B extends A {}


def foo(A a) {
    def x = <weak_warning>switch</weak_warning> (a) {
        case B -> 30
    }
}""", GrSwitchExhaustivenessCheckInspection
  }

  void 'test nested sealed'() {
    highlightingTest"""
abstract sealed class A {}
class B extends A {}
abstract sealed class C extends A {}
class D extends C {}
class E extends C {}

def foo(A a) {
  def x = switch (a) {
    case B -> 30
    case D -> 20
    case E -> 40
  }
}
""", GrSwitchExhaustivenessCheckInspection
  }
}
