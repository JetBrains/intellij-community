// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.controlflow;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.UnnecessaryReturnInspection;

/**
 * @author Fabrice TIERCELIN
 */
public class UnnecessaryReturnFixTest extends IGQuickFixesTestCase {
  @Override
  protected BaseInspection getInspection() {
    return new UnnecessaryReturnInspection();
  }

  public void testRemoveReturn() {
    doMemberTest(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "return"),
                 "  public void printName(String name) {\n" +
                 "    System.out.println(name);\n" +
                 "    return/**/;\n" +
                 "}\n",
                 "  public void printName(String name) {\n" +
                 "    System.out.println(name);\n" +
                 "}\n"
    );
  }

  public void testRemoveReturnIntoIf() {
    doMemberTest(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "return"),
                 "  public void printName(String name) {\n" +
                 "    if (!\"foo\".equals(name)) {\n" +
                 "      System.out.println(name);\n" +
                 "      return/**/;\n" +
                 "    }\n" +
                 "  }\n",
                 "  public void printName(String name) {\n" +
                 "    if (!\"foo\".equals(name)) {\n" +
                 "      System.out.println(name);\n" +
                 "    }\n" +
                 "  }\n"
    );
  }

  public void testRemoveReturnInConstructor() {
    doTest(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "return"),

                 "class X {\n" +
                 "  public X(String name) {\n" +
                 "    if (!\"foo\".equals(name)) {\n" +
                 "      System.out.println(name);\n" +
                 "      return/**/;\n" +
                 "    }\n" +
                 "  }\n" +
                 "}\n",

                 "class X {\n" +
                 "  public X(String name) {\n" +
                 "    if (!\"foo\".equals(name)) {\n" +
                 "      System.out.println(name);\n" +
                 "    }\n" +
                 "  }\n" +
                 "}\n"
    );
  }

  public void testDoNotFixReturnWithValue() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "return"),
                               "class X {\n" +
                               "  public int printName(String name) {\n" +
                               "    System.out.println(name);\n" +
                               "    return/**/ 0;\n" +
                               "  }\n" +
                               "}\n");
  }

  public void testDoNotFixFollowedReturn() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "return"),
                               "class X {\n" +
                               "  public void printName(String name) {\n" +
                               "    if (!\"foo\".equals(name)) {\n" +
                               "      System.out.println(name);\n" +
                               "      return/**/;\n" +
                               "    }\n" +
                               "    System.out.println(\"Bad code\");\n" +
                               "  }\n" +
                               "}\n");
  }
}
