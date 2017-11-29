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
package com.siyeh.ig.naming;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class OverloadedVarargsMethodInspectionTest extends LightInspectionTestCase
{
  @Override
  protected InspectionProfileEntry getInspection() {
    return new OverloadedVarargsMethodInspection();
  }

  public void testOneWarning() {
    doTest("class Overload {" +
           "  public void overload() {}" +
           "  public void overload(int p1) {}" +
           "  public void overload(int p1, String p2) {}" +
           "  public void /*Overloaded varargs method 'overload()'*/overload/**/(int p1, String p2, String... p3) {}" +
           "}");
  }

  public void testWarnWhenSuperMethod() {
    doTest("class Super {" +
           "  public void method() {}" +
           "}" +
           "class Overload extends Super {" +
           "  public void /*Overloaded varargs method 'method()'*/method/**/(String... ss) {}" +
           "}");
  }

  public void testOverridingMethod() {
    doTest("interface Base {" +
           "  void test(String... ss);" +
           "}" +
           "class Impl implements Base {" +
           "  public void test(String... ss) {}" +
           "}");
  }

  public void testGenericMethods() {
    doTest("interface Foo<T> {" +
           "        void makeItSo(T command, int... values);" +
           "    }" +
           "    class Bar implements Foo<String> {" +
           "        public void makeItSo(final String command, final int... values) {" +
           "        }" +
           "    }");
  }
}
