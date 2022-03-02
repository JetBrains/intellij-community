// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class TestCaseWithConstructorInspectionTest extends LightJavaInspectionTestCase {

  public void testJUnit4TestCaseWithConstructor() { doTest(); }
  public void testParameterizedTest() { doTest(); }
  public void testTestCaseWithConstructorInspection1() { doTest(); }
  public void testTestCaseWithConstructorInspection2() { doTest(); }
  public void testTestCaseWithConstructorInspection3() { doTest(); }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new TestCaseWithConstructorInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package org.junit; " +
      "public @interface Test {\n" +
      "    java.lang.Class<? extends java.lang.Throwable> expected() default org.junit.Test.None.class;" +
      "}",

      "package org.junit.runner;" +
      "public abstract class Runner {}",

      "package org.junit.runner;\n" +
      "import java.lang.annotation.ElementType;\n" +
      "import java.lang.annotation.Retention;\n" +
      "import java.lang.annotation.RetentionPolicy;\n" +
      "import java.lang.annotation.Target;\n" +
      "@Retention(RetentionPolicy.RUNTIME)\n" +
      "@Target(ElementType.TYPE)\n" +
      "public @interface RunWith {\n" +
      "    Class<? extends Runner> value();\n" +
      "}\n",

      "package org.junit.runners;\n" +
      "import java.lang.annotation.ElementType;\n" +
      "import java.lang.annotation.Retention;\n" +
      "import java.lang.annotation.RetentionPolicy;\n" +
      "import java.lang.annotation.Target;\n" +
      "import org.junit.runner.Runner;\n" +
      "public class Parameterized extends Runner {\n" +
      "    @Retention(RetentionPolicy.RUNTIME)\n" +
      "    @Target(ElementType.METHOD)\n" +
      "    public @interface Parameters {\n" +
      "    }" +
      "}",

      "package junit.framework;" +
      "public abstract class TestCase {}"};
  }
}