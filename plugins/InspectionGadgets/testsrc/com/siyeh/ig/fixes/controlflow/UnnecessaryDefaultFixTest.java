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
import com.siyeh.ig.controlflow.UnnecessaryDefaultInspection;
import com.siyeh.ig.controlflow.UnnecessaryReturnInspection;

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
