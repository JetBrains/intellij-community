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
import com.siyeh.ig.controlflow.SimplifiableBooleanExpressionInspection;

/**
 * @author Bas Leijdekkers
 */
public class SimplifiableBooleanExpressionFixTest extends IGQuickFixesTestCase {

  public void testSimple() {
    doMemberTest(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix"),
                 "void test(boolean foo, boolean bar) {" +
                 "    boolean d = !(foo ^ /**/(bar != true));" +
                 "}",
                 "void test(boolean foo, boolean bar) {" +
                 "    boolean d = foo == (bar != true);" +
                 "}");
  }

  public void testAndOrExpression() {
    doMemberTest(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix"),
                 "boolean fff(boolean a, boolean b) {" +
                 "    return a && b /**/|| !a;" +
                 "}",
                 "boolean fff(boolean a, boolean b) {" +
                 "    return !a || b;" +
                 "}");
  }

  public void testAndOrExpression3() {
    doMemberTest(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix"),
                 "boolean fff(boolean a, boolean b, boolean c) {" +
                 "    return a && b && c/**/|| !a;" +
                 "}",
                 "boolean fff(boolean a, boolean b, boolean c) {" +
                 "    return !a || b && c;" +
                 "}");
  }

  public void testAndOrExpression3Middle() {
    // While this particular case could be safely transformed to "a && c || !b", the order of execution is changed which may
    // affect dereferencing (e.g. "a != null && b != null && a.foo(b.bar()) || b == null") is safe, but replacement is not.
    // Proper replacement would be "(a || !b) && (!b || c)", but it's not shorter than the original code
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix"),
                 "class X {\n" +
                 "  boolean fff(boolean a, boolean b, boolean c) { \n" +
                 "    return a && b && c/**/ || !b;\n" +
                 "  }\n" +
                 "}");
  }

  public void testAndOrExpression3Parentheses() {
    doMemberTest(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix"),
                 "boolean fff(boolean a, boolean b, boolean c) {" +
                 "    return (a && b && !c)/**/|| c;" +
                 "}",
                 "boolean fff(boolean a, boolean b, boolean c) {" +
                 "    return (a && b) || c;" +
                 "}");
  }

  public void testAndOrExpressionComparisons() {
    doMemberTest(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix"),
                 "boolean fff(int a, int b, int c) {" +
                 "    return a > b && b > c /**/|| a <= b;" +
                 "}",
                 "boolean fff(int a, int b, int c) {" +
                 "    return a <= b || b > c;" +
                 "}");
  }

  public void testAndOrNonNegated() {
    doMemberTest(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix"),
                 "boolean fff(int a, int b, int c) {" +
                 "    return a > b && b > c && b > 0 /**/|| (b > c);" +
                 "}",
                 "boolean fff(int a, int b, int c) {" +
                 "    return b > c;" +
                 "}");
  }

  @Override
  protected BaseInspection getInspection() {
    return new SimplifiableBooleanExpressionInspection();
  }
}
