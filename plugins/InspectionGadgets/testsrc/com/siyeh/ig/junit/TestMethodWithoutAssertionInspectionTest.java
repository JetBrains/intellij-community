/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class TestMethodWithoutAssertionInspectionTest extends LightInspectionTestCase {

  public void testTestMethodWithoutAssertion() {
    doTest();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package org.junit;" +
      "import java.lang.annotation.ElementType;" +
      "import java.lang.annotation.Retention;" +
      "import java.lang.annotation.RetentionPolicy;" +
      "import java.lang.annotation.Target;" +
      "@Retention(RetentionPolicy.RUNTIME)" +
      "@Target({ElementType.METHOD})" +
      "public @interface Test {" +
      "  Class<? extends java.lang.Throwable> expected() default org.junit.Test.None.class;" +
      "}",

      "package org.junit;" +
      "public class Assert {" +
      "  static public void assertTrue(boolean condition) {}" +
      "}",

      "package junit.framework;" +
      "public class Assert {" +
      "  static public void assertTrue(boolean condition) {}" +
      "  static public void fail() {}" +
      "}",

      "package junit.framework;" +
      "public abstract class TestCase extends Assert {}",

      "package mockit;" +
      "public abstract class Verifications {" +
      "  protected Verifications() {}" +
      "}",

      "package mockit;" +
      "@java.lang.annotation.Retention(value=RUNTIME)\n" +
      "@java.lang.annotation.Target(value={FIELD,PARAMETER})" +
      "public @interface Mocked {}"
    };
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final TestMethodWithoutAssertionInspection inspection = new TestMethodWithoutAssertionInspection();
    inspection.ignoreIfExceptionThrown = true;
    return inspection;
  }
}