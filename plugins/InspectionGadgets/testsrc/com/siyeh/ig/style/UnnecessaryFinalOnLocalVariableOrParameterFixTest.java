// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;

/**
 * @author Fabrice TIERCELIN
 */
public class UnnecessaryFinalOnLocalVariableOrParameterFixTest extends IGQuickFixesTestCase {
  @Override
  protected BaseInspection getInspection() {
    return new UnnecessaryFinalOnLocalVariableOrParameterInspection();
  }

  public void testRemoveFinalOnParameter() {
    doMemberTest(InspectionGadgetsBundle.message("remove.modifier.quickfix", "final"),
                 "public int foo(final/**/ int number) {\n" +
                 "  return number + 10;\n" +
                 "}\n",
                 "public int foo(int number) {\n" +
                 "  return number + 10;\n" +
                 "}\n"
    );
  }

  public void testRemoveFinalOnLocalVariable() {
    doMemberTest(InspectionGadgetsBundle.message("remove.modifier.quickfix", "final"),
                 "public int foo(int number) {\n" +
                 "  final/**/ int localNumber = number * 2;\n" +
                 "  return localNumber + 10;\n" +
                 "}\n",
                 "public int foo(int number) {\n" +
                 "  int localNumber = number * 2;\n" +
                 "  return localNumber + 10;\n" +
                 "}\n"
    );
  }

  public void testRemoveFinalOnParameterUsedInLambda() {
    doMemberTest(InspectionGadgetsBundle.message("remove.modifier.quickfix", "final"),
                 "public java.util.function.Function foo(final/**/ int number) {\n" +
                 "  return (int i) -> number + i;\n" +
                 "}\n",
                 "public java.util.function.Function foo(int number) {\n" +
                 "  return (int i) -> number + i;\n" +
                 "}\n"
    );
  }

  public void testRemoveFinalOnLocalVariableUsedInLambda() {
    doMemberTest(InspectionGadgetsBundle.message("remove.modifier.quickfix", "final"),
                 "public java.util.function.Function foo(int number) {\n" +
                 "  final/**/ int localNumber = number * 2;\n" +
                 "  return (int i) -> localNumber + i;\n" +
                 "}\n",
                 "public java.util.function.Function foo(int number) {\n" +
                 "  int localNumber = number * 2;\n" +
                 "  return (int i) -> localNumber + i;\n" +
                 "}\n"
    );
  }
}
