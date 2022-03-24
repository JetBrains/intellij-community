// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrSwitchExhaustivenessCheckInspection

class AddMissingCaseSectionsTest extends LightJavaCodeInsightFixtureTestCase {

  void doTest(String before, String after) {
    myFixture.enableInspections(GrSwitchExhaustivenessCheckInspection)
    myFixture.configureByText("_.groovy", before)
    myFixture.launchAction(myFixture.findSingleIntention("Insert"))
    myFixture.checkResult(after)
  }


  void 'test boolean'() {
    doTest """
def foo(boolean b) {
  def x = swit<caret>ch (b) {
      case true -> 1
  }
}
""", """
def foo(boolean b) {
  def x = swit<caret>ch (b) {
      case true -> 1
      case false -> throw new IllegalStateException()
  }
}
"""
  }

  void 'test enum'() {
    doTest """
enum A { X, Y }

def foo(A b) {
  def x = swit<caret>ch (b) {
      case A.X -> 1
  }
}
""", """
enum A { X, Y }

def foo(A b) {
  def x = swit<caret>ch (b) {
      case A.X -> 1
      case A.Y -> throw new IllegalStateException()
  }
}
"""
  }

  void 'test sealed'() {
    doTest """
abstract sealed class A {}
class B extends A {}
class C extends A {}
class D extends A {}

def foo(A b) {
  def x = swit<caret>ch (b) {
      case B -> 1
  }
}
""", """
abstract sealed class A {}
class B extends A {}
class C extends A {}
class D extends A {}

def foo(A b) {
  def x = swit<caret>ch (b) {
      case B -> 1
      case C -> throw new IllegalStateException()
      case D -> throw new IllegalStateException()
  }
}
"""
  }
}
