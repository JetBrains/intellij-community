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

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.junit.JUnit5AssertionsConverterInspection;
import com.siyeh.ig.junit.JUnitCommonClassNames;

public class Junit5AssertionsConverterFixTest extends IGQuickFixesTestCase {

  public void testAssertArrayEquals() { doTestAssertions();}
  public void testAssertArrayEqualsMessage() { doTestAssertions();}
  public void testAssertEquals() { doTestAssertions();}
  public void testAssertTrue() { doTestAssertions();}
  public void testAssertNotEqualsWithDelta() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("junit5.assertions.converter.quickfix", JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS));
  }

  public void testAssertThat() {
    doTest(InspectionGadgetsBundle.message("junit5.assertions.converter.quickfix", JUnitCommonClassNames.ORG_HAMCREST_MATCHER_ASSERT));
  }
  public void testAssumeTrue() {
    doTest(InspectionGadgetsBundle.message("junit5.assertions.converter.quickfix", JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSUMPTIONS));
  }

  private void doTestAssertions() {
    doTest(InspectionGadgetsBundle.message("junit5.assertions.converter.quickfix", JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS));
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new JUnit5AssertionsConverterInspection());
    myRelativePath = "junit/junit5_assertions_converter";

    myFixture.addClass("package org.hamcrest;\n" +
                       "public interface Matcher<T>{}");
    myFixture.addClass("package org.hamcrest;\n" +
                       "public class MatcherAssert{}");

    myFixture.addClass("package org.junit;" +
                       "public class Assert {" +
                       "    public static void assertArrayEquals(Object[] expecteds, Object[] actuals) {}" +
                       "    public static void assertArrayEquals(String message, Object[] expecteds, Object[] actuals){}" +
                       "    public static void assertTrue(String message, boolean condition) {}" +
                       "    public static void assertTrue(boolean condition) {}" +
                       "    public static void assertEquals(String message, Object expected, Object actual) {}" +
                       "    public static void assertEquals(Object expected, Object actual) {}" +
                       "    public static void fail(String message) {}" +
                       "    public static <T> void assertThat(String reason, T actual, org.hamcrest.Matcher<? super T> matcher) {}" +
                       "    public static void assertNotEquals(double unexpected, double actual, double delta){}" +
                       "}");

    myFixture.addClass("package org.junit.jupiter.api;\n" +
                       "public class Assumptions {\n" +
                       "    public static void assumeTrue(boolean b) {}\n" +
                       "    public static void assumeTrue(boolean b, String message) {}\n" +
                       "}");

    myFixture.addClass("package org.junit;\n" +
                       "public class Assume {\n" +
                       "    public static void assumeTrue(boolean b) {}\n" +
                       "    public static void assumeTrue(String message, boolean b) {}\n" +
                       "}");

    myFixture.addClass("package org.junit.jupiter.api;" +
                       "public @interface Test {}");
    myFixture.addClass("package org.junit.platform.commons.annotation;" +
                       "public @interface Testable {}");

    myFixture.addClass("package org.junit.jupiter.api;\n" +
                       "public final class Assertions {\n" +
                       "    public static void assertArrayEquals(Object[] expected, Object[] actual) {}\n" +
                       "    public static void assertArrayEquals(Object[] expected, Object[] actual, String message) {}\n" +
                       "    public static void assertEquals(Object expected, Object actual) {}\n" +
                       "    public static void assertTrue(boolean expected) {}\n" +
                       "    public static void assertEquals(Object expected, Object actual, String message) {}\n" +
                       "    public static void assertTrue(Object expected, String message) {}\n" +
                       "    public static void fail(String message) {}" +
                       "}");
  }
}
