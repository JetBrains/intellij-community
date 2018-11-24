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
package com.siyeh.ipp.conditional;

import com.siyeh.ipp.IPPTestCase;

/**
 * @author Bas Leijdekkers
 */
public class ReplaceConditionalWithBooleanExpressionIntentionTest extends IPPTestCase {

  public void testPrecedence() {
    doTest("class X {" +
           "    void test(boolean foo, boolean bar) {" +
           "        boolean c = false;" +
           "        boolean b = /*_Replace '?:' with boolean expression*/foo ? true : c || bar;" +
           "    }" +
           "}",
           "class X {" +
           "    void test(boolean foo, boolean bar) {" +
           "        boolean c = false;" +
           "        boolean b = foo && true || !foo && (c || bar);" +
           "    }" +
           "}");
  }

  public void testIncomplete() {
    doTest("class X {" +
           "    boolean test(boolean a, boolean b, boolean c) {" +
           "        return a /*_Replace '?:' with boolean expression*/? b ? c;" +
           "    }" +
           "}",
           "class X {" +
           "    boolean test(boolean a, boolean b, boolean c) {" +
           "        return a && (b ? c) || !a &&;" +
           "    }" +
           "}");
  }
}
