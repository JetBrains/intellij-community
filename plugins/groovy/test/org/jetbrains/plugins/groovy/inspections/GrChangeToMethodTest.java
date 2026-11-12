// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.inspections;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.changeToMethod.ChangeToMethodInspection;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class GrChangeToMethodTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Operators.groovy", """
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
    myFixture.enableInspections(getInspection());
  }

  @Test
  public void testSimpleUnaryExpression() {
    doTest("~a", "a.bitwiseNegate()");
    doTest("-a", "a.negative()");
    doTest("+a", "a.positive()");
    doTest("++a", "a.next()");
    doTest("--a", "a.previous()");
  }

  @Test
  public void testSimpleBinaryExpression() {
    LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(14);
    map.put("a + b", "a.plus(b)");
    map.put("a + { b }", "a.plus({ b })");
    map.put("a - b", "a.minus(b)");
    map.put("a * b", "a.multiply(b)");
    map.put("a / b", "a.div(b)");
    map.put("a**b", "a.power(b)");
    map.put("a % b", "a.mod(b)");
    map.put("a | b", "a.or(b)");
    map.put("a & b", "a.and(b)");
    map.put("a ^ b", "a.xor(b)");
    map.put("a << b", "a.leftShift(b)");
    map.put("a >> b", "a.rightShift(b)");
    map.put("a >>> b", "a.rightShiftUnsigned(b)");
    map.put("a in b", "b.isCase(a)");
    map.forEach(this::doTest);
  }

  @Test
  public void testAsType() {
    doTest("a as String", "a.asType(String)");
    doTest("!(a a<caret>s String)", "!a.asType(String)");
    doTest("a a<caret>s List<Integer>");
    doTest("a a<caret>s List", "a.asType(List)");
  }

  @Test
  public void testCompareTo() {
    doTest("a <=> b", "a.compareTo(b)");
    doTest("a < b", "a.compareTo(b) < 0");
    doTest("a <= b", "a.compareTo(b) <= 0");
    doTest("a >= b", "a.compareTo(b) >= 0");
    doTest("a > b", "a.compareTo(b) > 0");
    doTest("if ((2 - 1) ><caret> 3);", "if ((2 - 1).compareTo(3) > 0);");
    doTest("!(a <<caret> b)", "!(a.compareTo(b) < 0)");
  }

  @Test
  public void testEqualsExpression() {
    doTest("a == b", "a.equals(b)");
    doTest("a != b", "!a.equals(b)");
  }

  @Test
  public void testComplexBinaryExpression() {
    doTest("new Object() as List", "new Object().asType(List)");
    doTest("(a =<caret>= b * c) == 1", "a.equals(b * c) == 1");
    doTest("(b * c =<caret>= a) == 1", "(b * c).equals(a) == 1");
    doTest("a.plus(b) +<caret> c", "a.plus(b).plus(c)");

    doTest("if (2 - 1 in<caret> [1, 2, 3]);", "if ([1, 2, 3].isCase(2 - 1));");
    doTest("(a ^<caret> (a.b + 1) * b).equals(a)", "a.xor((a.b + 1) * b).equals(a)");

    doTest("a == b * c", "a.equals(b * c)");
    doTest("(Boolean) (a =<caret>= b)", "(Boolean) a.eq<caret>uals(b)");
  }

  @Test
  public void testSamePrioritiesExpression() {
    doTest("1 << 1 <<<caret> 1", "(1 << 1).leftShift(1)");
    doTest("1 <<<caret> 1 << 1", "1.leftShift(1) << 1");
    doTest("1 - (a -<caret> b)", "1 - a.minus(b)");
    doTest("a - b -<caret> 1", "(a - b).minus(1)");
  }

  private void doTest(String before, String after) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", getDECLARATIONS() + before);
    EdtTestUtil.runInEdtAndWait(() -> {
      moveCaret();
      List<IntentionAction> intentions = myFixture.filterAvailableIntentions("Replace with");
      if (after != null) {
        assertNotNull(intentions, before);
        myFixture.launchAction(intentions.get(0));
        myFixture.checkResult(getDECLARATIONS() + after);
      }
      else {
        assertThat(intentions).isNullOrEmpty();
      }
    });
  }

  private void doTest(String before) {
    doTest(before, null);
  }

  private void moveCaret() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GrStatement statement = last(((GroovyFile)myFixture.getFile()).getStatements());
    PsiElement element = null;
    if (statement instanceof GrUnaryExpression unary) {
      element = unary.getOperationToken();
    }
    else if (statement instanceof GrBinaryExpression binary) {
      element = binary.getOperationToken();
    }
    else if (statement instanceof GrSafeCastExpression safeCast) {
      element = safeCast.getOperationToken();
    }

    if (element != null && myFixture.getEditor().getCaretModel().getLogicalPosition().getColumn() == 0) {
      myFixture.getEditor().getCaretModel().moveToOffset(element.getTextRange().getStartOffset());
    }
  }

  public final ChangeToMethodInspection getInspection() {
    return inspection;
  }

  public static String getDECLARATIONS() {
    return "def (Operators a, Operators b) = [null, null]\n";
  }

  private final ChangeToMethodInspection inspection = new ChangeToMethodInspection();

  private static <T> T last(T... list) {
    return list[list.length - 1];
  }
}
