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
package com.siyeh.ig.logging;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import junit.framework.TestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class LoggingConditionDisagreesWithLogStatementInspectionTest extends LightInspectionTestCase {

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[]{
      "package org.slf4j;" +
      "public interface Logger { " +
      "  boolean isInfoEnabled(); " +
      "  void info(String format, Object... arguments);" +
      "  void warn(String format, Object... arguments);" +
      "}",

      "package org.slf4j; " +
      "public class LoggerFactory {" +
      "  public static Logger getLogger(Class clazz) {" +
      "    return null; " +
      "  }" +
      "}",

      "package org.apache.logging.log4j;" +
      "public interface Logger {" +
      "  void warn(String var2);" +
      "  boolean isInfoEnabled() {" +
      "    return true;" +
      "  };" +
      "}",

      "package org.apache.logging.log4j;" +
      "public class LogManager {" +
      "  public static Logger getLogger() {" +
      "    return null;" +
      "  }" +
      "}",

      "package java.util.logging;" +
      "public class Logger {" +
      "  public static Logger getLogger(String name) {" +
      "    return null;" +
      "  }" +
      "  public void warning(String msg) {}" +
      "  public boolean isLoggable(Level level) {}" +
      "}",

      "package java.util.logging;" +
      "public class Level {" +
      "  public static final Level FINE = new Level();" +
      "  public static final Level WARNING = new Level();" +
      "}"
    };
  }

  public void testSlf4j() {
    doTest("import org.slf4j.Logger;" +
           "import org.slf4j.LoggerFactory;" +
           "class X {" +
           "  private static final Logger LOG = LoggerFactory.getLogger(X.class);" +
           "  void n() {" +
           "    if (LOG.isInfoEnabled()) {" +
           "      LOG.info(\"nothing to report\");" +
           "    }" +
           "    if (LOG./*Log condition 'isInfoEnabled()' does not match 'warn()' logging call*/isInfoEnabled/**/()) {" +
           "      LOG.warn(\"the sky is falling!\");" +
           "    }" +
           "  }" +
           "}");
  }

  public void testLog4j2() {
    doTest("import org.apache.logging.log4j.*;" +
           "class X {" +
           "  static final Logger LOG = LogManager.getLogger();" +
           "  void m() {" +
           "    if (LOG./*Log condition 'isInfoEnabled()' does not match 'warn()' logging call*/isInfoEnabled/**/()) {" +
           "      LOG.warn(\"hi!\");" +
           "    }" +
           "  }" +
           "}");
  }

  public void testJavaUtilLogging() {
    doTest("import java.util.logging.*;" +
           "class Loggers {" +
           "  static final Logger LOG = Logger.getLogger(\"\");" +
           "  public void method() {" +
           "    if (LOG./*Log condition 'isLoggable()' does not match 'warning()' logging call*/isLoggable/**/(Level.FINE)) {" +
           "      LOG.warning(\"asdfasdf\");" +
           "    }" +
           "    if (LOG.isLoggable(Level.WARNING)) {" +
           "      LOG.warning(\"oops!\");" +
           "    }" +
           "  }" +
           "}");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new LoggingConditionDisagreesWithLogStatementInspection();
  }
}