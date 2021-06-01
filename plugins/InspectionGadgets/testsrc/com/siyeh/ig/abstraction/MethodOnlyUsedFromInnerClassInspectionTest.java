// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("MethodMayBeStatic")
public class MethodOnlyUsedFromInnerClassInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() {
    doTest("class X {\n" +
           "  private void /*Method 'foo()'is only used from inner class 'Inner'*/foo/**/() {}\n" +
           "  \n" +
           "  class Inner {\n" +
           "    void test() {foo();}\n" +
           "  }\n" +
           "}");
  }

  public void testAnonymous() {
    doTest("class X {\n" +
           "  private int /*Method 'foo()'is only used from an anonymous class derived from 'Inner'*/foo/**/() {return 5;}\n" +
           "  \n" +
           "  void test() {\n" +
           "    new Inner(1) {\n" +
           "      int x = foo();\n" +
           "    };\n" +
           "  }\n" +
           "  \n" +
           "  static class Inner { Inner(int x) {}}\n" +
           "}");
  }

  public void testAnonymousCtor() {
    doTest("class X {\n" +
           "  private int foo() {return 5;}\n" +
           "  \n" +
           "  void test() {\n" +
           "    new Inner(foo()) {\n" +
           "      int x = 5;\n" +
           "    };\n" +
           "  }\n" +
           "  \n" +
           "  static class Inner { Inner(int x) {}}\n" +
           "}");
  }

  public void testLocalClass() {
    doTest("class X {\n" +
           "  private void /*Method 'foo()'is only used from local class 'Local'*/foo/**/() {}\n" +
           "  \n" +
           "   void x() {\n" +
           "    class Local {\n" +
           "      void test() {foo();}\n" +
           "    }\n" +
           "  }\n" +
           "}");
  }

  public void testNoWarnLocalClass() {
    final MethodOnlyUsedFromInnerClassInspection inspection = new MethodOnlyUsedFromInnerClassInspection();
    inspection.ignoreMethodsAccessedFromAnonymousClass = true;
    myFixture.enableInspections(inspection);
    doTest("class X {\n" +
           "  private void foo() {}\n" +
           "  \n" +
           "   void x() {\n" +
           "    class Local {\n" +
           "      void test() {foo();}\n" +
           "    }\n" +
           "  }\n" +
           "}");
  }

  public void testIgnoreStaticMethodCalledFromNonStaticInnerClass() {
    doTest("class Outer {\n" +
           "    public static void main(String[] args) {\n" +
           "        class Local {\n" +
           "            void run(String arg) {\n" +
           "                if (!isEmpty(arg)) {\n" +
           "                    System.out.println(\"Argument is supplied\");\n" +
           "                }\n" +
           "            }\n" +
           "        }\n" +
           "        new Local().run(args[0]);\n" +
           "    }\n" +
           "    private static boolean isEmpty(String s) {\n" +
           "        return s != null && s.trim().isEmpty();\n" +
           "    }\n" +
           "}");
  }

  @Override
  protected LocalInspectionTool getInspection() {
    return new MethodOnlyUsedFromInnerClassInspection();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_15;
  }
}
