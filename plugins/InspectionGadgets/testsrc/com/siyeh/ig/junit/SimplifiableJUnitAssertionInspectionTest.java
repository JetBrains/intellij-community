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
package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class SimplifiableJUnitAssertionInspectionTest extends LightInspectionTestCase {

  public void testSimplifiableJUnitAssertion() {
    doTest();
  }

  public void testSimplifiableJUnit40Assertion() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new SimplifiableJUnitAssertionInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package junit.framework;" +
      "public abstract class TestCase extends Assert {" +
      "    protected void setUp() throws Exception {}" +
      "    protected void tearDown() throws Exception {}" +
      "    public static void assertTrue(boolean condition) {" +
      "        Assert.assertTrue(condition);" +
      "    }" +
      "}",

      "package junit.framework;" +
      "public class Assert {" +
      "    public static void assertTrue(String message, boolean condition) {}" +
      "    public static void assertTrue(boolean condition) {}" +
      "    public static void assertEquals(String message, Object expected, Object actual) {}" +
      "    public static void assertEquals(Object expected, Object actual) {}" +
      "    public static void assertFalse(String message, boolean condition) {}" +
      "    public static void assertFalse(boolean condition) {}" +
      "}",

      "package org.junit;" +
      "public class Assert {" +
      "    public static public void assertTrue(boolean condition) {}" +
      "    public static public void assertFalse(boolean condition) {}" +
      "    public static void assertEquals(boolean expected, boolean actual) {}" +
      "    public static void assertFalse(String message, boolean condition) {}" +
      "    public static void assertNotEquals(long a, long b) {}" +
      "}",

      "package org.junit;" +
      "import java.lang.annotation.ElementType;" +
      "import java.lang.annotation.Retention;" +
      "import java.lang.annotation.RetentionPolicy;" +
      "import java.lang.annotation.Target;" +
      "@Retention(RetentionPolicy.RUNTIME)" +
      "@Target({ElementType.METHOD})" +
      "public @interface Test {}",

      "package org.junit.jupiter.api;\n" +
      "import java.util.function.Supplier;\n" +
      "public final class Assertions {\n" +
      "    public static void assertNotEquals(Object expected, Object actual) {}\n" +
      "    public static void assertFalse(boolean expected) {}\n" +
      "}"
    };
  }
}