// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.controlflow;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.ExpressionMayBeFactorizedInspection;

/**
 * @author Fabrice TIERCELIN
 */
public class ExpressionMayBeFactorizedFixTest extends IGQuickFixesTestCase {

  public void testSimple() {
    doMemberTest(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                 "boolean test(boolean duplicateExpression, boolean thenExpression, boolean elseExpression) {\n" +
                 "    return (duplicateExpression &&/**/ thenExpression) || (duplicateExpression && elseExpression);\n" +
                 "}",
                 "boolean test(boolean duplicateExpression, boolean thenExpression, boolean elseExpression) {\n" +
                 "    return duplicateExpression && (thenExpression || elseExpression);\n" +
                 "}"
    );
  }

  public void testFactorInControlWorkflow() {
    doMemberTest(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                 "char test(boolean duplicateExpression, boolean thenExpression, boolean elseExpression) {\n" +
                 "    if ((duplicateExpression &&/**/ thenExpression) || (duplicateExpression && elseExpression)) {\n" +
                 "        return 'a';\n" +
                 "    } else {\n" +
                 "        return 'b';\n" +
                 "    }\n" +
                 "}",
                 "char test(boolean duplicateExpression, boolean thenExpression, boolean elseExpression) {\n" +
                 "    if (duplicateExpression && (thenExpression || elseExpression)) {\n" +
                 "        return 'a';\n" +
                 "    } else {\n" +
                 "        return 'b';\n" +
                 "    }\n" +
                 "}"
    );
  }

  public void testFactorLast() {
    doMemberTest(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                 "boolean test(int duplicateExpression, int thenExpression, int elseExpression) {\n" +
                 "    return (thenExpression == 2 &&/**/ duplicateExpression == 1) || (elseExpression == 3 && duplicateExpression == 1);\n" +
                 "}",
                 "boolean test(int duplicateExpression, int thenExpression, int elseExpression) {\n" +
                 "    return (thenExpression == 2 || elseExpression == 3) && duplicateExpression == 1;\n" +
                 "}"
    );
  }

  public void testFactorInMiddle() {
    doMemberTest(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                 "boolean test(boolean duplicateExpression, boolean payload) {\n" +
                 "    boolean reassigned = false;\n" +
                 "    return (true &/**/ duplicateExpression) | duplicateExpression & ((reassigned = payload));\n" +
                 "}",
                 "boolean test(boolean duplicateExpression, boolean payload) {\n" +
                 "    boolean reassigned = false;\n" +
                 "    return duplicateExpression & (true | (reassigned = payload));\n" +
                 "}"
    );
  }

  public void testOrInsideAnd() {
    doMemberTest(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                 "boolean test(boolean duplicateExpression, boolean thenExpression, boolean elseExpression) {\n" +
                 "    return (duplicateExpression ||/**/ thenExpression) && (duplicateExpression || elseExpression);\n" +
                 "}",
                 "boolean test(boolean duplicateExpression, boolean thenExpression, boolean elseExpression) {\n" +
                 "    return duplicateExpression || (thenExpression && elseExpression);\n" +
                 "}"
    );
  }

  public void testEagerOperators() {
    doMemberTest(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                 "boolean test(boolean duplicateExpression, boolean payload) {\n" +
                 "    boolean reassigned = false;\n" +
                 "    return ((reassigned = payload)/**/ & duplicateExpression) | (true & duplicateExpression);\n" +
                 "}",
                 "boolean test(boolean duplicateExpression, boolean payload) {\n" +
                 "    boolean reassigned = false;\n" +
                 "    return ((reassigned = payload) | true) & duplicateExpression;\n" +
                 "}"
    );
  }

  public void testNumericExpression() {
    doMemberTest(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                 "int test(int duplicateExpression, int payload) {\n" +
                 "    int reassigned = 1;\n" +
                 "    return ((reassigned = payload)/**/ & duplicateExpression) | (2 & duplicateExpression);\n" +
                 "}",
                 "int test(int duplicateExpression, int payload) {\n" +
                 "    int reassigned = 1;\n" +
                 "    return ((reassigned = payload) | 2) & duplicateExpression;\n" +
                 "}"
    );
  }

  public void testNumericOperation() {
    doMemberTest(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                 "int fullHeight(int rowHeight, int rowCount, int rowMargin) {\n" +
                 "  return rowHeight * rowCount + /**/rowMargin * rowCount;\n" +
                 "}",
                 "int fullHeight(int rowHeight, int rowCount, int rowMargin) {\n" +
                 "  return (rowHeight + rowMargin) * rowCount;\n" +
                 "}");
  }

