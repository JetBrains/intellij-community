/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.ChangeToOperatorInspection

import java.util.regex.Pattern

import static org.jetbrains.plugins.groovy.GroovyFileType.GROOVY_FILE_TYPE
import static org.jetbrains.plugins.groovy.util.TestUtils.CARET_MARKER

class GrChangeToOperatorTest extends LightGroovyTestCase {

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

  def inspection = new ChangeToOperatorInspection()

  void testSimpleUnaryExpression() {
    assertValid(/a.bitwiseNegate()/, /~a/)
    assertValid(/a.negative()/, /-a/)
    assertValid(/a.positive()/, /+a/)
//    assertValid(/a.call()/, /a()/)
    assertValid(/a.next()/, /++a/)
    assertValid(/a.previous()/, /--a/)
  }

  void testNegatableUnaryExpression() {
    assertValid(/a.asBoolean()/, /!!a/)
    assertValid(/!a.asBoolean()/, /!a/)

    assertValid(/if (${_}a.asBoolean()${_});/, /a/)
    assertValid(/if (${_}!a.asBoolean()${_});/, /!a/)
  }

  void testComplexNegatableUnaryExpression() {
    assertValid(/if (${_}'a'.intern().asBoolean()${_});/, /'a'.intern()/)
  }

  void testNegatedOption() {
    inspection.useDoubleNegation = false

    assertValid(/a.asBoolean()/)
    assertValid(/!a.asBoolean()/, /!a/)

    assertValid(/if (${_}a.asBoolean()${_});/, /a/)
    assertValid(/if (${_}!a.asBoolean()${_});/, /!a/)
  }

  void testComplexNegatedOption() {
    inspection.useDoubleNegation = false

    assertValid(/if (${_}'a'.intern().asBoolean()${_});/, /'a'.intern()/)
  }

  void testSimpleBinaryExpression() {
    assertValid(/a.minus(b)/, /a - b/)
    assertValid(/a.plus(b)/, /a + b/)
    assertValid(/a.power(b)/, /a**b/)
    assertValid(/a.div(b)/, $/a / b/$)
    assertValid(/a.mod(b)/, /a % b/)
    assertValid(/a.or(b)/, /a | b/)
    assertValid(/a.and(b)/, /a & b/)
    assertValid(/a.xor(b)/, /a ^ b/)
    assertValid(/a.leftShift(b)/, /a << b/)
    assertValid(/a.rightShift(b)/, /a >> b/)
    assertValid(/a.rightShiftUnsigned(b)/, /a >>> b/)

    assertValid(/a.asType(String)/, /a as String/)
    assertValid(/a.multiply(b)/, /a * b/)

    assertValid(/(a.toString() as Operators).minus(b.hashCode())/, /(a.toString() as Operators) - b.hashCode()/)

    assertValid(/!${_}a.asType(String)${_}/, /(a as String)/)

    assertValid(/${_}a.xor((a.b+1) == b)${_} == a/, /(a ^ ((a.b + 1) == b))/)
  }

  void testComplexBinaryExpression() {
    assertValid(/b.isCase(a)/, /a in b/)
    assertValid(/if (${_}[1, 2, 3].isCase(2-1)${_});/, /(2 - 1) in [1, 2, 3]/)
    assertValid(/def x = ${_}"1".plus(1)${_}/, /"1" + 1/)
    assertValid(/("1" + 1).plus(1)/, /("1" + 1) + 1/)
    assertValid(/!a.toString().asBoolean()/, /!a.toString()/)
  }

  void testNegatableBinaryExpression() {
    assertValid(/a.equals(b)/, /a == b/)
    assertValid(/!a.equals(b)/, /a != b/)
  }

  void testComplexNegatableBinaryExpression() {
    assertValid(/!(1.toString().replace('1', '2')+"").equals(2.toString())/, /(1.toString().replace('1', '2') + "") != 2.toString()/)
  }

  void testCompareTo() {
    assertValid(/a.compareTo(b)/, /a <=> b/)
    assertValid(/a.compareTo(b) < 0/, /a < b/)
    assertValid(/a.compareTo(b) <= 0/, /a <= b/)
    assertValid(/a.compareTo(b) == 0/, /a == b/)
    assertValid(/a.compareTo(b) != 0/, /a != b/)
    assertValid(/a.compareTo(b) >= 0/, /a >= b/)
    assertValid(/a.compareTo(b) > 0/, /a > b/)

    assertValid(/if (${_}(2-1).compareTo(3) > 0${_});/, /(2 - 1) > 3/)
  }

  void testCompareToOption() {
    inspection.shouldChangeCompareToEqualityToEquals = false
    assertValid(/a.compareTo(b) == 0/)
    assertValid(/a.compareTo(b) != 0/)
  }

  void testGetAndPut() {
    assertValid(/a.getAt(b)/, /a[b]/)
    assertValid(/a.putAt(b, 'c')/, /a[b] = 'c'/)

    assertValid(/a.putAt(b, 'c'*2)/, /a[b] = ('c' * 2)/)
  }

  final _ = '/*placeholder*/'
  @Language('Groovy') final DECLARATIONS = '''
    @SuppressWarnings("GrMethodMayBeStatic")
    class Operators {
      def bitwiseNegate() { null }
      def negative() { null }
      def positive() { null }
      def call() { null }
      def next() { null }
      def previous() { null }
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
      def putAt(b, c) { null }

      boolean asBoolean() { true }
      boolean isCase(b) { true }
      boolean equals(b) { true }
      int compareTo(b) { 0 }
    }

    def (Operators a, Operators b) = [null, null]
  '''

  private void assertValid(@Language('Groovy') String text, @Language('Groovy') String methodReplacement) {
    def (prefix, method, suffix) = getMessage(text)
    configure("${prefix}${CARET_MARKER}${method}${suffix}")
    myFixture.launchAction(myFixture.findSingleIntention("Change '"))
    myFixture.checkResult("${DECLARATIONS}  ${prefix}${methodReplacement}${suffix}")
  }

  private void assertValid(@Language('Groovy') String text) {
    configure(text)
    assertEmpty myFixture.getAvailableIntentions()
  }

  private configure(@Language('Groovy') String text) {
    myFixture.enableInspections(inspection)
    myFixture.configureByText(GROOVY_FILE_TYPE, "${DECLARATIONS}  ${text}")
  }

  private getMessage(String text) {
    def _ = Pattern.quote(_)
    def (__, prefix, method, suffix) = (text =~ /^(?:(.*?)${_})?(.+?)(?:${_}(.*?))?$/)[0]
    [prefix ?: '', method, suffix ?: '']
  }
}