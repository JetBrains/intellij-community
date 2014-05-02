package com.siyeh.ig.logging;

import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightInspectionTestCase;

public class StringConcatenationArgumentToLogCallInspectionTest extends LightInspectionTestCase {
  @Override
  protected LocalInspectionTool getInspection() {
    final StringConcatenationArgumentToLogCallInspection inspection = new StringConcatenationArgumentToLogCallInspection();
    inspection.warnLevel = 3; // debug level and lower
    return inspection;
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[]{
      "package org.slf4j; public interface Logger { " +
      "  void debug(String format, Object... arguments); " +
      "  void info(String format, Object... arguments);" +
      "}",
      "package org.slf4j; public class LoggerFactory { public static Logger getLogger(Class clazz) { return null; }}"};
  }

  public void testBasic() {
    doTest("import org.slf4j.*;\n" +
           "class X {\n" +
           "  void foo() {\n" +
           "    Logger logger = LoggerFactory.getLogger(X.class);\n" +
           "    final String CONST = \"const\";\n" +
           "    String var = \"var\";\n" +
           "    logger./*Non-constant string concatenation as argument to 'debug()' logging call*/debug/**/(\"string \" + var + CONST);\n" +
           "  }\n" +
           "}"
           );
  }

  public void testWarnLevel() {
    doTest("import org.slf4j.*;" +
           "class X {" +
           "  Logger LOG = LoggerFactory.getLogger(X.class);" +
           "  void foo(String s) {" +
           "    LOG.info(\"value: \" + s);" +
           "  }" +
           "}");
  }
}