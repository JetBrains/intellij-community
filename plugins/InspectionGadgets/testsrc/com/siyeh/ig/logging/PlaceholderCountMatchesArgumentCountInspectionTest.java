// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.logging;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.util.text.StringUtil;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.intellij.lang.annotations.Language;

import java.util.ArrayList;
import java.util.Arrays;

@SuppressWarnings("PlaceholderCountMatchesArgumentCount")
public class PlaceholderCountMatchesArgumentCountInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected InspectionProfileEntry getInspection() {
    PlaceholderCountMatchesArgumentCountInspection inspection = new PlaceholderCountMatchesArgumentCountInspection();
    inspection.slf4jToLog4J2Type = PlaceholderCountMatchesArgumentCountInspection.Slf4jToLog4J2Type.YES;
    String option = StringUtil.substringAfter(getName(), "_");
    String disabled = "disable_";
    String auto = "auto_";
    if(option != null && option.startsWith(disabled)) {
      inspection.slf4jToLog4J2Type = PlaceholderCountMatchesArgumentCountInspection.Slf4jToLog4J2Type.NO;
    }
    else if (option != null && option.startsWith(auto)) {
      inspection.slf4jToLog4J2Type = PlaceholderCountMatchesArgumentCountInspection.Slf4jToLog4J2Type.AUTO;
    }
    return inspection;
  }


  @Override
  protected String[] getEnvironmentClasses() {
    @Language("JAVA") String[] baseClasses = {
      """
      package org.slf4j.spi;
      public interface LoggingEventBuilder {
         void log(String format, Object... arguments);
      }
      """,
      """
      package org.slf4j;\s
      import org.slf4j.spi.LoggingEventBuilder;\s
      public class LoggerFactory { public static Logger getLogger(Class clazz) { return null; }}
      public interface Logger {\s
         void info(String format, Object... arguments);\s
         LoggingEventBuilder atError();\s
      }
      """,
      """
      package org.apache.logging.log4j;
      import org.apache.logging.log4j.util.Supplier;
      public interface Logger {
        void info(String message, Object... params);
        void fatal(String message, Object... params);
        void error(Supplier<?> var1, Throwable var2);
        void info(String message, Supplier<?>... params);
        LogBuilder atInfo();
        LogBuilder atFatal();
        LogBuilder atError();
      }
      """,

      """
      package org.apache.logging.log4j;
      public class LogManager {
        public static Logger getLogger() {
          return null;
        }
        public static Logger getFormatterLogger() {
          return null;
        }
      }
      """,

      """
      package org.apache.logging.log4j.util;
      public interface Supplier<T> {
          T get();
      }
      """,

      """
      package org.apache.logging.log4j;
      import org.apache.logging.log4j.util.Supplier;
      public interface LogBuilder {
        void log(String format, Object p0);
        void log(String format, Object... params);
        void log(String format, Supplier<?>... params);
      }
      """
    };

    String option = StringUtil.substringAfter(getName(), "_");
    String auto = "auto_";

    if (option != null && option.startsWith(auto)) {
      ArrayList<String> temp = new ArrayList<>(Arrays.asList(baseClasses));
      temp.add("""
                  package org.apache.logging.slf4j.;
                  public interface Log4jLogger {
                  }
                 """);
      baseClasses = temp.toArray(baseClasses);
    }
    return baseClasses;
  }

  public void testLog4j2() {
    doTest("""
             import org.apache.logging.log4j.*;
             class Logging {
               private static final Logger LOG = LogManager.getLogger();
               void m(int i) {
                 LOG.info(/*Fewer arguments provided (1) than placeholders specified (3)*/"hello? {}{}{}"/**/, i);
                 LogManager.getLogger().fatal(/*More arguments provided (1) than placeholders specified (0)*/"you got me "/**/,  i);
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
                 LOG.fatal(/*Fewer arguments provided (1) than placeholders specified (6)*/text + text/**/, i);
                 LOG.fatal(/*Fewer arguments provided (1) than placeholders specified (18)*/text + text + text + text + text + text/**/, i);
                 LOG.info(/*More arguments provided (1) than placeholders specified (0)*/FINAL_TEXT/**/, i);
                 String text3;
                 text3 = "another";
                 LOG.info(/*More arguments provided (1) than placeholders specified (0)*/text3/**/, i);
                 String sum = "first {}" + "second {}" + 1;
                 LOG.info(/*Fewer arguments provided (1) than placeholders specified (2)*/sum/**/, i);
               }
             }""");
  }

  public void testLog4j2Builder() {
    doTest("""
             import org.apache.logging.log4j.*;
             class Logging {
               private static final Logger LOG = LogManager.getLogger();
               void m(int i) {
                try {
                  throw new RuntimeException();
                } catch (Throwable t) {
                 LOG.atError().log(/*More arguments provided (2) than placeholders specified (1)*/"'{}'"/**/, "bar", new Exception());
                 LOG.atError().log("'{}' '{}'", "bar", new Exception());
                 LOG.atError().log("'{}'", "bar");
                }
               }
             }""");
  }

  public void testSlf4J_disable_slf4jToLog4J2Type() {
    doTest("""
             import org.slf4j.*;
             class X {
               void foo() {
                 Logger logger = LoggerFactory.getLogger(X.class);
                 logger.info(/*Fewer arguments provided (1) than placeholders specified (2)*/"string {} {}"/**/, 1, new RuntimeException());
                 logger.atError().log(/*Fewer arguments provided (0) than placeholders specified (1)*/"{}"/**/, new RuntimeException("test"));
                 LoggerFactory.getLogger(X.class).atError().log(/*Fewer arguments provided (1) than placeholders specified (2)*/"{} {}"/**/, 1, new RuntimeException("test"));
                 LoggerFactory.getLogger(X.class).atError().log("{}", 1, new RuntimeException("test"));
               }
             }"""
    );
  }

  public void testSlf4J_auto_slf4jToLog4J2Type() {
    doTest("""
             import org.slf4j.*;
             class X {
               void foo() {
                 Logger logger = LoggerFactory.getLogger(X.class);
                 logger.info("string {} {}", 1, new RuntimeException());
                 logger.atError().log("{}", new RuntimeException("test"));
                 LoggerFactory.getLogger(X.class).atError().log("{} {}", 1, new RuntimeException("test"));
                 LoggerFactory.getLogger(X.class).atError().log(/*More arguments provided (2) than placeholders specified (1)*/"{}"/**/, 1, new RuntimeException("test"));
               }
             }"""
    );
  }

  public void testSlf4JBuilder() {
    doTest("""
             import org.slf4j.*;
             class X {
               void foo() {
                 Logger logger = LoggerFactory.getLogger(X.class);
                 LoggerFactory.getLogger(X.class).atError().log("{}", new RuntimeException("test"));
                 LoggerFactory.getLogger(X.class).atError().log("{} {}", 1, new RuntimeException("test"));
                 LoggerFactory.getLogger(X.class).atError().log(/*More arguments provided (2) than placeholders specified (1)*/"{}"/**/, 1, new RuntimeException("test"));
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

  public void testLog4j2WithExceptionInSuppliers() {
    doTest("""
             import org.apache.logging.log4j.*;
             import org.apache.logging.log4j.util.Supplier;
             class Logging {
               private static final Logger LOG = LogManager.getLogger();
               void m(int i) {
                try {
                  throw new RuntimeException();
                } catch (IllegalArgumentException | IllegalStateException t) {
                    LOG.info(/*More arguments provided (3) than placeholders specified (1)*/"test {}"/**/, () -> "test", () -> "test", () -> t);
                } catch (Throwable t) {
                    LOG.info("test {}", () -> "test", () -> t);
                    LOG.info(/*More arguments provided (3) than placeholders specified (1)*/"test {}"/**/, () -> "test", () -> "test", () -> t);
                    Supplier<Throwable> s = () -> t;
                    LOG.info("test {}", () -> "test", s);
                    Supplier<?> s2 = () -> t;
                    LOG.info("test {}", () -> "test", s2);
                    Supplier s3 = () -> t;
                    LOG.info("test {}", () -> "test", s3);
                    LOG.info("test {}", () -> "test", RuntimeException::new);
                    LOG.info("test {}", () -> "test");
                }
               }
             }""");
  }

  public void testSlf4JPartial() {
    doTest("""
             import org.slf4j.*;
             import java.util.Random;
             class X {
              Logger logger = LoggerFactory.getLogger(X.class);
              
              private static final String logText = "{} {}" + getSomething();
              private static final String logText2 = "{} {}" + 1 + "{}" + getSomething();
              private static final String logText3 = "{} {}" + 1 + "{}";
          
              private static String getSomething(){
                return new Random().nextBoolean() ? "{}" : "";
              }
              
              void m(String t) {
               logger.info("{} {}", 1, 2);
               logger.info(/*Fewer arguments provided (0) than placeholders specified (at least 2)*/"{}" + t + 1 + "{}"/**/);
               logger.info(/*Fewer arguments provided (0) than placeholders specified (at least 1)*/"{}" + t + 1/**/);
               logger.info(/*Fewer arguments provided (0) than placeholders specified (at least 1)*/"{}" + t + "{}"/**/);
               logger.info("{}" + t + "{}", 1, 2);
               logger.info("{}" + t + "{}", 1, 2, 3);
               String temp = "{} {}" + t;
               logger.info(/*Fewer arguments provided (1) than placeholders specified (at least 2)*/temp/**/, 1);
               logger.info(temp, 1, 2, 3);
               logger.info(logText, 1, 2, 3);
               logger.info(/*Fewer arguments provided (1) than placeholders specified (at least 2)*/logText/**/, 1);
               logger.info(/*Fewer arguments provided (1) than placeholders specified (at least 3)*/logText2/**/, 1);
               logger.info(/*Fewer arguments provided (1) than placeholders specified (3)*/logText3/**/, 1);
               temp = "{}" + t;
               logger.info(temp , 1);
              }
                         
              void m(int i, String s) {
               logger.info(/*Fewer arguments provided (0) than placeholders specified (1)*/"test1 {}"/**/);
               logger.info(/*Fewer arguments provided (0) than placeholders specified (at least 1)*/"test1 {}" + s/**/);
               logger.info(/*Fewer arguments provided (0) than placeholders specified (1)*/"test1 {}" + i/**/);
              }
             }""");
  }
  public void testLog4jBuilderPartial() {
    doTest("""
             import org.apache.logging.log4j.*;
             import java.util.Random;
             class Logging {
              private static final Logger LOG = LogManager.getLogger();
              private static final String logText = "{} {}" + getSomething();
              private static final String logText2 = "{} {}" + 1 + "{}" + getSomething();
              private static final String logText3 = "{} {}" + 1 + "{}";
              private static String getSomething(){
                return new Random().nextBoolean() ? "{}" : "";
              }
              static {
               LOG.atError().log(/*Fewer arguments provided (1) than placeholders specified (at least 2)*/logText/**/, 1);
               LOG.atError().log(/*Fewer arguments provided (1) than placeholders specified (at least 4)*/logText + logText2/**/, 1);
              }
              public static void test(String t) {
               LogBuilder logger = LOG.atError();
               logger.log("{} {}", 1, 2);
               logger.log(/*Fewer arguments provided (0) than placeholders specified (at least 2)*/"{}" + t + 1 + "{}"/**/);
               logger.log(/*Fewer arguments provided (0) than placeholders specified (at least 1)*/"{}" + t + 1/**/);
               logger.log(/*Fewer arguments provided (0) than placeholders specified (at least 1)*/"{}" + t + "{}"/**/);
               logger.log("{}" + t + "{}", 1, 2);
               logger.log("{}" + t + "{}", 1, 2, 3);
               String temp = "{} {}" + t;
               logger.log(/*Fewer arguments provided (1) than placeholders specified (at least 2)*/temp/**/, 1);
               logger.log(temp, 1, 2, 3);
               logger.log(logText, 1, 2, 3);
               logger.log(/*Fewer arguments provided (1) than placeholders specified (at least 2)*/logText/**/, 1);
               logger.log(/*Fewer arguments provided (1) than placeholders specified (at least 3)*/logText2/**/, 1);
               logger.log(/*Fewer arguments provided (1) than placeholders specified (3)*/logText3/**/, 1);
               temp = "{}" + t;
               logger.log(temp , 1);
              }
             }""");
  }
  public void testFormatterLog4jPartial() {
    doTest("""
             import org.apache.logging.log4j.*;
             class Logging {
              private static final Logger logger = LogManager.getFormatterLogger();
              public static void test(String t) {
               logger.info(/*Fewer arguments provided (0) than placeholders specified (at least 1)*/"%s" + t + 1 + "%s "/**/);
               logger.info(/*Fewer arguments provided (0) than placeholders specified (at least 2)*/"%s %s" + t + 1/**/);
               logger.info("%s" + t + "%s", 1);
              }
             }""");
  }

  public void testLog4jPartial() {
    doTest("""
             import org.apache.logging.log4j.*;
             class Logging {
              private static final Logger logger = LogManager.getLogger();
              public static void test(String t) {
                logger.info(/*Fewer arguments provided (0) than placeholders specified (at least 2)*/"{}" + t + 1 + "{}"/**/);
                logger.info(/*Fewer arguments provided (0) than placeholders specified (at least 2)*/"{}" + t + 1 + "{}"/**/, new RuntimeException());
                logger.info(/*Fewer arguments provided (1) than placeholders specified (at least 3)*/"{} {}" + t + 1 + "{}"/**/, 1, new RuntimeException());
                logger.info("{}" + t + 1 + "{}", 1, new RuntimeException());
                logger.info("{}" + t + 1 + "{}", 1, 1);
              }
             }""");
  }

  public void testManyVariables() {
    doTest("""
             import org.slf4j.*;
             import java.util.Random;
             class X {
              Logger LOGGER = LoggerFactory.getLogger(X.class);
              
                  void m(String s) {
                      String a1 = " {} " + s;
                      String a2 = a1 + a1 + a1 + a1 + a1 + a1 + a1 + a1 + a1;
                      String a3 = a2 + a2 + a2 + a2 + a2 + a2 + a2 + a2 + a2;
                      String a4 = a3 + a3 + a3 + a3 + a3 + a3 + a3 + a3 + a3;
                      String a5 = a4 + a4 + a4 + a4 + a4 + a4 + a4 + a4 + a4;
                      String a6 = a5 + a5 + a5 + a5 + a5 + a5 + a5 + a5 + a5;
                      String a7 = a6 + a6 + a6 + a6 + a6 + a6 + a6 + a6 + a6;
                      String a8 = a7 + a7 + a7 + a7 + a7 + a7 + a7 + a7 + a7;
                      String a9 = a8 + a8 + a8 + a8 + a8 + a8 + a8 + a8 + a8;
                      String a10 = a9 + a9 + a9 + a9 + a9+ a9 + a9 + a9 + a9;
                      LOGGER.info(/*Fewer arguments provided (1) than placeholders specified (at least 10)*/"abd" + a10/**/, 1);
                      LOGGER.info(/*Fewer arguments provided (1) than placeholders specified (at least 10)*/"abd" + a10/**/, 1);
                      LOGGER.info(/*Fewer arguments provided (1) than placeholders specified (at least 10)*/"abd" + a10/**/, 1);
                      LOGGER.info(/*Fewer arguments provided (1) than placeholders specified (at least 10)*/"abd" + a10/**/, 1);
                      LOGGER.info(/*Fewer arguments provided (1) than placeholders specified (at least 10)*/"abd" + a10/**/, 1);
                      LOGGER.info(/*Fewer arguments provided (1) than placeholders specified (at least 10)*/"abd" + a10/**/, 1);
                      LOGGER.info(/*Fewer arguments provided (1) than placeholders specified (at least 10)*/"abd" + a10/**/, 1);
                      LOGGER.info(/*Fewer arguments provided (1) than placeholders specified (at least 10)*/"abd" + a10/**/, 1);
                      LOGGER.info(/*Fewer arguments provided (1) than placeholders specified (at least 10)*/"abd" + a10/**/, 1);
                      LOGGER.info(/*Fewer arguments provided (1) than placeholders specified (at least 10)*/"abd" + a10/**/, 1);
                  }
             }""");
    
  }
  }