  public void testNumericOperation2() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                               "class X {\n" +
                               "  int fullHeight(int rowHeight, int rowCount, int rowMargin) {\n" +
                               "    return rowHeight + rowCount * /**/rowMargin + rowCount;\n" +
                               "  }" +
                               "}");
  }

  public void testFinalEagerActiveExpression() {
    doMemberTest(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                 "boolean test(int x, int y, int updatedState, int newState) {\n" +
                 "    return (x > y & updatedState == -1) | /**/(y < x & (updatedState = newState) == 0);\n" +
                 "  }",
                 "boolean test(int x, int y, int updatedState, int newState) {\n" +
                 "    return y < x & (updatedState == -1 | (updatedState = newState) == 0);\n" +
                 "  }");
  }

  public void testComparison() {
    doTest(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
           "class X {\n" +
           "  boolean test(int x, int y, int z) {\n" +
           "    return (x > y && z == 1) || /**/(y < x && z == 2);\n" +
           "  }\n" +
           "}",
           "class X {\n" +
           "  boolean test(int x, int y, int z) {\n" +
           "    return y < x && (z == 1 || z == 2);\n" +
           "  }\n" +
           "}");
  }

  public void testDoNotCleanupOnExpressionWithSideEffects() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                               "class X {\n" +
                               "  boolean elseExpression = true;\n" +
                               "  boolean test(boolean thenExpression) {\n" +
                               "    return activeMethod() && thenExpression ||/**/ activeMethod() && elseExpression;\n" +
                               "  }\n" +
                               "  boolean activeMethod() {\n" +
                               "    elseExpression = !elseExpression;\n" +
                               "    return true;\n" +
                               "  }\n" +
                               "}\n");
  }

  public void testDoNotCleanupWithXOrOperator() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                               "class X {\n" +
                               "  boolean test() {\n" +
                               "    boolean duplicateExpression = true;\n" +
                               "    boolean thenExpression = false;\n" +
                               "    boolean elseExpression = true;\n" +
                               "    return (duplicateExpression ^ thenExpression) &&/**/ (duplicateExpression ^ elseExpression);\n" +
                               "  }\n" +
                               "}\n");
  }

  public void testDoNotCleanupWithFinalLazyActiveExpression() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                               "class X {\n" +
                               "  boolean test() {\n" +
                               "    boolean duplicateExpression = true;\n" +
                               "    boolean thenExpression = true;\n" +
                               "    boolean updatedExpression = true;\n" +
                               "    boolean newValue = false;\n" +
                               "    boolean evaluation = (duplicateExpression || thenExpression) &&/**/ (duplicateExpression || (updatedExpression = newValue));\n" +
                               "    return updatedExpression;\n" +
                               "  }\n" +
                               "}\n");
  }

  public void testDoNotCleanupWithNotFinalEagerActiveExpression() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                               "class X {\n" +
                               "  boolean test() {\n" +
                               "    boolean duplicateExpression = true;\n" +
                               "    boolean newValue = false;\n" +
                               "    boolean elseExpression = false;\n" +
                               "    return (duplicateExpression | (duplicateExpression = newValue)) &/**/ (duplicateExpression | elseExpression);\n" +
                               "  }\n" +
                               "}\n");
  }

  public void testDoNotCleanupWithTrailingOperands() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                               "class X {\n" +
                               "  boolean test(boolean evilExpression) {\n" +
                               "    boolean duplicateExpression = true;\n" +
                               "    boolean thenExpression = false;\n" +
                               "    boolean elseExpression = true;\n" +
                               "    return (duplicateExpression || thenExpression) &&/**/ (duplicateExpression || elseExpression) && evilExpression;\n" +
                               "  }\n" +
                               "}\n");
  }

  public void testDoNotCleanupOnIncrementedDuplicateExpression() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                               "class X {\n" +
                               "  int test() {\n" +
                               "    int duplicateExpression = 0;\n" +
                               "    boolean thenExpression = false;\n" +
                               "    boolean elseExpression = false;\n" +
                               "    boolean dummy = thenExpression && (duplicateExpression++ < 100) ||/**/ elseExpression && (duplicateExpression++ < 100);\n" +
                               "    return duplicateExpression;\n" +
                               "  }\n" +
                               "}\n");
  }

  public void testDoNotCleanupOperandWithSideEffect() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                               "class X {\n" +
                               "  boolean test() {\n" +
                               "    boolean duplicateExpression = true;\n" +
                               "    boolean expressionWithSideEffect = false;\n" +
                               "    boolean elseExpression = false;\n" +
                               "    boolean dummy = (duplicateExpression || (expressionWithSideEffect = true)) &&/**/ (elseExpression || duplicateExpression);\n" +
                               "    return expressionWithSideEffect;\n" +
                               "  }\n" +
                               "}\n");
  }

  public void testDoNotCleanupOperandWithSideEffectAtTheEnd() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                               "class X {\n" +
                               "  boolean test() {\n" +
                               "    boolean duplicateExpression = false;\n" +
                               "    boolean thenExpression = true;\n" +
                               "    boolean expressionWithSideEffect = false;\n" +
                               "    boolean dummy = (thenExpression || duplicateExpression) &&/**/ (duplicateExpression || (expressionWithSideEffect = true));\n" +
                               "    return expressionWithSideEffect;\n" +
                               "  }\n" +
                               "}\n");
  }

  public void testDoNotCleanupWithSkippedThrowingExceptionOperand() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                               "class X {\n" +
                               "  boolean test() {\n" +
                               "    boolean duplicateExpression = false;\n" +
                               "    boolean thenExpression = true;\n" +
                               "    return (duplicateExpression && thenExpression) ||/**/ (duplicateExpression && throwingException());\n" +
                               "  }\n" +
                               "  boolean throwingException() {\n" +
                               "    throw null;\n" +
                               "  }\n" +
                               "}\n");
  }

  @Override
  protected BaseInspection getInspection() {
    return new ExpressionMayBeFactorizedInspection();
  }
}
