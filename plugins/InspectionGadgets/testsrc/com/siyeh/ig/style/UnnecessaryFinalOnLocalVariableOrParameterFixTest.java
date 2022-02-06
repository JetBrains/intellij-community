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
package com.siyeh.ig.style;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;

/**
 * @author Fabrice TIERCELIN
 */
public class UnnecessaryFinalOnLocalVariableOrParameterFixTest extends IGQuickFixesTestCase {
  @Override
  protected void tuneFixture(final JavaModuleFixtureBuilder builder) throws Exception {
    builder.setLanguageLevel(LanguageLevel.JDK_1_8);
  }

  @Override
  protected BaseInspection getInspection() {
    return new UnnecessaryFinalOnLocalVariableOrParameterInspection();
  }

  public void testRemoveFinalOnParameter() {
    doMemberTest(InspectionGadgetsBundle.message("remove.modifier.quickfix", "final"),
                 "  public int foo(final/**/ int number) {\n" +
                 "    return number + 10;\n" +
                 "  }\n",
                 "  public int foo(int number) {\n" +
                 "    return number + 10;\n" +
                 "  }\n"
    );
  }

  public void testRemoveFinalOnLocalVariable() {
    doMemberTest(InspectionGadgetsBundle.message("remove.modifier.quickfix", "final"),
                 "  public int foo(int number) {\n" +
                 "    final/**/ int localNumber = number * 2;\n" +
                 "    return localNumber + 10;\n" +
                 "  }\n",
                 "  public int foo(int number) {\n" +
                 "    int localNumber = number * 2;\n" +
                 "    return localNumber + 10;\n" +
                 "  }\n"
    );
  }

  public void testRemoveFinalOnParameterUsedInLambda() {
    doMemberTest(InspectionGadgetsBundle.message("remove.modifier.quickfix", "final"),
                 "  public java.util.function.Function foo(final/**/ int number) {\n" +
                 "    return (int i) -> number + i;\n" +
                 "  }\n",
                 "  public java.util.function.Function foo(int number) {\n" +
                 "    return (int i) -> number + i;\n" +
                 "  }\n"
    );
  }

  public void testRemoveFinalOnLocalVariableUsedInLambda() {
    doMemberTest(InspectionGadgetsBundle.message("remove.modifier.quickfix", "final"),
                 "  public java.util.function.Function foo(int number) {\n" +
                 "    final/**/ int localNumber = number * 2;\n" +
                 "    return (int i) -> localNumber + i;\n" +
                 "  }\n",
                 "  public java.util.function.Function foo(int number) {\n" +
                 "    int localNumber = number * 2;\n" +
                 "    return (int i) -> localNumber + i;\n" +
                 "  }\n"
    );
  }
}
