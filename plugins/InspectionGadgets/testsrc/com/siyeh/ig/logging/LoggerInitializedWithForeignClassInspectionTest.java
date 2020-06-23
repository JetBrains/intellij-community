// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.logging;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class LoggerInitializedWithForeignClassInspectionTest extends LightJavaInspectionTestCase {

  public void testLoggerInitializedWithForeignClass() {
    doTest();
  }

  public void testLog4J2() {
    doTest();
    checkQuickFixAll();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new LoggerInitializedWithForeignClassInspection();
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

      "package org.apache.logging.log4j;" +
      "public class LogManager {" +
      "  public static Logger getLogger(Class<?> clazz) {" +
      "    return null;" +
      "  }" +
      "  public static Logger getLogger(String name) {" +
      "    return null;" +
      "  }" +
      "}" +
      "public interface Logger {}"
    };
  }
}
