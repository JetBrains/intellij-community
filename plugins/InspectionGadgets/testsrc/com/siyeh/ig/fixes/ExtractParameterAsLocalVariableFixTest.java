/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.fixes;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.assignment.AssignmentToCatchBlockParameterInspection;
import com.siyeh.ig.assignment.AssignmentToForLoopParameterInspection;
import com.siyeh.ig.assignment.AssignmentToMethodParameterInspection;

/**
 * @author Bas Leijdekkers
 */
public class ExtractParameterAsLocalVariableFixTest extends IGQuickFixesTestCase {


  @Override
  protected BaseInspection[] getInspections() {
    final AssignmentToForLoopParameterInspection inspection2 = new AssignmentToForLoopParameterInspection();
    inspection2.m_checkForeachParameters = true;
    return new BaseInspection[] {
      new AssignmentToMethodParameterInspection(),
      inspection2,
      new AssignmentToCatchBlockParameterInspection()
    };
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {"class A {}",
      "public interface Function<T, R> {\n" +
      "    R apply(T t);" +
      "}"};
  }

  public void testLambdaWithExpressionBody() {
    doTest(
      InspectionGadgetsBundle.message("extract.parameter.as.local.variable.quickfix"),
      "class X {" +
      "  Function<A, A> f = (a) -> a/**/ = null;" +
      "}",
      "class X {" +
      "  Function<A, A> f = (a) -> {\n" +
      "    A a1 = a;\n" +
      "    return a1 = null;\n" +
      "};}"
    );
  }

  public void testSimpleMethod() {
    doTest(
      InspectionGadgetsBundle.message("extract.parameter.as.local.variable.quickfix"),
      "class X {" +
      "  void m(String s) {" +
      "    /**/s = \"hello\";" +
      "    System.out.println(s);" +
      "  }" +
      "}",
      "class X {\n" +
      "    void m(String s) {\n" +
      "        String s1 = \"hello\";\n" +
      "        System.out.println(s1);\n" +
      "    }\n" +
      "}"
    );
  }

  public void testSimpleForeach() {
    doTest(
      InspectionGadgetsBundle.message("extract.parameter.as.local.variable.quickfix"),
      "class X {" +
      "  void m() {" +
      "    for (String s : new String[]{\"one\", \"two\", \"three\"})" +
      "      s/**/ = \"four\";" +
      "}}",
      "class X {" +
      "  void m() {\n" +
      "    for (String s : new String[]{\"one\", \"two\", \"three\"}){\n" +
      "        String s1 = \"four\";\n" +
      "    }\n" +
      "}}"
    );
  }

  public void testSimleCatchBlock() {
    doTest(
      InspectionGadgetsBundle.message("extract.parameter.as.local.variable.quickfix"),
      "class X {" +
      "  void m() {" +
      "    try (java.io.InputStream in = null) {" +
      "    } catch (java.io.IOException e) {" +
      "      e/**/ = null;" +
      "      System.out.println(e);" +
      "    }" +
      "  }" +
      "}",
      "class X {" +
      "  void m() {" +
      "    try (java.io.InputStream in = null) {" +
      "    } catch (java.io.IOException e) {\n" +
      "    java.io.IOException e1 = null;\n" +
      "    System.out.println(e1);\n" +
      "}\n" +
      "}}"
    );
  }
}
