// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.redundancy;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;

/**
 * @author Fabrice TIERCELIN
 */
public class UnusedLabelFixTest extends IGQuickFixesTestCase {
  @Override
  protected BaseInspection getInspection() {
    return new UnusedLabelInspection();
  }

  public void testRemoveLabel() {
    doMemberTest(InspectionGadgetsBundle.message("unused.label.remove.quickfix"),
                 "  public void myMethod(int count) {\n" +
                 "    label/**/: for (int i = 0; i < count; i++) {\n" +
                 "      if (i == 3) {\n" +
                 "        break;\n" +
                 "      }\n" +
                 "    }\n" +
                 "  }\n",

                 "  public void myMethod(int count) {\n" +
                 "    for (int i = 0; i < count; i++) {\n" +
                 "        if (i == 3) {\n" +
                 "            break;\n" +
                 "        }\n" +
                 "    }\n" +
                 "  }\n"
    );
  }

  public void testDoNotFixUsedLabel() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unused.label.remove.quickfix"),
                               "class Example {\n" +
                               "  public void myMethod(int count) {\n" +
                               "    label/**/: for (int i = 0; i < count; i++) {\n" +
                               "      for (int j = 0; j < count; j++) {\n" +
                               "        if (i == 3) {\n" +
                               "          break label;\n" +
                               "        }\n" +
                               "      }\n" +
                               "    }\n" +
                               "  }\n" +
                               "}\n"
    );
  }
}
