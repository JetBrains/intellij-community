/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.fixes.controlflow;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.BooleanExpressionMayBeConditionalInspection;

/**
 * @author Bas Leijdekkers
 */
public class BooleanExpressionMayBeConditionalFixTest extends IGQuickFixesTestCase {

  public void testSimple() {
    doMemberTest(InspectionGadgetsBundle.message("if.may.be.conditional.quickfix"),
                 "void test(boolean foo, boolean bar) {\n" +
                 "    boolean c = false;\n" +
                 "    boolean b = (!foo &&/**/ (c = bar)) || (foo && true);" +
                 "}",
                 "void test(boolean foo, boolean bar) {\n" +
                 "    boolean c = false;\n" +
                 "    boolean b = foo ? true : (c = bar);}"
                 );
  }

  public void testSideEffects1() {
    doTest(InspectionGadgetsBundle.message("if.may.be.conditional.quickfix"),
           "class X {" +
           "  boolean b = true;" +
           "  void test(boolean foo) {" +
           "    boolean c = b && foo ||/**/ !b && complex();" +
           "  }" +
           "  boolean complex() {" +
           "    b = !b;" +
           "    return true;" +
           "  }" +
           "}",
           "class X {" +
           "  boolean b = true;" +
           "  void test(boolean foo) {" +
           "    boolean c = b ? foo : complex();" +
           "  }" +
           "  boolean complex() {" +
           "    b = !b;" +
           "    return true;" +
           "  }" +
           "}");
  }

  public void testSideEffects2() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("if.may.be.conditional.quickfix"),
           "class X {" +
           "  boolean b = true;" +
           "  void test(boolean foo) {" +
           "    boolean c = complex() && foo ||/**/ !complex() && b;" +
           "  }" +
           "  boolean complex() {" +
           "    b = !b;" +
           "    return true;" +
           "  }" +
           "}");
  }

  public void testComparison() {
    doTest(InspectionGadgetsBundle.message("if.may.be.conditional.quickfix"),
           "class X {\n" +
           "  boolean test(int x, int y, int z) {\n" +
           "    return (x > y && z == 1) || /**/(x <= y && z == 2);\n" +
           "  }\n" +
           "}",
           "class X {\n" +
           "  boolean test(int x, int y, int z) {\n" +
           "    return x > y ? z == 1 : z == 2;\n" +
           "  }\n" +
           "}");
  }

  @Override
  protected BaseInspection getInspection() {
    return new BooleanExpressionMayBeConditionalInspection();
  }
}
