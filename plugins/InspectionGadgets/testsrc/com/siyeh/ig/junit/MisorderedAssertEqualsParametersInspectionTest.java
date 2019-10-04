// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class MisorderedAssertEqualsParametersInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() { doTest(); }
  public void testThingTest() { doTest(); }

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
      "public @interface Test {}"
    };
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new MisorderedAssertEqualsParametersInspection();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
