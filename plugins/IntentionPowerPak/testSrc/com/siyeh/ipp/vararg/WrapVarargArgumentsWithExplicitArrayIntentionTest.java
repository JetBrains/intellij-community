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
      "" +
      "import java.util.*;\n" +
      "class Y {\n" +
      "    <T> void m(Set<T>... t){}\n" +
      "    public static void run(Set<?> s) {\n" +
      "        m(/*_Wrap vararg arguments with explicit array creation*/s);\n" +
      "    }\n" +
      "}");
  }
}
