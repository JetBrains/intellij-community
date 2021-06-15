// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.controlflow;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.BooleanExpressionMayBeFactorizedInspection;

/**
 * @author Fabrice TIERCELIN
 */
public class BooleanExpressionMayBeFactorizedFixTest extends IGQuickFixesTestCase {

  public void testSimple() {
    doMemberTest(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
                 "void test(boolean foo, boolean bar) {\n" +
                 "    boolean c = false;\n" +
                 "    boolean b = (foo &&/**/ (c = bar)) || (foo && true);" +
                 "}",
                 "void test(boolean foo, boolean bar) {\n" +
                 "    boolean c = false;\n" +
                 "    boolean b = foo && ((c = bar) || true);}"
                 );
  }

  public void testSideEffects1() {
    doTest(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
           "class X {" +
           "  boolean b = true;" +
           "  void test(boolean foo) {" +
           "    boolean c = b && foo ||/**/ b && complex();" +
           "  }" +
           "  boolean complex() {" +
           "    b = !b;" +
           "    return true;" +
           "  }" +
           "}",
           "class X {" +
           "  boolean b = true;" +
           "  void test(boolean foo) {" +
           "    boolean c = b && (foo || complex());" +
           "  }" +
           "  boolean complex() {" +
           "    b = !b;" +
           "    return true;" +
           "  }" +
           "}");
  }

  public void testSideEffects2() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("if.may.be.factorized.quickfix"),
           "class X {" +
           "  boolean b = true;" +
           "  void test(boolean foo) {" +
           "    boolean c = complex() && foo ||/**/ complex() && b;" +
           "  }" +
           "  boolean complex() {" +
           "    b = !b;" +
           "    return true;" +
           "  }" +
           "}");
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
           "    return x > y && (z == 1 || z == 2);\n" +
           "  }\n" +
           "}");
  }

  @Override
  protected BaseInspection getInspection() {
    return new BooleanExpressionMayBeFactorizedInspection();
  }
}
