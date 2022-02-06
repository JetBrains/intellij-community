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

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.ConfusingElseInspection;
import com.siyeh.ig.controlflow.UnnecessaryBreakInspection;

/**
 * @author Fabrice TIERCELIN
 */
public class UnnecessaryBreakFixTest extends IGQuickFixesTestCase {
  @Override
  protected void tuneFixture(final JavaModuleFixtureBuilder builder) throws Exception {
    builder.setLanguageLevel(LanguageLevel.JDK_16);
  }

  @Override
  protected BaseInspection getInspection() {
    return new UnnecessaryBreakInspection();
  }

  public void testRemoveBreakOnCase() {
    doMemberTest(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "break"),
                 "  public void printName(String name) {\n" +
                 "    switch (name) {\n" +
                 "      case \"A\" -> {\n" +
                 "          System.out.println(\"A\");\n" +
                 "          break/**/; // reports 'break' statement is unnecessary\n" +
                 "      }\n" +
                 "      default -> {\n" +
                 "          System.out.println(\"Default\");\n" +
                 "          break; // reports 'break' statement is unnecessary\n" +
                 "      }\n" +
                 "    }\n" +
                 "  }\n",
                 "  public void printName(String name) {\n" +
                 "    switch (name) {\n" +
                 "      case \"A\" -> {\n" +
                 "          System.out.println(\"A\");\n" +
                 "          // reports 'break' statement is unnecessary\n" +
                 "      }\n" +
                 "      default -> {\n" +
                 "          System.out.println(\"Default\");\n" +
                 "          break; // reports 'break' statement is unnecessary\n" +
                 "      }\n" +
                 "    }\n" +
                 "  }\n"
    );
  }

  public void testRemoveBreakOnDefault() {
    doMemberTest(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "break"),
                 "  public void printName(String name) {\n" +
                 "    switch (name) {\n" +
                 "      case \"A\" -> {\n" +
                 "          System.out.println(\"A\");\n" +
                 "          break; // reports 'break' statement is unnecessary\n" +
                 "      }\n" +
                 "      default -> {\n" +
                 "          System.out.println(\"Default\");\n" +
                 "          break/**/; // reports 'break' statement is unnecessary\n" +
                 "      }\n" +
                 "    }\n" +
                 "  }\n",
                 "  public void printName(String name) {\n" +
                 "    switch (name) {\n" +
                 "      case \"A\" -> {\n" +
                 "          System.out.println(\"A\");\n" +
                 "          break; // reports 'break' statement is unnecessary\n" +
                 "      }\n" +
                 "      default -> {\n" +
                 "          System.out.println(\"Default\");\n" +
                 "          // reports 'break' statement is unnecessary\n" +
                 "      }\n" +
                 "    }\n" +
                 "  }\n"
    );
  }

  public void testDoNotFixClassicSwitch() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "break"),
                               "class X {\n" +
                               "  public void printName(String name) {\n" +
                               "    switch (name) {\n" +
                               "      case \"A\" : {\n" +
                               "          System.out.println(\"A\");\n" +
                               "          break/**/;\n" +
                               "      }\n" +
                               "      default : {\n" +
                               "          System.out.println(\"Default\");\n" +
                               "          break;\n" +
                               "      }\n" +
                               "    }\n" +
                               "  }\n" +
                               "}\n");
  }

  public void testDoNotFixLabelledBreak() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "break"),
                               "class X {\n" +
                               "  public void printName(String[] names) {\n" +
                               "    label: for (String name : names) {\n" +
                               "      switch (name) {\n" +
                               "        case \"A\" -> {\n" +
                               "            System.out.println(\"A\");\n" +
                               "            break/**/ label;\n" +
                               "        }\n" +
                               "        default -> {\n" +
                               "            System.out.println(\"Default\");\n" +
                               "            break label;\n" +
                               "        }\n" +
                               "      }\n" +
                               "    }\n" +
                               "  }\n" +
                               "}\n");
  }
}
