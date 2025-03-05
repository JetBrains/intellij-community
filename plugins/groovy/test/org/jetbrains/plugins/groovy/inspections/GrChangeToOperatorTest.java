// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.inspections;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.ChangeToOperatorInspection;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;

import java.io.Closeable;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;

public class GrChangeToOperatorTest extends LightGroovyTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    getFixture().addFileToProject("Operators.groovy", """
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
      """);
    getFixture().enableInspections(getInspection());
  }

  public void testSimpleUnaryExpression() {
    doTest("a.bitwiseNegate()", "~a");
    doTest("a.negative()", "-a");
    doTest("a.positive()", "+a");
  }

  public void testIncDecUnary() {
    doTest("a.next()");
    doTest("a.previous()");
    doTest("a = a.next(1)");
    doTest("a = a.previous(1)");
    doTest("b = a.next()");
    doTest("b = a.previous()");
    doTest("a = a.<caret>next()", "++a");
    doTest("a = a.<caret>previous()", "--a");
    doTest("b = a = a.<caret>next()", "b = ++a");
    doTest("b = a = a.<caret>previous()", "b = --a");
    doTest("while(a = a.<caret>next()) {}", "while(++a) {}");
    doTest("while(a = a.<caret>previous()) {}", "while(--a) {}");
  }

  public void testAsBoolean() {
    doTest("a.asBoolean()", "!!a");
    doTest("!a.asBoolean()", "!a");
    doTest("a.as<caret>Boolean().toString()", "(!!a).toString()");
    doTest("if (a.as<caret>Boolean());", "if (a);");
    doTest("while (a.as<caret>Boolean()) {}", "while (a) {}");
    doTest("a.as<caret>Boolean() ? 1 : 0", "a ? 1 : 0");
    doTest("a ? a.as<caret>Boolean() : 0", "a ? !!a : 0");
    doTest("if (!a.asB<caret>oolean());", "if (!a);");
    doTest("if ('a'.intern().asBool<caret>ean());", "if ('a'.intern());");
  }

  public void test_unary_expression_with_wrong_number_arguments() {
    doTest("a.bitwiseNegate(1)");
    doTest("a.negative(1)");
    doTest("a.positive(1)");
    doTest("a.asBoolean(1)");
  }

  public void testNegatedOption() {
    inspection.useDoubleNegation = false;
    doTest("a.asBoolean()");
    doTest("!a.asBoolean()", "!a");
    doTest("if (a.as<caret>Boolean());", "if (a);");
    doTest("if (!a.asB<caret>oolean());", "if (!a);");
    doTest("if ('a'.intern().asBool<caret>ean());", "if ('a'.intern());");
    doTest("a ? a.as<caret>Boolean() : 0");
  }

  public void testSimpleBinaryExpression() {
    LinkedHashMap<String, String> map = new LinkedHashMap<>(13);
    map.put("a.plus(b)", "a + b");
    map.put("a.minus(b)", "a - b");
    map.put("a.multiply(b)", "a * b");
    map.put("a.div(b)", "a / b");
    map.put("a.power(b)", "a**b");
    map.put("a.mod(b)", "a % b");
    map.put("a.or(b)", "a | b");
    map.put("a.and(b)", "a & b");
    map.put("a.xor(b)", "a ^ b");
    map.put("a.leftShift(b)", "a << b");
    map.put("a.rightShift(b)", "a >> b");
    map.put("a.rightShiftUnsigned(b)", "a >>> b");
    map.put("a.plus({ b })", "a + { b }");
    map.forEach(this::doTest);
  }

  public void test_binary_expression_with_wrong_number_of_arguments() {
    doTest("a.plus()");
    doTest("a.minus()");
    doTest("a.multiply()");
    doTest("a.div()");
    doTest("a.power()");
    doTest("a.mod()");
    doTest("a.or()");
    doTest("a.and()");
    doTest("a.xor()");
    doTest("a.leftShift()");
    doTest("a.rightShift()");
    doTest("a.rightShiftUnsigned()");
    doTest("a.asType()");

    doTest("a.plus(b, 1)");
    doTest("a.minus(b, 1)");
    doTest("a.multiply(b, 1)");
    doTest("a.div(b, 1)");
    doTest("a.power(b, 1)");
    doTest("a.mod(b, 1)");
    doTest("a.or(b, 1)");
    doTest("a.and(b, 1)");
    doTest("a.xor(b, 1)");
    doTest("a.leftShift(b, 1)");
    doTest("a.rightShift(b, 1)");
    doTest("a.rightShift('a': 1, 'b':2)");
    doTest("a.rightShiftUnsigned(b, 1)");
    doTest("a.asType(b, 1)");
    doTest("a.n<caret>ext({return 1})");
    doTest("a.n<caret>ext {return 1}");
    doTest("a.pl<caret>us(1) {return 1}");
  }

  public void testComplexBinaryExpression() {
    LinkedHashMap<String, String> map = new LinkedHashMap<>(9);
    map.put("(a.toString() as Operators).minus(b.hashCode())", "(a.toString() as Operators) - b.hashCode()");
    map.put("b.isCase(a)", "a in b");
    map.put("if ([1, 2, 3].is<caret>Case(2-1));", "if (2 - 1 in [1, 2, 3]);");
    map.put("![1, 2, 3].is<caret>Case(2-1)", "!(2 - 1 in [1, 2, 3])");
    map.put("def x = \"1\".p<caret>lus(1)", "def x = \"1\" + 1");
    map.put("(\"1\" + 1).plus(1)", "(\"1\" + 1) + 1");
    map.put("!a.toString().asBoolean()", "!a.toString()");
    map.put("a.xo<caret>r((a.b + 1) * b) == a", "(a ^ (a.b + 1) * b) == a");
    map.put("a.as<caret>Type(String).bytes", "(a as String).bytes");
    map.forEach(this::doTest);
  }

  public void testNegatableBinaryExpression() {
    doTest("a.equals(b)", "a == b");
    doTest("!a.equals(b)", "a != b");
  }

  public void testSamePrioritiesExpression() {
    doTest("a.eq<caret>uals(b) == 1", "a == b == 1");
    doTest("(a == b).eq<caret>uals(1)", "(a == b) == 1");
    doTest("1 == a.eq<caret>uals(b)", "1 == (a == b)");
    doTest("!a.eq<caret>uals(b) == 1", "a != b == 1");
    doTest("1 == !a.eq<caret>uals(b)", "1 == (a != b)");

    doTest("1 + a.p<caret>lus(b)", "1 + (a + b)");
    doTest("1 + a.m<caret>inus(b)", "1 + (a - b)");
    doTest("1 - a.m<caret>inus(b)", "1 - (a - b)");
    doTest("a.m<caret>inus(1 - b)", "a - (1 - b)");
    doTest("1 - a.p<caret>lus(b)", "1 - (a + b)");

    doTest("a.m<caret>inus(b) - 1", "a - b - 1");
    doTest("a.p<caret>lus(b) - 1", "a + b - 1");
    doTest("a.m<caret>inus(b) + 1", "a - b + 1");
    doTest("a.p<caret>lus(b) + 1", "a + b + 1");
  }

  public void testAsType() {
    doTest("a.asType(String)", "a as String");
    doTest("!a.asType(String)", "!(a as String)");
    doTest("a.asType(String.class)", "a as String");
    doTest("a.asType(a.getClass())");
    doTest("a.asType(UnknownClass)");
  }

  public void test_asType_with_context() {
    getFixture().addClass("package com.foo; class Node {}");
    doTest("import com.foo.Node\n\na.asType(Node)", "import com.foo.Node\n\na as Node");
  }

  public void testComplex() {
    doTest("a.eq<caret>uals(b * c) == 1", "a == b * c == 1");

    doTest("a.eq<caret>uals(b * c)", "a == b * c");
    doTest("(Boolean) a.eq<caret>uals(b)", "(Boolean) (a == b)");
  }

  public void testComplexNegatableBinaryExpression() {
    doTest("!(1.toString().replace('1', '2')+\"\").equals(2.toString())", "(1.toString().replace('1', '2') + \"\") != 2.toString()");
  }

  public void test_compareTo() {
    doTest("a.compareTo(b)", "a <=> b");
    doTest("a.compareTo(b) < 1", "(a <=> b) < 1");
    doTest("a.compareTo(b) <= 1", "(a <=> b) <= 1");
    doTest("a.compareTo(b) == 1", "a <=> b == 1");
    doTest("a.compareTo(b) != 1", "a <=> b != 1");
    doTest("a.compareTo(b) >= 1", "(a <=> b) >= 1");
    doTest("a.compareTo(b) > 1", "(a <=> b) > 1");
  }

  public void test_compareTo_0() {
    doTest("a.compareTo(b) < 0", "a < b");
    doTest("a.compareTo(b) <= 0l", "a <= b");
    doTest("a.compareTo(b) == 0g", "a == b");
    doTest("a.compareTo(b) != 0f", "a != b");
    doTest("a.compareTo(b) >= 0d", "a >= b");
    doTest("a.compareTo(b) > 0.0g", "a > b");

    doTest("if ((2-1).<caret>compareTo(3) > 0);", "if ((2 - 1) > 3);");
    doTest("! (a.<caret>compareTo(b) < 0)", "!(a < b)");
    doTest("(2 - 1).<caret>compareTo(2 | 1) > 0", "(2 - 1) > (2 | 1)");
  }

  public void test_compareTo_0_off() {
    inspection.shouldChangeCompareToEqualityToEquals = false;
    doTest("a.compareTo(b) == 0");
    doTest("a.compareTo(b) != 0");
  }

  public void testGetAndPut() {
    doTest("a.getAt(b)", "a[b]");
    doTest("a.g<caret>etAt(b).toString()", "a[b].toString()");
    doTest("a.putAt(b, 'c')", "a[b] = 'c'");
    doTest("a.putAt(b, 'c'*2)", "a[b] = 'c' * 2");
    doTest("a.getAt(a, b)");
    doTest("a.putAt(b)");
    doTest("a.putAt(b, b, b)");
    doTest("a.put<caret>At(b,b) {b}");
    doTest("(List) a.g<caret>etAt(b)", "(List) a[b]");
    doTest("(List) a.g<caret>etAt(b + 1)", "(List) a[b + 1]");
    doTest("a.put<caret>At(b) { 1 }", "a[b] = { 1 }");

    doTest("""
              a.put<caret>At(b) {\s
                 return 1\s
             };""", """
             a[b] = {
                 return 1
             };""");
  }

  public void testWithoutAdditionalParenthesesOption() {
    inspection.withoutAdditionalParentheses = true;
    doTest("a.eq<caret>uals(b) == 1", "a == b == 1");
    doTest("1 == !a.eq<caret>uals(b)");
    doTest("a.eq<caret>uals(b) && c", "a == b && c");

    doTest("1 - a.m<caret>inus(b)");
    doTest("a.m<caret>inus(1 - b)");
    doTest("1 - a.p<caret>lus(b)");
    doTest("(\"1\" + 1).plus(1)", "(\"1\" + 1) + 1");

    doTest("a.asType(String)", "a as String");
    doTest("!a.asType(String)");
    doTest("a.as<caret>Type(String).toString()");

    doTest("a.g<caret>etAt(b).field", "a[b].field");
    doTest("a.p<caret>utAt(b, 1).field");

    doTest("[1, 2, 3].is<caret>Case(2-1)", "2 - 1 in [1, 2, 3]");
    doTest("![1, 2, 3].is<caret>Case(2-1)");

    doTest("! (a.compar<caret>eTo(b) < 0)", "!(a < b)");
    doTest("if ((2 - 1).compa<caret>reTo(2-1) > 0);", "if ((2 - 1) > 2 - 1);");
    doTest("(2 - 1).compa<caret>reTo(2) - 1");
    doTest("(2 - 1).compa<caret>reTo(2 | 1)");

    doTest("a.as<caret>Boolean() != b.asBoolean()", "!!a != b.asBoolean()");
    doTest("a.asBoolean().toString()");
  }

  public void test_super_calls() {
    getFixture().configureByText("_.groovy", """
      class Inheritor extends Operators {
        def testStuff(o) {
          super.bitwiseNegate()  \s
          super.negative()  \s
          super.positive()  \s
          super.next()  \s
          super.previous()  \s
          super.plus(o)  \s
          super.minus(o)  \s
          super.multiply(o)  \s
          super.power(o)  \s
          super.div(o)  \s
          super.mod(o)  \s
          super.or(o)  \s
          super.and(o)  \s
          super.xor(o)  \s
          super.leftShift(o)  \s
          super.rightShift(o)  \s
          super.rightShiftUnsigned(o)  \s
          super.asType(String)  \s
          super.getAt(o)  \s
          super.putAt(o, o)  \s
          super.asBoolean()  \s
          super.isCase(o)  \s
          super.equals(o)  \s
          super.compareTo(o)  \s
        }
      }
      """);
    getFixture().checkHighlighting();
  }

  public void test_non_dots() {
    doTest("a*.<caret>equals(String)");
    doTest("a?.<caret>equals(String)");
    doTest("a.&<caret>equals(String)");
  }

  private void doTest(String before, String after) {
    try (Closeable ignored = () -> getFixture().getEditor().getCaretModel().moveToOffset(0)) {
      getFixture().configureByText("_.groovy", "def (Operators a, Operators b) = [null, null]\n" + before);
      moveCaret();
      List<IntentionAction> intentions = getFixture().filterAvailableIntentions("Replace ");

      if (after != null) {
        assertNotNull("Intentions should not be null for: " + before, intentions);
        getFixture().launchAction(intentions.get(0));
        getFixture().checkResult("def (Operators a, Operators b) = [null, null]\n" + after);
      }
      else {
        assertEmpty(intentions);
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void doTest(String before) {
    doTest(before, null);
  }

  private void moveCaret() {
    GrStatement statement = last(((GroovyFile)getFixture().getFile()).getStatements());
    GrMethodCall call = null;

    if (statement instanceof GrMethodCall method) {
      call = method;
    }
    else if (statement instanceof GrUnaryExpression unary) {
      GrStatement operand = unary.getOperand();
      if (operand instanceof GrMethodCall method) {
        call = method;
      }
    }
    else if (statement instanceof GrBinaryExpression binary) {
      GrStatement left = binary.getLeftOperand();
      if (left instanceof GrMethodCall method) {
        call = method;
      }
    }

    CaretModel caretModel = getFixture().getEditor().getCaretModel();
    if (call != null && caretModel.getLogicalPosition().column == 0) {
      GrReferenceExpression invoked = (GrReferenceExpression)call.getInvokedExpression();
      caretModel.moveToOffset(invoked.getReferenceNameElement().getTextRange().getStartOffset());
    }
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  public final ChangeToOperatorInspection getInspection() {
    return inspection;
  }

  private final ChangeToOperatorInspection inspection = new ChangeToOperatorInspection();

  private static <T> T last(T... list) {
    return list[list.length - 1];
  }
}