// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.inspections

import com.intellij.psi.PsiElement
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.changeToMethod.ChangeToMethodInspection
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression

@CompileStatic
class GrChangeToMethodTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

  final ChangeToMethodInspection inspection = new ChangeToMethodInspection()

  @Override
  void setUp() throws Exception {
    super.setUp()
    fixture.with {
      addFileToProject 'Operators.groovy', '''\
class Operators {
  def bitwiseNegate(a = null) { null }
  def negative(a = null) { null }
  def positive(a = null) { null }
  def call() { null }
  def next(a = null) { null }
  def previous(a = null) { null }
  def plus(b) { null }
  def minus(b) { null }
  def multiply(b) { null }
  def power(b) { null }
  def div(b) { null }
  def mod(b) { null }
  def or(b) { null }
  def and(b) { null }
  def xor(b) { null }
  def leftShift(b) { null }
  def rightShift(b) { null }
  def rightShiftUnsigned(b) { null }
  def asType(b) { null }
  def getAt(b) { null }
  def putAt(b, c = null, d = null) { null }
  boolean asBoolean(a = null) { true }
  boolean isCase(b) { true }
  boolean equals(b) { true }
  int compareTo(b) { 0 }
}
'''
      enableInspections inspection
    }
  }

  final String DECLARATIONS = 'def (Operators a, Operators b) = [null, null]\n'


  void testSimpleUnaryExpression() {
    doTest "~a", "a.bitwiseNegate()"
    doTest "-a", "a.negative()"
    doTest "+a", "a.positive()"
    doTest "++a", "a.next()"
    doTest "--a", "a.previous()"
  }

  void testSimpleBinaryExpression() {
    [
      "a + b"           :"a.plus(b)",
      "a + { b }"       :"a.plus({ b })",
      "a - b"           :"a.minus(b)",
      "a * b"           :"a.multiply(b)",
      "a / b"           :"a.div(b)",
      "a**b"            :"a.power(b)",
      "a % b"           :"a.mod(b)",
      "a | b"           :"a.or(b)",
      "a & b"           :"a.and(b)",
      "a ^ b"           :"a.xor(b)",
      "a << b"          :"a.leftShift(b)",
      "a >> b"          :"a.rightShift(b)",
      "a >>> b"         :"a.rightShiftUnsigned(b)",
      "a in b"          :"b.isCase(a)"
    ].each {
      doTest it.key, it.value
    }
  }

  void testAsType() {
    doTest "a as String", "a.asType(String)"
    doTest "!(a a<caret>s String)", "!a.asType(String)"
    doTest "a a<caret>s List<Integer>"
    doTest "a a<caret>s List", "a.asType(List)"
  }

  void testCompareTo() {
    doTest "a <=> b", "a.compareTo(b)"
    doTest "a < b", "a.compareTo(b) < 0"
    doTest "a <= b", "a.compareTo(b) <= 0"
    doTest "a >= b", "a.compareTo(b) >= 0"
    doTest "a > b", "a.compareTo(b) > 0"
    doTest "if ((2 - 1) ><caret> 3);", "if ((2 - 1).compareTo(3) > 0);"
    doTest "!(a <<caret> b)", "!(a.compareTo(b) < 0)"
  }

  void testEqualsExpression() {
    doTest "a == b", "a.equals(b)"
    doTest "a != b", "!a.equals(b)"
  }

  void testComplexBinaryExpression() {
    doTest "new Object() as List" , "new Object().asType(List)"
    doTest "(a =<caret>= b * c) == 1",  "a.equals(b * c) == 1"
    doTest "(b * c =<caret>= a) == 1",  "(b * c).equals(a) == 1"
    doTest "a.plus(b) +<caret> c",  "a.plus(b).plus(c)"

    doTest "if (2 - 1 in<caret> [1, 2, 3]);", "if ([1, 2, 3].isCase(2 - 1));"
    doTest "(a ^<caret> (a.b + 1) * b).equals(a)", "a.xor((a.b + 1) * b).equals(a)"

    doTest "a == b * c", "a.equals(b * c)"
    doTest "(Boolean) (a =<caret>= b)", "(Boolean) a.eq<caret>uals(b)"
  }



  void testSamePrioritiesExpression() {
    doTest "1 << 1 <<<caret> 1", "(1 << 1).leftShift(1)"
    doTest "1 <<<caret> 1 << 1", "1.leftShift(1) << 1"
    doTest "1 - (a -<caret> b)", "1 - a.minus(b)"
    doTest "a - b -<caret> 1", "(a - b).minus(1)"
  }

  private void doTest(String before, String after = null) {
    fixture.with {
      configureByText '_.groovy', "$DECLARATIONS$before"
      moveCaret()
      def intentions = filterAvailableIntentions('Replace with')
      if (after) {
        assert intentions: before
        launchAction intentions.first()
        checkResult "$DECLARATIONS$after"
      }
      else {
        assert !intentions
      }
    }
  }

  private void moveCaret() {
    def statement = (fixture.file as GroovyFile).statements.last()
    PsiElement element = null

    if (statement instanceof GrUnaryExpression) {
      element = (statement as GrUnaryExpression).operationToken
    } else if (statement instanceof GrBinaryExpression) {
      element = (statement as GrBinaryExpression).operationToken
    } else if (statement instanceof GrSafeCastExpression) {
      element = (statement as GrSafeCastExpression).operationToken
    }

    if (element && fixture.editor.caretModel.logicalPosition.column == 0) {
      fixture.editor.caretModel.moveToOffset(element.textRange.startOffset)
    }
  }
}