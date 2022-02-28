// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class UnconstructableTestCaseInspectionTest extends LightJavaInspectionTestCase {

  public void testUnconstructableJUnit4TestCase() { doTest(); }
  public void testUnconstructableJUnit4TestCase2() { doTest(); }
  public void testUnconstructableJUnit4TestCase3() { doTest(); }
  public void testUnconstructableTestCase1() { doTest(); }
  public void testUnconstructableTestCase2() { doTest(); }
  public void testUnconstructableTestCase3() { doTest(); }
  public void testUnconstructableTestCase4() { doTest(); }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnconstructableTestCaseInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package org.junit; " +
      "public @interface Test {\n" +
      "    java.lang.Class<? extends java.lang.Throwable> expected() default org.junit.Test.None.class;" +
      "}",
      "package junit.framework;" +
      "public abstract class TestCase {}"};
  }
}