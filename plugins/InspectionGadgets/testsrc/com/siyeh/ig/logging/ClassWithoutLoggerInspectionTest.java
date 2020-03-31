// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.logging;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ClassWithoutLoggerInspectionTest extends LightJavaInspectionTestCase {

  public void testClassWithoutLogger() { doTest();}
  public void testClassWithLogger() { doTest();}
  public void testMyException() { doTest();}
  public void testAnnotation() { doTest();}

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final ClassWithoutLoggerInspection inspection = new ClassWithoutLoggerInspection();
    inspection.annotations.add("test.Entity");
    return inspection;
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.util.logging;" +
      "public class Logger {" +
      "  public static Logger getLogger(String name) {" +
      "    return null;" +
      "  }" +
      "}",

      "package test;" +
      "public @interface Entity {}"
    };
  }
}
