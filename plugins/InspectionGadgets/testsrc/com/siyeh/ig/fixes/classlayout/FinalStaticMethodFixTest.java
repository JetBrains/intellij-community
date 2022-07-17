// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.classlayout;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.classlayout.FinalStaticMethodInspection;

/**
 * @author Fabrice TIERCELIN
 */
public class FinalStaticMethodFixTest extends IGQuickFixesTestCase {
  @Override
  protected BaseInspection getInspection() {
    return new FinalStaticMethodInspection();
  }

  public void testSimple() {
    doMemberTest(InspectionGadgetsBundle.message("remove.modifier.quickfix", "final"),
                 "public static final/**/ boolean isPositive(int number) {\n" +
                 "    return number > 0;\n" +
                 "}",
                 "public static boolean isPositive(int number) {\n" +
                 "    return number > 0;\n" +
                 "}"
    );
  }

  public void testDoNotFixOnInstanceMethod() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("remove.modifier.quickfix", "final"),
                               "class X {\n" +
                               "  public final boolean isPositive(int number) {\n" +
                               "    return number > 0;\n" +
                               "  }\n" +
                               "}\n");
  }
}
