// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import com.siyeh.ig.testFrameworks.MisorderedAssertEqualsArgumentsInspection;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class MisorderedAssertEqualsArgumentsInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() { doTest(); }
  public void testThingTest() { doTest(); }
  public void testTestNGTest() { doTest(); }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package org.junit;" +
      "public class Assert {" +
      "  static public void assertEquals(long expected, long actual) {}" +
      "  static public void assertEquals(String message, long expected, long actual) {}" +
      "  static public void assertEquals(String message, Object expected, Object actual) {}" +
      "  static public void assertEquals(Object expected, Object actual){}" +
      "  static public void assertSame(Object expected, Object actual) {}" +
      "}",

      "package junit.framework;" +
      "public class Assert {" +
      "  static public void failNotEquals(String message, Object expected, Object actual) {}" +
      "}",

      "package org.junit;" +
      "import java.lang.annotation.ElementType;" +
      "import java.lang.annotation.Retention;" +
      "import java.lang.annotation.RetentionPolicy;" +
      "import java.lang.annotation.Target;" +
      "@Retention(RetentionPolicy.RUNTIME)" +
      "@Target({ElementType.METHOD})" +
      "public @interface Test {}",

      "package org.testng.annotations;" +
      "@Retention(RetentionPolicy.RUNTIME)" +
      "@Target({ElementType.METHOD, ElementType.TYPE, ElementType.CONSTRUCTOR})" +
      "public @interface Test {}",

      "package org.testng;" +
      "public class Assert {" +
      "      public static void assertEquals(Object actual, Object expected) {}" +
      "      public static void assertEquals(Object actual, Object expected, String message) {}" +
      "}"
    };
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new MisorderedAssertEqualsArgumentsInspection();
  }

  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/junit/misordered_assert_equals_parameters";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
