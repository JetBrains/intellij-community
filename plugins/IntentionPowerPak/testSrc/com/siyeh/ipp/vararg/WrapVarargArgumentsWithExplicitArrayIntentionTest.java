// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.vararg;

import com.siyeh.ipp.IPPTestCase;

/**
 * @author Bas Leijdekkers
 */
public class WrapVarargArgumentsWithExplicitArrayIntentionTest extends IPPTestCase {

  @SuppressWarnings("ConfusingArgumentToVarargsMethod")
  public void testNullArgument() {
    doTestIntentionNotAvailable("class X {" +
                                "  void a(String... ss) {}" +
                                "  void b() {" +
                                "    a(/*_Wrap vararg arguments with explicit array creation*/null);" +
                                "  }" +
                                "}");
  }

  @SuppressWarnings("RedundantArrayCreation")
  public void testEnumConstants() {
    doTest("enum X {" +
           "  A(/*_Wrap vararg arguments with explicit array creation*/1), B(1,2), C(1,2,3);" +
           "  X(int... is) {}" +
           "}",

           "enum X {" +
           "  A(new int[]{1}), B(1,2), C(1,2,3);" +
           "  X(int... is) {}" +
           "}");
  }

  @SuppressWarnings("RedundantArrayCreation")
  public void testConstructorCall() {
    doTest("class A {" +
           "  A(int... is) {}" +
           "  void a() {" +
           "    new A(1,2,3/*_Wrap vararg arguments with explicit array creation*/);" +
           "  }" +
           "}",

           "class A {" +
           "  A(int... is) {}" +
           "  void a() {" +
           "    new A(new int[]{1, 2, 3});" +
           "  }" +
           "}");
  }

  public void testCapturedWildcard1() {
    doTest(
      "class X {\n" +
      "    interface I<T> {\n" +
      "        String m(T... t);\n" +
      "    }\n" +
      "    public static void run() {\n" +
      "        I<? super Integer> i = null;\n" +
      "        i.m(/*_Wrap vararg arguments with explicit array creation*/1, 2, 3);\n" +
      "    }\n" +
      "}",

      "class X {\n" +
      "    interface I<T> {\n" +
      "        String m(T... t);\n" +
      "    }\n" +
      "    public static void run() {\n" +
      "        I<? super Integer> i = null;\n" +
      "        i.m(new Integer[]{1, 2, 3});\n" +
      "    }\n" +
      "}"
    );
  }

  public void testCapturedWildcard2() {
    doTestIntentionNotAvailable(
      "class Y {\n" +
      "    interface I<T> {\n" +
      "        String m(T... t);\n" +
      "    }\n" +
      "    public static void run() {\n" +
      "        I<?> i = null;\n" +
      "        i.m(/*_Wrap vararg arguments with explicit array creation*/1, 2, 3);\n" +
      "    }\n" +
      "}");
  }
  
  public void testNonReifiable() {
    doTestIntentionNotAvailable(
      "import java.util.*;\n" +
      "class Y {\n" +
      "    <T> void m(Set<T>... t){}\n" +
      "    public static void run(Set<?> s) {\n" +
      "        m(/*_Wrap vararg arguments with explicit array creation*/s);\n" +
      "    }\n" +
      "}");
  }

  @SuppressWarnings("ALL")
  public void testGenericArray() {
    doTest(
      "import java.util.Set;" +
      "class Y<T> {\n" +
      "  void m(Set<String>... t){}\n" +
      "  public void run(Set<String> s) {\n" +
      "    m(/*_Wrap vararg arguments with explicit array creation*/s);\n" +
      "  }\n" +
      "}",

      "import java.util.Set;class Y<T> {\n" +
      "  void m(Set<String>... t){}\n" +
      "  public void run(Set<String> s) {\n" +
      "    m(new Set[]{s});\n" +
      "  }\n" +
      "}"
    );
  }

  public void testClassWildcard() {
    doTestIntentionNotAvailable(
      "class Y {" +
      "  void test() throws NoSuchMethodException {\n" +
      "    String.class.getMethod(\"indexOf\", new Class[]/*_Wrap vararg arguments with explicit array creation*/ {int.class});\n" +
      "  }" +
      "}"
    );
  }

  public void testEmptyArray() {
    doTest(
      "class X {" +
      "  void x() {" +
      "    java.util.Arrays.asList(/*_Wrap vararg arguments with explicit array creation*/);" +
      "  }" +
      "}",

      "class X {" +
      "  void x() {" +
      "    java.util.Arrays.asList(new Object[]{});" +
      "  }" +
      "}"
    );
  }
}
