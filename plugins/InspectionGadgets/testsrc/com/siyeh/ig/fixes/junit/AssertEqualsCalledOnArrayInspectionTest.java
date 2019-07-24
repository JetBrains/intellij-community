/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.fixes.junit;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.junit.AssertEqualsCalledOnArrayInspection;

public class AssertEqualsCalledOnArrayInspectionTest extends IGQuickFixesTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();


    myFixture.addClass("package org.junit.jupiter.api;\n" +
                       "import java.util.function.Supplier;\n" +
                       "public final class Assertions {\n" +
                       "    public static void assertArrayEquals(Object[] expected, Object[] actual) {}\n" +
                       "    public static void assertArrayEquals(Object[] expected, Object[] actual, String message) {}\n" +
                       "    public static void assertEquals(Object expected, Object actual) {}\n" +
                       "    public static void assertEquals(Object expected, Object actual, String message) {}\n" +
                       "}");

    myFixture.enableInspections(new AssertEqualsCalledOnArrayInspection());
  }

  public void testAssertEqualsForJunit5() {
    doFixTest();
  }

  private void doFixTest() {
    doTest(getTestName(true), CommonQuickFixBundle.message("fix.replace.with.x", "assertArrayEquals"));
  }

  @Override
  protected String getRelativePath() {
    return "junit/assertEqualsOnArray";
  }
}
