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

import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.junit.JUnit5ConverterInspection;

public class Junit5ConverterFixTest extends IGQuickFixesTestCase {

  public void testSimple() {
    doTest();
  }
  public void testFullConversion() {
    doTest();
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder builder) throws Exception {
    super.tuneFixture(builder);
    builder.addJdk(IdeaTestUtil.getMockJdk18Path().getPath());
  }

  public void testExpectedOnTestAnnotation() {
    assertQuickfixNotAvailable();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new JUnit5ConverterInspection());
    myRelativePath = "junit/junit5_converter";
    myDefaultHint = InspectionGadgetsBundle.message("junit5.converter.fix.name");

    myFixture.addClass("package org.junit;" +
                       "public class Assert {" +
                       "    public static void assertArrayEquals(Object[] expecteds, Object[] actuals) {}" +
                       "    public static void assertArrayEquals(String message, Object[] expecteds, Object[] actuals){}" +
                       "    public static void assertTrue(String message, boolean condition) {}" +
                       "    public static void assertTrue(boolean condition) {}" +
                       "    public static void assertEquals(String message, Object expected, Object actual) {}" +
                       "    public static void assertEquals(Object expected, Object actual) {}" +
                       "    public static void fail(String message) {}" +
                       "    public static <T> void assertThat(T actual, Matcher<? super T> matcher) {}" +
                       "}");

    myFixture.addClass("package org.junit;" +
                       "public @interface Test {}");

    myFixture.addClass("package org.junit.jupiter.api;" +
                       "public @interface Test {}");

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
