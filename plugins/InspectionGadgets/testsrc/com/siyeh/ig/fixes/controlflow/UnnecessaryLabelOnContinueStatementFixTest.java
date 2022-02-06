/*
 * Copyright 2000-2022 JetBrains s.r.o.
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
import com.siyeh.ig.controlflow.UnnecessaryLabelOnBreakStatementInspection;
import com.siyeh.ig.controlflow.UnnecessaryLabelOnContinueStatementInspection;

/**
 * @author Fabrice TIERCELIN
 */
public class UnnecessaryLabelOnContinueStatementFixTest extends IGQuickFixesTestCase {
  @Override
  protected BaseInspection getInspection() {
    return new UnnecessaryLabelOnContinueStatementInspection();
  }

  public void testRemoveContinueLabel() {
    doMemberTest(InspectionGadgetsBundle.message("unnecessary.label.remove.quickfix"),
                 "  public void printName(String name) {\n" +
                 "    label:\n" +
                 "    for (int i = 0; i < 10; i++) {\n" +
                 "      if (shouldBreak()) continue label/**/;\n" +
                 "      for (int j = 0; j < 10; j++) {\n" +
                 "        if (shouldBreak()) break label;\n" +
                 "        System.out.println(\"B\");\n" +
                 "      }\n" +
                 "    }\n" +
                 "  }\n",
                 "  public void printName(String name) {\n" +
                 "    label:\n" +
                 "    for (int i = 0; i < 10; i++) {\n" +
                 "      if (shouldBreak()) continue;\n" +
                 "      for (int j = 0; j < 10; j++) {\n" +
                 "        if (shouldBreak()) break label;\n" +
                 "        System.out.println(\"B\");\n" +
                 "      }\n" +
                 "    }\n" +
                 "  }\n"
    );
  }

  public void testRemoveContinueLabelOnWhile() {
    doMemberTest(InspectionGadgetsBundle.message("unnecessary.label.remove.quickfix"),
                 "  public void printName(int i) {\n" +
                 "    label:\n" +
                 "    while (i < 100) {\n" +
                 "      if (i == 50) continue label/**/;\n" +
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
                 "      if (i == 50) continue;\n" +
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
                               "        if (\"A\".equals(name)) continue label/**/;\n" +
                               "        System.out.println(i);\n" +
                               "      }\n" +
                               "    }\n" +
                               "  }\n" +
                               "}\n");
  }
}
