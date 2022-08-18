// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.controlflow;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.UnnecessaryDefaultInspection;

/**
 * @author Fabrice TIERCELIN
 */
public class UnnecessaryDefaultFixTest extends IGQuickFixesTestCase {
  @Override
  protected void tuneFixture(final JavaModuleFixtureBuilder builder) throws Exception {
    builder.setLanguageLevel(LanguageLevel.JDK_16);
  }

  @Override
  protected BaseInspection getInspection() {
    return new UnnecessaryDefaultInspection();
  }

  public void testRemoveReturn() {
    doMemberTest(InspectionGadgetsBundle.message("unnecessary.default.quickfix"),
                 "  enum E { A, B }\n" +
                 "  int foo(E e) {\n" +
                 "    return switch (e) {\n" +
                 "      case A -> 1;\n" +
                 "      case B -> 2;\n" +
                 "      default/**/ -> 3;\n" +
                 "    };\n" +
                 "  }\n",
                 "  enum E { A, B }\n" +
                 "  int foo(E e) {\n" +
                 "    return switch (e) {\n" +
                 "      case A -> 1;\n" +
                 "      case B -> 2;\n" +
                 "    };\n" +
                 "  }\n"
    );
  }

  public void testDoNotFixWithMissingValues() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.default.quickfix"),
                               "class X {\n" +
                               "  enum E { A, B, C }\n" +
                               "  int foo(E e) {\n" +
                               "    return switch (e) {\n" +
                               "      case A -> 1;\n" +
                               "      case B -> 2;\n" +
                               "      default/**/ -> 3;\n" +
                               "    };\n" +
                               "  }\n" +
                               "}\n");
  }

  public void testDoNotFixOnObject() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.default.quickfix"),
                               "class X {\n" +
                               "  int foo(Character e) {\n" +
                               "    return switch (e) {\n" +
                               "      case 'A' -> 1;\n" +
                               "      case 'B' -> 2;\n" +
                               "      default/**/ -> 3;\n" +
                               "    };\n" +
                               "  }\n" +
                               "}\n");
  }

  public void testDoNotFixOnPrimitive() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.default.quickfix"),
                               "class X {\n" +
                               "  int foo(char e) {\n" +
                               "    return switch (e) {\n" +
                               "      case 'A' -> 1;\n" +
                               "      case 'B' -> 2;\n" +
                               "      default/**/ -> 3;\n" +
                               "    };\n" +
                               "  }\n" +
                               "}\n");
  }
}
