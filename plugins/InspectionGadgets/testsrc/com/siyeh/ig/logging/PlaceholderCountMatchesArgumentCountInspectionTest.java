package com.siyeh.ig.logging;

import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightInspectionTestCase;

public class PlaceholderCountMatchesArgumentCountInspectionTest extends LightInspectionTestCase {
  @Override
  protected LocalInspectionTool getInspection() {
    return new PlaceholderCountMatchesArgumentCountInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[]{
      "package org.slf4j; public interface Logger { void info(String format, Object... arguments); }",
      "package org.slf4j; public class LoggerFactory { public static Logger getLogger(Class clazz) { return null; }}"};
  }

  public void testNoWarn() {
    doTest("import org.slf4j.*;\n" +
           "class X {\n" +
           "  void foo() {\n" +
           "    Logger logger = LoggerFactory.getLogger(X.class);\n" +
           "    logger.info(\"string {}\", 1);\n" +
           "  }\n" +
           "}"
           );
  }

  public void testMorePlaceholders() {
    doTest("import org.slf4j.*;\n" +
           "class X {\n" +
           "  void foo() {\n" +
           "    Logger logger = LoggerFactory.getLogger(X.class);\n" +
           "    logger./*'info()' call has fewer arguments (1) than placeholders (2)*/info/**/(\"string {}{}\", 1);\n" +
           "  }\n" +
           "}"
    );
  }

  public void testFewerPlaceholders() {
    doTest("import org.slf4j.*;\n" +
           "class X {\n" +
           "  void foo() {\n" +
           "    Logger logger = LoggerFactory.getLogger(X.class);\n" +
           "    logger./*'info()' call has more arguments (1) than placeholders (0)*/info/**/(\"string\", 1);\n" +
           "  }\n" +
           "}"
    );
  }

  public void testThrowable() {
    doTest("import org.slf4j.*;\n" +
           "class X {\n" +
           "  void foo() {\n" +
           "    Logger logger = LoggerFactory.getLogger(X.class);\n" +
           "    logger.info(\"string {}\", 1, new RuntimeException());\n" +
           "  }\n" +
           "}"
    );
  }

  public void testMultiCatch() {
    doTest("import org.slf4j.*;\n" +
           "class X {\n" +
           "    private static final Logger logger = LoggerFactory.getLogger( X.class );\n" +
           "    public void multiCatch() {\n" +
           "        try {\n" +
           "            method();\n" +
           "        } catch ( FirstException|SecondException e ) {\n" +
           "            logger.info( \"failed with first or second\", e );\n" +
           "        }\n" +
           "    }\n" +
           "    public void method() throws FirstException, SecondException {}\n" +
           "    public static class FirstException extends Exception { }\n" +
           "    public static class SecondException extends Exception { }\n" +
           "}");
  }
}