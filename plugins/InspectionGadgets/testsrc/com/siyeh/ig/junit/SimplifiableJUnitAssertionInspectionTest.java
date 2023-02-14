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
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import com.siyeh.ig.testFrameworks.SimplifiableAssertionInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class SimplifiableJUnitAssertionInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/junit/simplifiable_junit_assertion";
  }

  public void testSimplifiableJUnitAssertion() {
    doTest();
  }

  public void testSimplifiableJUnit40Assertion() {
    doTest();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new SimplifiableAssertionInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package junit.framework;" +
      " /** @noinspection ALL*/ public abstract class TestCase extends Assert {" +
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

      """
package org.junit.jupiter.api;
import java.util.function.Supplier;
public final class Assertions {
    public static void assertNotEquals(Object expected, Object actual) {}
    public static void assertFalse(boolean expected) {}
}"""
    };
  }
}