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
import com.siyeh.ig.controlflow.UnnecessaryContinueInspection;
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
