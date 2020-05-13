// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.logging;

import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightJavaInspectionTestCase;

@SuppressWarnings("PlaceholderCountMatchesArgumentCount")
public class PlaceholderCountMatchesArgumentCountInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected LocalInspectionTool getInspection() {
    return new PlaceholderCountMatchesArgumentCountInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[]{
      "package org.slf4j; public interface Logger { void info(String format, Object... arguments); }",
      "package org.slf4j; public class LoggerFactory { public static Logger getLogger(Class clazz) { return null; }}",

      "package org.apache.logging.log4j;" +
      "import org.apache.logging.log4j.util.Supplier;" +
      "public interface Logger {" +
      "  void info(String message, Object... params);" +
      "  void fatal(String message, Object... params);" +
      "  void error(Supplier<?> var1, Throwable var2);" +
      "  LogBuilder atInfo();" +
      "  LogBuilder atFatal();" +
      "  LogBuilder atError();" +
      "}",

      "package org.apache.logging.log4j;" +
      "public class LogManager {" +
      "  public static Logger getLogger() {" +
      "    return null;" +
      "  }" +
      "}",

      "package org.apache.logging.log4j.util;" +
      "public interface Supplier<T> {" +
      "    T get();" +
      "}",

      "package org.apache.logging.log4j;" +
      "import org.apache.logging.log4j.util.Supplier;" +
      "public interface LogBuilder {" +
      "  public void log(String format, Object p0);" +
      "  public void log(String format, Object... params);" +
      "  public void log(String format, Supplier<?>... params);" +
      "}"
    };
  }

  public void testLog4j2() {
    doTest("import org.apache.logging.log4j.*;\n" +
           "class Logging {\n" +
           "  private static final Logger LOG = LogManager.getLogger();\n" +
           "  void m(int i) {\n" +
           "    LOG.info(/*Fewer arguments provided (1) than placeholders specified (3)*/\"hello? {}{}{}\"/**/, i);\n" +
           "    LOG.fatal(/*More arguments provided (1) than placeholders specified (0)*/\"you got me \"/**/,  i);\n" +
           "    LOG.error(() -> \"\", new Exception());\n" +
           "  }\n" +
           "}");
  }

  public void testLog4j2LogBuilder() {
    doTest("import org.apache.logging.log4j.*;\n" +
           "class Logging {\n" +
           "  private static final Logger LOG = LogManager.getLogger();\n" +
           "  void m(int i) {\n" +
           "    LOG.atInfo().log(/*Fewer arguments provided (1) than placeholders specified (3)*/\"hello? {}{}{}\"/**/, i);\n" +
           "    LOG.atFatal().log(/*More arguments provided (2) than placeholders specified (0)*/\"you got me \"/**/, i, i);\n" +
           "    LOG.atError().log(/*More arguments provided (1) than placeholders specified (0)*/\"what does the supplier say? \"/**/, () -> \"\");\n" +
           "  }\n" +
           "}");
  }

  public void testOneExceptionArgument() {
    doTest("import org.slf4j.*;" +
           "class X {" +
           "  void foo() {" +
           "    RuntimeException e = new RuntimeException();" +
           "    LoggerFactory.getLogger(X.class).info(/*Fewer arguments provided (0) than placeholders specified (1)*/\"this: {}\"/**/, e);" +
           "  }" +
           "}");
  }

  public void testExceptionTwoPlaceholders() {
    doTest("import org.slf4j.*;" +
           "class X {" +
           "  void foo() {" +
           "    RuntimeException e = new RuntimeException();" +
           "    LoggerFactory.getLogger(X.class).info(\"1: {} e: {}\", 1, e);" +
           "  }" +
           "}");
  }

  public void testExceptionThreePlaceholder() {
    doTest("import org.slf4j.*;" +
           "class X {" +
           "  void foo() {" +
           "    RuntimeException e = new RuntimeException();" +
           "    LoggerFactory.getLogger(X.class).info(/*Fewer arguments provided (1) than placeholders specified (3)*/\"1: {} {} {}\"/**/, 1, e);" +
           "  }" +
           "}");
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
           "    logger.info(/*Fewer arguments provided (1) than placeholders specified (2)*/\"string {}{}\"/**/, 1);\n" +
           "  }\n" +
           "}"
    );
  }

  public void testFewerPlaceholders() {
    doTest("import org.slf4j.*;\n" +
           "class X {\n" +
           "  void foo() {\n" +
           "    Logger logger = LoggerFactory.getLogger(X.class);\n" +
           "    logger.info(/*More arguments provided (1) than placeholders specified (0)*/\"string\"/**/, 1);\n" +
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

  public void testNoSlf4j() {
    doTest("class FalsePositiveSLF4J {\n" +
           "    public void method( DefinitelyNotSLF4J definitelyNotSLF4J ) {\n" +
           "        definitelyNotSLF4J.info( \"not a trace message\", \"not a trace parameter\" );\n" +
           "    }\n" +
           "    public interface DefinitelyNotSLF4J {\n" +
           "        void info( String firstParameter, Object secondParameter );\n" +
           "    }\n" +
           "}");
  }

  @SuppressWarnings("RedundantArrayCreation")
  public void testArrayArgument() {
    doTest("import org.slf4j.*;" +
           "class X {" +
           "  Logger LOG = LoggerFactory.getLogger( X.class );" +
           "  void m(String a, int b, Object c) {" +
           "    LOG.info(\"schnizzle {} for blurb {} in quark {}\", new Object[] {a, b, c});" +
           "  }" +
           "}");
  }

  @SuppressWarnings("RedundantArrayCreation")
  public void testArrayWithException() {
    doTest("import org.slf4j.*;" +
           "class X {" +
           "  void z(int i) {" +
           "    RuntimeException e = new RuntimeException();" +
           "    LoggerFactory.getLogger(X.class).info(\"Freak mulching accident {} : {}\", new Object[] {i, e.getMessage(), e});" +
           "  }" +
           "}");
  }

  public void testUncountableArray() {
    doTest("import org.slf4j.*;" +
           "class X {" +
           "  Logger LOG = LoggerFactory.getLogger( X.class );" +
           "  void m(Object[] objects) {" +
           "    LOG.info(\"deep cover {} quantum disstressor {} at light speed {}\", objects);" +
           "  }" +
           "}");
  }

  public void testConstant() {
    doTest("import org.slf4j.*;" +
           "class X {" +
           "  Logger LOG = LoggerFactory.getLogger(X.class);" +
           "  private static final String message = \"HELLO {}\";" +
           "  void m() {" +
           "    LOG.info(/*Fewer arguments provided (0) than placeholders specified (1)*/message/**/);" +
           "  }" +
           "}");
  }

  public void testNonConstantString() {
    doTest("import org.slf4j.*;" +
           "class X {" +
           "  Logger LOG = LoggerFactory.getLogger(X.class);" +
           "  private static final String S = \"{}\";" +
           "  void m() {" +
           "    LOG.info(/*Fewer arguments provided (0) than placeholders specified (3)*/S +\"{}\" + (1 + 2) + '{' + '}' +Integer.class/**/);" +
           "  }" +
           "}");
  }

  public void testEscaping1() {
    doTest("import org.slf4j.*;" +
           "class X {" +
           "  Logger LOG = LoggerFactory.getLogger(X.class);" +
           "  void m() {" +
           "    LOG.info(\"Created registry key {}\\\\\\\\{}\", 1, 2);" +
           "  }" +
           "}");
  }

  public void testEscaping2() {
    doTest("import org.slf4j.*;" +
           "class X {" +
           "  Logger LOG = LoggerFactory.getLogger(X.class);" +
           "  void m() {" +
           "    LOG.info(/*More arguments provided (2) than placeholders specified (1)*/\"Created registry key {}\\\\{}\"/**/, 1, 2);" +
           "  }" +
           "}");
  }

  public void testNullArgument() {
    doTest("import org.slf4j.*;" +
           "class X {" +
           "  Logger LOG = LoggerFactory.getLogger(X.class);" +
           "  void m() {" +
           "    LOG.info(null, new Exception());" +
           "    LOG.info(\"\", new Exception());" +
           "  }" +
           "}");
  }
}