// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.controlflow;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.UnnecessaryBreakInspection;

/**
 * @author Fabrice TIERCELIN
 */
@SuppressWarnings("UnnecessaryBreak")
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
