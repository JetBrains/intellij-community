package com.siyeh.ig.logging;

import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightInspectionTestCase;

public class StringConcatenationArgumentToLogCallInspectionTest extends LightInspectionTestCase {
  @Override
  protected LocalInspectionTool getInspection() {
    return new StringConcatenationArgumentToLogCallInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[]{
      "package org.slf4j; public interface Logger { void debug(String format); }",
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
}