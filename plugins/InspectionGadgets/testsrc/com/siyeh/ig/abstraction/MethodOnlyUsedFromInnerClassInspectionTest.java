// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightInspectionTestCase;

@SuppressWarnings("MethodMayBeStatic")
public class MethodOnlyUsedFromInnerClassInspectionTest extends LightInspectionTestCase {

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
           "  private int /*Method 'foo()'is only used from an anonymous class extending 'Inner'*/foo/**/() {return 5;}\n" +
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

  @Override
  protected LocalInspectionTool getInspection() {
    return new MethodOnlyUsedFromInnerClassInspection();
  }
}
