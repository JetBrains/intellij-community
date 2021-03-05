// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.bool;

import com.siyeh.ipp.IPPTestCase;

/**
 * @author Bas Leijdekkers
 */
public class FlipComparisonIntentionTest extends IPPTestCase {

  public void testSimple() {
    doTest("class X {" +
           "  boolean b = 1//some comment\n" +
           " >/*_Flip '>' to '<'*///another comment\n" +
           " 2;" +
           "}",

           "class X {  //some comment\n" +
           "    //another comment\n" +
           "    boolean b = 2 < 1;}");
  }

  public void testAssignment() {
    doTest("class X {\n" +
           "  void foo(int x) {\n" +
           "    boolean b;\n" +
           "    b = 1/*_Flip '>' to '<'*/ > x;\n" +
           "  }\n" +
           "}",

           "class X {\n" +
           "  void foo(int x) {\n" +
           "    boolean b;\n" +
           "    b = x < 1;\n" +
           "  }\n" +
           "}");
  }

  public void testGreater() {
    doTest("class X {{" +
           "  if(a+b>/*_Flip '>' to '<'*/c);" +
           "}}",

           "class X {{" +
           "  if(c < a + b);" +
           "}}");
  }

  public void testBrokenCode() {
    doTestIntentionNotAvailable("import java.util.*;" +
                                "class X {" +
                                "  String x(Set<String> set) {" +
                                "  }" +
                                "  {x(HashSet</*_Flip '>' to '<'*/>());}" +
                                "}");
  }

  public void testBrokenCode2() {
    doTestIntentionNotAvailable("class Builder {\n" +
                                "    Builder b = !(new//simple end comment\n" +
                                "         </*_Flip '>=' to '<='*/>   Builder().method( >= caret) >1).method(2);\n" +
                                "}");
  }

  public void testBrokenCode3() {
    doTest("@Anno(\n" +
           "  param//test comment\n" +
           "  /*_Flip '<' to '>'*/<foo>",

           "@Anno(\n" +
           "        //test comment\n" +
           "        foo > param >)");
  }

  public void testBrokenCode4() {
    doTestIntentionNotAvailable("class A{\n" +
           "  {\n" +
           "    \"\"<foo>\"//simple/*_Flip '>' to '<'*/ end comment\n" +
           "  }\n" +
           "}");
  }

  public void testBrokenCode5() {
    doTestIntentionNotAvailable("class A{\n" +
           "  {\n" +
           "    /*_Flip '>' to '<'*/a > b > c" +
           "  }\n" +
           "}");
  }

  public void testBrokenCode6() {
    doTestIntentionNotAvailable("class A{\n" +
           "  {\n" +
           "    ((LookupElementBuilder)variants[0]).rendeFragment>/*_Flip '>' to '<'*/ fragments = presentation.getTailFragments();" +
           "  }\n" +
           "}");
  }

  public void testNoop() {
    doTestIntentionNotAvailable("class X {" +
                                "  void x(String x) {" +
                                "    if (x /*_Flip '=='*/== x) {}" +
                                "  }" +
                                "}");
  }
}