// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.controlflow;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.ConditionalCanBePushedInsideExpressionInspection;

public class ConditionalCanBePushedInsideExpressionFixTest extends IGQuickFixesTestCase {


  public void testSideEffects1() {
    doTest(InspectionGadgetsBundle.message("conditional.can.be.pushed.inside.expression.quickfix"),
           "class X {\n" +
           "    String foo(boolean b) {\n" +
           "        return b ? String.v<caret>alueOf(new Double(0)) //c1\n" +
           "                : String.valueOf(new Double(1.2))//c2\n" +
           "                ;\n" +
           "    }\n" +
           "}",
           "class X {\n" +
           "    String foo(boolean b) {\n" +
           "        //c1\n" +
           "        return String.valueOf(new Double(b ? 0 : 1.2))//c2\n" +
           "                ;\n" +
           "    }\n" +
           "}");
  }
  @Override
  protected BaseInspection getInspection() {
    return new ConditionalCanBePushedInsideExpressionInspection();
  }
}
