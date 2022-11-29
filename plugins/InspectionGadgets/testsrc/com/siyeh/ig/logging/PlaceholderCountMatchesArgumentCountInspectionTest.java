// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.logging;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ui.OptionAccessor;
import com.intellij.openapi.util.text.StringUtil;
import com.siyeh.ig.LightJavaInspectionTestCase;

@SuppressWarnings("PlaceholderCountMatchesArgumentCount")
public class PlaceholderCountMatchesArgumentCountInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected InspectionProfileEntry getInspection() {
    PlaceholderCountMatchesArgumentCountInspection inspection = new PlaceholderCountMatchesArgumentCountInspection();
    inspection.slf4jThrowableShouldNotHavePlaceholder = false;
    String option = StringUtil.substringAfter(getName(), "_");
    if(option != null) {
      new OptionAccessor.Default(inspection).setOption(option, true);
    }
    return inspection;
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
      "  void info(String message, Supplier<?>... params);" +
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
      "  public static Logger getFormatterLogger() {" +
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
      "  void log(String format, Object p0);" +
      "  void log(String format, Object... params);" +
      "  void log(String format, Supplier<?>... params);" +
      "}"
    };
  }

  public void testLog4j2() {
    doTest("""
             import org.apache.logging.log4j.*;
             class Logging {
               private static final Logger LOG = LogManager.getLogger();
               void m(int i) {
                 LOG.info(/*Fewer arguments provided (1) than placeholders specified (3)*/"hello? {}{}{}"/**/, i);
                 LOG.fatal(/*More arguments provided (1) than placeholders specified (0)*/"you got me "/**/,  i);
                 LOG.error(() -> "", new Exception());
               }
             }""");
  }

  public void testLog4j2LogBuilder() {
    doTest("""
             import org.apache.logging.log4j.*;
             class Logging {
               private static final Logger LOG = LogManager.getLogger();
               void m(int i) {
                 LOG.atInfo().log(/*Fewer arguments provided (1) than placeholders specified (3)*/"hello? {}{}{}"/**/, i);
                 LOG.atFatal().log(/*More arguments provided (2) than placeholders specified (0)*/"you got me "/**/, i, i);
                 LOG.atError().log(/*More arguments provided (1) than placeholders specified (0)*/"what does the supplier say? "/**/, () -> "");
               }
             }""");
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
    doTest("""
             import org.slf4j.*;
             class X {
               void foo() {
                 Logger logger = LoggerFactory.getLogger(X.class);
                 logger.info("string {}", 1);
               }
             }"""
           );
  }

  public void testMorePlaceholders() {
    doTest("""
             import org.slf4j.*;
             class X {
               void foo() {
                 Logger logger = LoggerFactory.getLogger(X.class);
                 logger.info(/*Fewer arguments provided (1) than placeholders specified (2)*/"string {}{}"/**/, 1);
               }
             }"""
    );
  }

  public void testFewerPlaceholders() {
    doTest("""
             import org.slf4j.*;
             class X {
               void foo() {
                 Logger logger = LoggerFactory.getLogger(X.class);
                 logger.info(/*More arguments provided (1) than placeholders specified (0)*/"string"/**/, 1);
               }
             }"""
    );
  }

  public void testThrowable() {
    doTest("""
             import org.slf4j.*;
             class X {
               void foo() {
                 Logger logger = LoggerFactory.getLogger(X.class);
                 logger.info("string {}", 1, new RuntimeException());
               }
             }"""
    );
  }

  @SuppressWarnings("RedundantThrows")
  public void testMultiCatch() {
    doTest("""
             import org.slf4j.*;
             class X {
                 private static final Logger logger = LoggerFactory.getLogger( X.class );
                 public void multiCatch() {
                     try {
                         method();
                     } catch ( FirstException|SecondException e ) {
                         logger.info( "failed with first or second", e );
                     }
                 }
                 public void method() throws FirstException, SecondException {}
                 public static class FirstException extends Exception { }
                 public static class SecondException extends Exception { }
             }""");
  }

  public void testNoSlf4j() {
    doTest("""
             class FalsePositiveSLF4J {
                 public void method( DefinitelyNotSLF4J definitelyNotSLF4J ) {
                     definitelyNotSLF4J.info( "not a trace message", "not a trace parameter" );
                 }
                 public interface DefinitelyNotSLF4J {
                     void info( String firstParameter, Object secondParameter );
                 }
             }""");
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

  public void testLog4j2WithTextVariables() {
    doTest("""
             import org.apache.logging.log4j.*;
             class Logging {
               private static final String FINAL_TEXT = "const";
               private static final Logger LOG = LogManager.getLogger();
               void m(int i) {
                 String text = "test {}{}{}";
                 LOG.info(/*Fewer arguments provided (1) than placeholders specified (3)*/text/**/, i);
                 final String text2 = "test ";
                 LOG.fatal(/*More arguments provided (1) than placeholders specified (0)*/text2/**/, i);
                 LOG.info(/*More arguments provided (1) than placeholders specified (0)*/FINAL_TEXT/**/, i);
               }
             }""");
  }
  public void testLog4j2WithExceptionInSuppliers() {
    doTest("""
             import org.apache.logging.log4j.*;
             class Logging {
               private static final Logger LOG = LogManager.getLogger();
               void m(int i) {
                try {
                  throw new RuntimeException();
                } catch (Throwable t) {
                    LOG.info("test {}", () -> "test", () -> t);
                    LOG.info("test {}", () -> "test");
                }
               }
             }""");
  }

  public void testLog4j2BuilderWithException() {
    doTest("""
             import org.apache.logging.log4j.*;
             class Logging {
               private static final Logger LOG = LogManager.getLogger();
               void m(int i) {
                try {
                  throw new RuntimeException();
                } catch (Throwable t) {
                 LOG.atError().log(/*More arguments provided (2) than placeholders specified (1)*/"'{}'"/**/, "bar", new Exception());
                 LOG.atError().log(/*More arguments provided (2) than placeholders specified (1)*/"test test test {}"/**/, () -> "123", () -> t);
                }
               }
             }""");
  }

  public void testSlf4J_slf4jThrowableShouldNotHavePlaceholder() {
    doTest("""
             import org.slf4j.*;
             class X {
               void foo() {
                 Logger logger = LoggerFactory.getLogger(X.class);
                 logger.info(/*Fewer arguments provided (1) than placeholders specified (2)*/"string {} {}"/**/, 1, new RuntimeException());
               }
             }"""
    );
  }

  public void testFormattedLog4J() {
    doTest("""
             import org.apache.logging.log4j.*;
             class X {
               private static final Logger LOG = LogManager.getFormatterLogger();
               void m() {
                try {
                  throw new RuntimeException();
                } catch (Throwable t) {
                 Logger LOG2 = LogManager.getFormatterLogger();
                 LOG.info("My %s text", "test", t);
                 LOG.info(/*Illegal format string specifier*/"My %i text"/**/, "test");
                 LOG.info(/*More arguments provided (2) than placeholders specified (1)*/"My %s text"/**/, "test1", "test2");
                 LOG2.info("My %s text, %s", "test1"); //skip because LOG2 is not final
                 LogManager.getFormatterLogger().info(/*Fewer arguments provided (1) than placeholders specified (2)*/"My %s text, %s"/**/, "test1");
                                                                       }
               }
             }"""
    );
  }
}