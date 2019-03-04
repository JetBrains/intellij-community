// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class UnnecessaryDefaultInspectionTest extends LightInspectionTestCase {

  public void testUnnecessaryDefault() {
    doTest();
  }

  public void testSwitchExpression() {
    doTest("class X {\n" +
           "  boolean x(E e) {\n" +
           "    return switch (e) {\n" +
           "      case A, B -> true;\n" +
           "      /*'default' branch is unnecessary*//*_*/default/**/ -> false;\n" +
           "    };\n" +
           "  }\n" +
           "}");
    checkQuickFix("Remove 'default' branch",
                  "class X {\n" +
                  "  boolean x(E e) {\n" +
                  "    return switch (e) {\n" +
                  "      case A, B -> true;\n" +
                  "    };\n" +
                  "  }\n" +
                  "}");
  }

  public void testSwitchFallthrough() {
    doTest("class X {\n" +
           "  void x(E e) {\n" +
           "    switch (e) {\n" +
           "      case A,B:\n" +
           "          System.out.println(e);\n" +
           "      /*'default' branch is unnecessary*/default/*_*//**/:\n" +
           "          System.out.println();\n" +
           "    }\n" +
           "  }\n" +
           "}\n");
    checkQuickFix("Remove 'default' branch",
                  "class X {\n" +
                  "  void x(E e) {\n" +
                  "    switch (e) {\n" +
                  "      case A,B:\n" +
                  "          System.out.println(e);\n" +
                  "          System.out.println();\n" +
                  "    }\n" +
                  "  }\n" +
                  "}\n");
  }

  public void testDeclarationInBranch() {
    doTest("class X {" +
           "  void x(E e) {" +
           "    switch (e) {" +
           "      /*'default' branch is unnecessary*/default/*_*//**/:" +
           "        int x = 1;" +
           "        System.out.println(x);" +
           "      case A,B:" +
           "        x = 2;" +
           "        System.out.println(x);" +
           "      }" +
           "   }" +
           "}");
    checkQuickFix("Remove 'default' branch",
                  "class X {" +
                  "  void x(E e) {" +
                  "    switch (e) {\n" +
                  "    case A,B:\n" +
                  "        int x;\n" +
                  "        x = 2;" +
                  "        System.out.println(x);" +
                  "      }" +
                  "   }" +
                  "}");
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "enum E { A, B }"
    };
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final UnnecessaryDefaultInspection inspection = new UnnecessaryDefaultInspection();
    inspection.onlyReportSwitchExpressions = false;
    return inspection;
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_12;
  }
}