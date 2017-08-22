/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.inspections

import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.ChangeToOperatorInspection
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression

@CompileStatic
class GrChangeToOperatorTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  final ChangeToOperatorInspection inspection = new ChangeToOperatorInspection()

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

  void testSimpleUnaryExpression() {
    doTest "a.bitwiseNegate()", "~a"
    doTest "a.negative()", "-a"
    doTest "a.positive()", "+a"
    doTest "a.next()", "++a"
    doTest "a.previous()", "--a"
  }

  void testAsBoolean() {
    doTest "a.asBoolean()", "!!a"
    doTest "!a.asBoolean()", "!a"
    doTest "a.as<caret>Boolean().toString()", "(!!a).toString()"
    doTest "if (a.as<caret>Boolean());", "if (a);"
    doTest "while (a.as<caret>Boolean()) {}", "while (a) {}"
    doTest "a.as<caret>Boolean() ? 1 : 0", "a ? 1 : 0"
    doTest "a ? a.as<caret>Boolean() : 0", "a ? !!a : 0"
    doTest "if (!a.asB<caret>oolean());", "if (!a);"
    doTest "if ('a'.intern().asBool<caret>ean());", "if ('a'.intern());"
  }

  void 'test unary expression with wrong number arguments'() {
    doTest "a.bitwiseNegate(1)"
    doTest "a.negative(1)"
    doTest "a.positive(1)"
    doTest "a.next(1)"
    doTest "a.previous(1)"
    doTest "a.asBoolean(1)"
  }

  void testNegatedOption() {
    inspection.useDoubleNegation = false
    doTest "a.asBoolean()"
    doTest "!a.asBoolean()", "!a"
    doTest "if (a.as<caret>Boolean());", "if (a);"
    doTest "if (!a.asB<caret>oolean());", "if (!a);"
    doTest "if ('a'.intern().asBool<caret>ean());", "if ('a'.intern());"
    doTest "a ? a.as<caret>Boolean() : 0"
  }

  void testSimpleBinaryExpression() {
    [
      "a.plus(b)"              : "a + b",
      "a.minus(b)"             : "a - b",
      "a.multiply(b)"          : "a * b",
      "a.div(b)"               : "a / b",
      "a.power(b)"             : "a**b",
      "a.mod(b)"               : "a % b",
      "a.or(b)"                : "a | b",
      "a.and(b)"               : "a & b",
      "a.xor(b)"               : "a ^ b",
      "a.leftShift(b)"         : "a << b",
      "a.rightShift(b)"        : "a >> b",
      "a.rightShiftUnsigned(b)": "a >>> b",
      "a.plus({ b })"            : "a + { b }",
    ].each {
      doTest it.key, it.value
    }
  }

  void 'test binary expression with wrong number of arguments'() {
    doTest "a.plus()"
    doTest "a.minus()"
    doTest "a.multiply()"
    doTest "a.div()"
    doTest "a.power()"
    doTest "a.mod()"
    doTest "a.or()"
    doTest "a.and()"
    doTest "a.xor()"
    doTest "a.leftShift()"
    doTest "a.rightShift()"
    doTest "a.rightShiftUnsigned()"
    doTest "a.asType()"

    doTest "a.plus(b, 1)"
    doTest "a.minus(b, 1)"
    doTest "a.multiply(b, 1)"
    doTest "a.div(b, 1)"
    doTest "a.power(b, 1)"
    doTest "a.mod(b, 1)"
    doTest "a.or(b, 1)"
    doTest "a.and(b, 1)"
    doTest "a.xor(b, 1)"
    doTest "a.leftShift(b, 1)"
    doTest "a.rightShift(b, 1)"
    doTest "a.rightShift('a': 1, 'b':2)"
    doTest "a.rightShiftUnsigned(b, 1)"
    doTest "a.asType(b, 1)"
    doTest "a.n<caret>ext({return 1})"
    doTest "a.n<caret>ext {return 1}"
    doTest "a.pl<caret>us(1) {return 1}"
  }

  void testComplexBinaryExpression() {
    [
      "(a.toString() as Operators).minus(b.hashCode())": "(a.toString() as Operators) - b.hashCode()",
      "b.isCase(a)"                                    : "a in b",
      "if ([1, 2, 3].is<caret>Case(2-1));"             : "if (2 - 1 in [1, 2, 3]);",
      "![1, 2, 3].is<caret>Case(2-1)"                  : "!(2 - 1 in [1, 2, 3])",
      'def x = "1".p<caret>lus(1)'                     : 'def x = "1" + 1',
      '("1" + 1).plus(1)'                              : '("1" + 1) + 1',
      '!a.toString().asBoolean()'                      : '!a.toString()',
      "a.xo<caret>r((a.b + 1) * b) == a"               : "(a ^ (a.b + 1) * b) == a",
      "a.as<caret>Type(String).bytes"                  : "(a as String).bytes",
    ].each {
      doTest it.key, it.value
    }
  }

  void testNegatableBinaryExpression() {
    doTest "a.equals(b)", "a == b"
    doTest "!a.equals(b)", "a != b"
  }

  void testSamePrioritiesExpression() {
    doTest "a.eq<caret>uals(b) == 1", "(a == b) == 1"
    doTest "(a == b).eq<caret>uals(1)", "(a == b) == 1"
    doTest "1 == a.eq<caret>uals(b)", "1 == (a == b)"
    doTest "!a.eq<caret>uals(b) == 1", "(a != b) == 1"
    doTest "1 == !a.eq<caret>uals(b)", "1 == (a != b)"

    doTest "1 + a.p<caret>lus(b)", "1 + a + b"
    doTest "1 + a.m<caret>inus(b)", "1 + a - b"
    doTest "1 - a.m<caret>inus(b)", "1 - (a - b)"
    doTest "a.m<caret>inus(1 - b)", "a - (1 - b)"
    doTest "1 - a.p<caret>lus(b)", "1 - (a + b)"

    doTest "a.m<caret>inus(b) - 1", "a - b - 1"
    doTest "a.p<caret>lus(b) - 1", "a + b - 1"
    doTest "a.m<caret>inus(b) + 1", "a - b + 1"
    doTest "a.p<caret>lus(b) + 1", "a + b + 1"
  }

  void testAsType() {
    doTest "a.asType(String)", "a as String"
    doTest "!a.asType(String)", "!(a as String)"
    doTest "a.asType(String.class)", "a as String"
    doTest "a.asType(a.getClass())"
    doTest "a.asType(UnknownClass)"
  }

  void testComplex() {
    doTest "a.eq<caret>uals(b * c) == 1", "(a == b * c) == 1"

    doTest "a.eq<caret>uals(b * c)", "a == b * c"
    doTest "(Boolean) a.eq<caret>uals(b)", "(Boolean) (a == b)"
  }


  void testComplexNegatableBinaryExpression() {
    doTest(/!(1.toString().replace('1', '2')+"").equals(2.toString())/, /(1.toString().replace('1', '2') + "") != 2.toString()/)
  }

  void testCompareTo() {
    doTest "a.compareTo(b)", "a <=> b"
    doTest "a.compareTo(b) < 0", "a < b"

    doTest "a.compareTo(b) <= 0", "a <= b"
    doTest "a.compareTo(b) == 0", "a == b"
    doTest "a.compareTo(b) != 0", "a != b"
    doTest "a.compareTo(b) >= 0", "a >= b"
    doTest "a.compareTo(b) > 0", "a > b"
    doTest "if ((2-1).compa<caret>reTo(3) > 0);", /if ((2 - 1) > 3);/
    doTest "! (a.compar<caret>eTo(b) < 0)", "!(a < b)"
    doTest "(2 - 1).compa<caret>reTo(2 | 1) > 0", "(2 - 1) > (2 | 1)"
  }

  void testCompareToOption() {
    inspection.shouldChangeCompareToEqualityToEquals = false
    doTest "a.compareTo(b) == 0"
    doTest "a.compareTo(b) != 0"
  }

  void testGetAndPut() {
    doTest "a.getAt(b)", "a[b]"
    doTest "a.g<caret>etAt(b).toString()", "a[b].toString()"
    doTest "a.putAt(b, 'c')", "a[b] = 'c'"
    doTest "a.putAt(b, 'c'*2)", "a[b] = 'c' * 2"
    doTest "a.getAt(a, b)"
    doTest "a.putAt(b)"
    doTest "a.putAt(b, b, b)"
    doTest "a.put<caret>At(b,b) {b}"
    doTest "(List) a.g<caret>etAt(b)", "(List) a[b]"
    doTest "(List) a.g<caret>etAt(b + 1)", "(List) a[b + 1]"
    doTest "a.put<caret>At(b) { 1 }", "a[b] = { 1 }"

    doTest(
''' a.put<caret>At(b) { 
    return 1 
};''',
'''a[b] = {
    return 1
};''')
  }

  void testWithoutAdditionalParenthesesOption() {
    inspection.withoutAdditionalParentheses = true
    doTest "a.eq<caret>uals(b) == 1"
    doTest "1 == !a.eq<caret>uals(b)"
    doTest "a.eq<caret>uals(b) && c", "a == b && c"

    doTest "1 - a.m<caret>inus(b)"
    doTest "a.m<caret>inus(1 - b)"
    doTest "1 - a.p<caret>lus(b)"
    doTest '("1" + 1).plus(1)', '("1" + 1) + 1'

    doTest "a.asType(String)", "a as String"
    doTest "!a.asType(String)"
    doTest "a.as<caret>Type(String).toString()"

    doTest "a.g<caret>etAt(b).field", "a[b].field"
    doTest "a.p<caret>utAt(b, 1).field"

    doTest "a.ne<caret>xt().bytes"
    doTest "a.ne<caret>xt() + 1", "++a + 1"

    doTest "[1, 2, 3].is<caret>Case(2-1)", "2 - 1 in [1, 2, 3]"
    doTest "![1, 2, 3].is<caret>Case(2-1)"

    doTest "! (a.compar<caret>eTo(b) < 0)", "!(a < b)"
    doTest "if ((2 - 1).compa<caret>reTo(2-1) > 0);", "if ((2 - 1) > 2 - 1);"
    doTest "(2 - 1).compa<caret>reTo(2) - 1"
    doTest "(2 - 1).compa<caret>reTo(2 | 1)"

    doTest "a.as<caret>Boolean() != b.asBoolean()", "!!a != b.asBoolean()"
    doTest "a.asBoolean().toString()"
  }

  void 'test super calls'() {
    fixture.configureByText '_.groovy', '''\
class Inheritor extends Operators {
  def testStuff(o) {
    super.bitwiseNegate()   
    super.negative()   
    super.positive()   
    super.next()   
    super.previous()   
    super.plus(o)   
    super.minus(o)   
    super.multiply(o)   
    super.power(o)   
    super.div(o)   
    super.mod(o)   
    super.or(o)   
    super.and(o)   
    super.xor(o)   
    super.leftShift(o)   
    super.rightShift(o)   
    super.rightShiftUnsigned(o)   
    super.asType(String)   
    super.getAt(o)   
    super.putAt(o, o)   
    super.asBoolean()   
    super.isCase(o)   
    super.equals(o)   
    super.compareTo(o)   
  }
}
'''
    fixture.checkHighlighting()
  }

  final String DECLARATIONS = 'def (Operators a, Operators b) = [null, null]\n'

  private void doTest(String before, String after = null) {
    Closeable closeCaret =  {fixture.editor.caretModel.moveToOffset(0)}

    closeCaret.withCloseable {
      fixture.with {
        configureByText '_.groovy', "$DECLARATIONS$before"
        moveCaret()
        def intentions = filterAvailableIntentions('Replace ')
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
  }

  private void moveCaret() {
    def statement = (fixture.file as GroovyFile).statements.last()
    GrMethodCall call = null

    if (statement instanceof GrMethodCall) {
      call = statement as GrMethodCall
    }
    else if (statement instanceof GrUnaryExpression) {
      def operand = (statement as GrUnaryExpression).operand
      if (operand instanceof GrMethodCall) {
        call = operand as GrMethodCall
      }
    }
    else if (statement instanceof GrBinaryExpression) {
      def left = (statement as GrBinaryExpression).leftOperand
      if (left instanceof GrMethodCall) {
        call = left as GrMethodCall
      }
    }

    if (call && fixture.editor.caretModel.logicalPosition.column == 0) {
      def invoked = call.invokedExpression as GrReferenceExpression
      fixture.editor.caretModel.moveToOffset(invoked.referenceNameElement.textRange.startOffset)
    }
  }
}