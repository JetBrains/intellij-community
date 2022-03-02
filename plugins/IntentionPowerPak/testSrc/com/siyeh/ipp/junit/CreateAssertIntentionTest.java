// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.junit;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see CreateAssertIntention
 * @author Bas Leijdekkers
 */
public class CreateAssertIntentionTest extends IPPTestCase {

  public void testAnonymousClassJUnit3() { doTest(); }
  public void testAnonymousClassJUnit4() { doTest(); }
  public void testStaticImportJUnit4() { doTest(); }
  public void testAssertFalse() { doTest(); }
  public void testConflictingMethod() { doTest(); }
  public void testNonConflictingMethod() { doTest(); }
  public void testJUnit5Test() { doTest(); }

  @Override
  protected String getRelativePath() {
    return "junit/create_assert";
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("create.assert.intention.name");
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package org.junit;" +
                       "public class Assert {" +
                       "  public static void assertTrue(java.lang.String message, boolean condition) {}" +
                       "  public static void assertNotNull(boolean condition) {}" +
                       "}");
    myFixture.addClass("package org.junit;" +
                       "@Retention(RetentionPolicy.RUNTIME)" +
                       "@Target({ElementType.METHOD})" +
                       "public @interface Test {}");
    myFixture.addClass("package junit.framework;" +
                       "public abstract class TestCase {" +
                       "  static public void assertTrue(boolean condition) {}" +
                       "  static public void assertFalse(boolean condition) {}" +
                       "}");

    myFixture.addClass("package org.junit.jupiter.api;" +
                       "import org.junit.platform.commons.annotation.Testable;" +
                       "@Testable\n" +
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
