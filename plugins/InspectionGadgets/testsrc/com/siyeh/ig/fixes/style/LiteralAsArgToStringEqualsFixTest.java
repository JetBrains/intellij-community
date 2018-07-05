// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.style;

import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.LiteralAsArgToStringEqualsInspection;

public class LiteralAsArgToStringEqualsFixTest extends IGQuickFixesTestCase {

  public void testSimple() {
    doTest("Flip '.equals()'",
      "class X {\n" +
      "  void test(String s) {\n" +
      "    System.out.println(s.equals(\"<caret>foo\"));\n" +
      "  }\n" +
      "}",

      "class X {\n" +
      "  void test(String s) {\n" +
      "    System.out.println(\"foo\".equals(s));\n" +
      "  }\n" +
      "}"
    );
  }

  public void testParentheses() {
    doTest("Flip '.equals()'",
      "class X {\n" +
      "  void test(String s) {\n" +
      "    System.out.println(s.equals((\"<caret>foo\")));\n" +
      "  }\n" +
      "}",

      "class X {\n" +
      "  void test(String s) {\n" +
      "    System.out.println(\"foo\".equals(s));\n" +
      "  }\n" +
      "}"
    );
  }

  @Override
  protected BaseInspection getInspection() {
    return new LiteralAsArgToStringEqualsInspection();
  }
}
