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

  @Override
  protected BaseInspection getInspection() {
    return new SimplifiableBooleanExpressionInspection();
  }
}
