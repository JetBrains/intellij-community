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
import com.siyeh.ig.classlayout.FinalStaticMethodInspection;
import com.siyeh.ig.controlflow.ConfusingElseInspection;

/**
 * @author Fabrice TIERCELIN
 */
public class ConfusingElseFixTest extends IGQuickFixesTestCase {
  @Override
  protected BaseInspection getInspection() {
    return new ConfusingElseInspection();
  }

  public void testRemoveElse() {
    doMemberTest(InspectionGadgetsBundle.message("redundant.else.unwrap.quickfix"),
                 "  public void printName(String name) {\n" +
                 "    if (name == null) {\n" +
                 "        throw new IllegalArgumentException();\n" +
                 "    } else/**/ {\n" +
                 "        System.out.println(name);\n" +
                 "    }\n" +
                 "}\n",
                 "  public void printName(String name) {\n" +
                 "    if (name == null) {\n" +
                 "        throw new IllegalArgumentException();\n" +
                 "    }\n" +
                 "    System.out.println(name);\n" +
                 "}\n"
    );
  }

  public void testReturnStatement() {
    doMemberTest(InspectionGadgetsBundle.message("redundant.else.unwrap.quickfix"),
                 "  public int printName(String name) {\n" +
                 "    if (name == null) {\n" +
                 "        return -1;\n" +
                 "    } else/**/ {\n" +
                 "        System.out.println(name);\n" +
                 "    }\n" +
                 "    return 0;\n" +
                 "  }\n",
                 "  public int printName(String name) {\n" +
                 "    if (name == null) {\n" +
                 "        return -1;\n" +
                 "    }\n" +
                 "    System.out.println(name);\n" +
                 "    return 0;\n" +
                 "  }\n"
    );
  }

  public void testBreakStatement() {
    doMemberTest(InspectionGadgetsBundle.message("redundant.else.unwrap.quickfix"),
                 "  public void printName(String[] texts) {\n" +
                 "    for (String text : texts) {\n" +
                 "      if (\"illegal\".equals(text)) {\n" +
                 "        break;\n" +
                 "      } else/**/ {\n" +
                 "        System.out.println(text);\n" +
                 "      }\n" +
                 "    }\n" +
                 "  }\n",
                 "  public void printName(String[] texts) {\n" +
                 "    for (String text : texts) {\n" +
                 "      if (\"illegal\".equals(text)) {\n" +
                 "        break;\n" +
                 "      }\n" +
                 "        System.out.println(text);\n" +
                 "    }\n" +
                 "  }\n"
    );
  }

  public void testContinueStatement() {
    doMemberTest(InspectionGadgetsBundle.message("redundant.else.unwrap.quickfix"),
                 "  public void printName(String[] texts) {\n" +
                 "    for (String text : texts) {\n" +
                 "      if (\"illegal\".equals(text)) {\n" +
                 "        continue;\n" +
                 "      } else/**/ {\n" +
                 "        System.out.println(text);\n" +
                 "      }\n" +
                 "    }\n" +
                 "  }\n",
                 "  public void printName(String[] texts) {\n" +
                 "    for (String text : texts) {\n" +
                 "      if (\"illegal\".equals(text)) {\n" +
                 "        continue;\n" +
                 "      }\n" +
                 "        System.out.println(text);\n" +
                 "    }\n" +
                 "  }\n"
    );
  }

  public void testDoNotFixElseWithoutJump() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("redundant.else.unwrap.quickfix"),
                               "class X {\n" +
                               "  public void printName(String name) {\n" +
                               "    if (name == null) {\n" +
                               "        System.out.println(\"illegal\");\n" +
                               "    } else/**/ {\n" +
                               "        System.out.println(name);\n" +
                               "    }\n" +
                               "  }\n" +
                               "}\n");
  }
}
