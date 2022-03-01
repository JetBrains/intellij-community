// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.controlflow;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.UnnecessaryLabelOnBreakStatementInspection;

/**
 * @author Fabrice TIERCELIN
 */
public class UnnecessaryLabelOnBreakStatementFixTest extends IGQuickFixesTestCase {
  @Override
  protected BaseInspection getInspection() {
    return new UnnecessaryLabelOnBreakStatementInspection();
  }

  public void testRemoveBreakLabel() {
    doMemberTest(InspectionGadgetsBundle.message("unnecessary.label.remove.quickfix"),
                 "  public void printName(String name) {\n" +
                 "    label:\n" +
                 "    for (int i = 0; i < 10; i++) {\n" +
                 "      if (shouldBreak()) break label/**/;\n" +
                 "      for (int j = 0; j < 10; j++) {\n" +
                 "        if (shouldBreak()) break label;\n" +
                 "        System.out.println(\"B\");\n" +
                 "      }\n" +
                 "    }\n" +
                 "  }\n",
                 "  public void printName(String name) {\n" +
                 "    label:\n" +
                 "    for (int i = 0; i < 10; i++) {\n" +
                 "      if (shouldBreak()) break;\n" +
                 "      for (int j = 0; j < 10; j++) {\n" +
                 "        if (shouldBreak()) break label;\n" +
                 "        System.out.println(\"B\");\n" +
                 "      }\n" +
                 "    }\n" +
                 "  }\n"
    );
  }

  public void testRemoveBreakLabelOnWhile() {
    doMemberTest(InspectionGadgetsBundle.message("unnecessary.label.remove.quickfix"),
                 "  public void printName(int i) {\n" +
                 "    label:\n" +
                 "    while (i < 100) {\n" +
                 "      if (i == 50) break label/**/;\n" +
                 "      for (int j = 0; j < 10; j++) {\n" +
                 "        if (shouldBreak()) break label;\n" +
                 "        System.out.println(\"B\");\n" +
                 "      }\n" +
                 "      i *= 2;\n" +
                 "    }\n" +
                 "  }\n",
                 "  public void printName(int i) {\n" +
                 "    label:\n" +
                 "    while (i < 100) {\n" +
                 "      if (i == 50) break;\n" +
                 "      for (int j = 0; j < 10; j++) {\n" +
                 "        if (shouldBreak()) break label;\n" +
                 "        System.out.println(\"B\");\n" +
                 "      }\n" +
                 "      i *= 2;\n" +
                 "    }\n" +
                 "  }\n"
    );
  }

  public void testDoNotFixMeaningfulLabel() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.label.remove.quickfix"),
                               "class X {\n" +
                               "  public void printName(String[] names) {\n" +
                               "    label:\n" +
                               "    for (int i = 0; i < 10; i++) {\n" +
                               "      for (String name : names) {\n" +
                               "        if (\"A\".equals(name)) break label/**/;\n" +
                               "        System.out.println(i);\n" +
                               "      }\n" +
                               "    }\n" +
                               "  }\n" +
                               "}\n");
  }
}
