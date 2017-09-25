/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.siyeh.ig.assignment.AssignmentToLambdaParameterInspection;
import com.siyeh.ig.assignment.AssignmentToMethodParameterInspection;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings("ALL")
public class ExtractParameterAsLocalVariableFixTest extends IGQuickFixesTestCase {

  @Override
  protected BaseInspection[] getInspections() {
    final AssignmentToForLoopParameterInspection inspection2 = new AssignmentToForLoopParameterInspection();
    inspection2.m_checkForeachParameters = true;
    return new BaseInspection[] {
      new AssignmentToMethodParameterInspection(),
      inspection2,
      new AssignmentToCatchBlockParameterInspection(),
      new AssignmentToLambdaParameterInspection()
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
      "class X {\n" +
      "    void m(String s) {\n" +
      "        /**/s = \"hello\";\n" +
      "        System.out.println(s);\n" +
      "    }\n" +
      "}",
      "class X {\n" +
      "    void m(String s) {\n" +
      "        String hello = \"hello\";\n" +
      "        System.out.println(hello);\n" +
      "    }\n" +
      "}"
    );
  }

  public void testParenthesizedExpression() {
    doTest(InspectionGadgetsBundle.message("extract.parameter.as.local.variable.quickfix"),
           "class X {\n" +
           "    void m(int i) {\n" +
           "        (/**/i)++;\n" +
           "        System.out.println(i);\n" +
           "    }\n" +
           "}",
           "class X {\n" +
           "    void m(int i) {\n" +
           "        int i1 = i;\n" +
           "        (i1)++;\n" +
           "        System.out.println(i1);\n" +
           "    }\n" +
           "}");
  }

  public void testSimpleForeach() {
    doTest(
      InspectionGadgetsBundle.message("extract.parameter.as.local.variable.quickfix"),
      "class X {\n" +
      "    void m() {\n" +
      "        for (String s : new String[]{\"one\", \"two\", \"three\"})\n" +
      "            s/**/ = \"four\";\n" +
      "    }\n" +
      "}",

      "class X {\n" +
      "    void m() {\n" +
      "        for (String s : new String[]{\"one\", \"two\", \"three\"}) {\n" +
      "            String s1 = s;\n" +
      "            s1 = \"four\";\n" +
      "        }\n" +
      "    }\n" +
      "}"
    );
  }

  public void testSimpleCatchBlock() {
    doTest(
      InspectionGadgetsBundle.message("extract.parameter.as.local.variable.quickfix"),
      "import java.io.*;\n" +
      "class X {\n" +
      "    void m() {\n" +
      "        try (InputStream in = null) {\n" +
      "        } catch (IOException e) {\n" +
      "            e/**/ = null;\n" +
      "            System.out.println(e);\n" +
      "        }\n" +
      "    }\n" +
      "}",

      "import java.io.*;\n" +
      "class X {\n" +
      "    void m() {\n" +
      "        try (InputStream in = null) {\n" +
      "        } catch (IOException e) {\n" +
      "            IOException o = null;\n" +
      "            System.out.println(o);\n" +
      "        }\n" +
      "    }\n" +
      "}"
    );
  }

  public void testJavaDoccedParameter() {
    doTest(InspectionGadgetsBundle.message("extract.parameter.as.local.variable.quickfix"),
           "class Foo {\n" +
           "    /**\n" +
           "     * @param customEnumElement name of custom element of the enumeration (attribute or method) whose values should be used to match equivalent {@code String}s.\n" +
           "     */\n" +
           "    void foo(String customEnumElement) {\n" +
           "        if (customEnumElement != null) {\n" +
           "            /**/customEnumElement = customEnumElement.trim();\n" +
           "        }\n" +
           "    }\n" +
           "}",

           "class Foo {\n" +
           "    /**\n" +
           "     * @param customEnumElement name of custom element of the enumeration (attribute or method) whose values should be used to match equivalent {@code String}s.\n" +
           "     */\n" +
           "    void foo(String customEnumElement) {\n" +
           "        String enumElement = customEnumElement;\n" +
           "        if (enumElement != null) {\n" +
           "            enumElement = enumElement.trim();\n" +
           "        }\n" +
           "    }\n" +
           "}");
  }

  public void testSuperCall() {
    doTest(InspectionGadgetsBundle.message("extract.parameter.as.local.variable.quickfix"),
           "class Foo {\n" +
           "    Foo(Object o ) {\n" +
           "        super();\n" +
           "        if (o != null) {\n" +
           "            /**/o = o.toString();\n" +
           "        }\n" +
           "    }\n" +
           "}",

           "class Foo {\n" +
           "    Foo(Object o ) {\n" +
           "        super();\n" +
           "        Object o1 = o;\n" +
           "        if (o1 != null) {\n" +
           "            o1 = o1.toString();\n" +
           "        }\n" +
           "    }\n" +
           "}");
  }

  public void testCorrectVariableScope() {
    doTest(InspectionGadgetsBundle.message("extract.parameter.as.local.variable.quickfix"),
           "class Foo {\n" +
           "    public final void setValue(String label, Object value) {\n" +
           "        if (false) {\n" +
           "            /**/value = null;\n" +
           "        }\n" +
           "        System.out.println(value);\n" +
           "    }\n" +
           "    \n" +
           "}",

           "class Foo {\n" +
           "    public final void setValue(String label, Object value) {\n" +
           "        Object o = value;\n" +
           "        if (false) {\n" +
           "            o = null;\n" +
           "        }\n" +
           "        System.out.println(o);\n" +
           "    }\n" +
           "    \n" +
           "}");
  }
}
