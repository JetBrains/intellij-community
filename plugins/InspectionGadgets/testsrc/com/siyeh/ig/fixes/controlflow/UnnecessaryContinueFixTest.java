// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.controlflow;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.UnnecessaryContinueInspection;

/**
 * @author Fabrice TIERCELIN
 */
public class UnnecessaryContinueFixTest extends IGQuickFixesTestCase {
  @Override
  protected BaseInspection getInspection() {
    return new UnnecessaryContinueInspection();
  }

  public void testRemoveContinue() {
    doMemberTest(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "continue"),
                 "  public void printName(String[] names) {\n" +
                 "    for (String name : names) {\n" +
                 "      System.out.println(name);\n" +
                 "      continue/**/;\n" +
                 "    }\n" +
                 "  }\n",
                 "  public void printName(String[] names) {\n" +
                 "    for (String name : names) {\n" +
                 "      System.out.println(name);\n" +
                 "    }\n" +
                 "  }\n"
    );
  }

  public void testRemoveContinueIntoIf() {
    doMemberTest(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "continue"),
                 "  public void printName(String[] names) {\n" +
                 "    for (String name : names) {\n" +
                 "      if (!\"foo\".equals(name)) {\n" +
                 "        System.out.println(name);\n" +
                 "        continue/**/;\n" +
                 "      }\n" +
                 "    }\n" +
                 "  }\n",
                 "  public void printName(String[] names) {\n" +
                 "    for (String name : names) {\n" +
                 "      if (!\"foo\".equals(name)) {\n" +
                 "        System.out.println(name);\n" +
                 "      }\n" +
                 "    }\n" +
                 "  }\n"
    );
  }

  public void testDoNotFixFollowedContinue() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "continue"),
                               "class X {\n" +
                               "  public void printName(String[] names) {\n" +
                               "    for (String name : names) {\n" +
                               "      if (!\"foo\".equals(name)) {\n" +
                               "        System.out.println(name);\n" +
                               "        continue/**/;\n" +
                               "      }\n" +
                               "      System.out.println(\"Ready for a new iteration\");\n" +
                               "    }\n" +
                               "  }\n" +
                               "}\n");
  }
}
