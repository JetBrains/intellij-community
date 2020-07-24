package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import com.siyeh.ig.testFrameworks.AssertBetweenInconvertibleTypesInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AssertEqualsBetweenInconvertibleTypesInspectionTest extends LightJavaInspectionTestCase {

  public void testAssertEqualsBetweenInconvertibleTypes() {
    doTest();
  }
  public void testAssertEqualsBetweenInconvertibleTypesAssertJ() {
    doTest();
  }
  public void testAssertEqualsBetweenInconvertibleTypesJUnit5() {
    doTest();
  }
  public void testAssertNotEqualsBetweenInconvertibleTypes() {
    doTest();
  }
  public void testAssertNotEqualsBetweenInconvertibleTypesAssertJ() {
    doTest();
  }
  public void testAssertNotEqualsBetweenInconvertibleTypesJUnit5() {
    doTest();
  }
  public void testAssertSameBetweenInconvertibleTypes() {
    doTest();
  }
  public void testAssertSameBetweenInconvertibleTypesAssertJ() {
    doTest();
  }
  public void testAssertSameBetweenInconvertibleTypesJUnit5() {
    doTest();
  }
  public void testAssertNotSameBetweenInconvertibleTypes() {
    doTest();
  }
  public void testAssertNotSameBetweenInconvertibleTypesAssertJ() {
    doTest();
  }
  public void testAssertNotSameBetweenInconvertibleTypesJUnit5() {
    doTest();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package org.junit;" +
      "import java.lang.annotation.ElementType;" +
      "import java.lang.annotation.Retention;" +
      "import java.lang.annotation.RetentionPolicy;" +
      "import java.lang.annotation.Target;" +
      "@Retention(RetentionPolicy.RUNTIME)" +
      "@Target(ElementType.METHOD)" +
      "public @interface Test {}", 

      "package org.junit.jupiter.api;" +
      "import java.lang.annotation.ElementType;" +
      "import java.lang.annotation.Retention;" +
      "import java.lang.annotation.RetentionPolicy;" +
      "import java.lang.annotation.Target;" +
      "@Retention(RetentionPolicy.RUNTIME)" +
      "@Target(ElementType.METHOD)" +
      "public @interface Test {}",
      
      "package org.junit;" +
      "public class Assert {" +
      "  static public void assertEquals(double expected, double actual, double delta) {}" +
      "  static public void assertEquals(Object expected, Object actual){}" +
      "  static public void assertNotEquals(Object expected, Object actual){}" +

      "  static public void assertSame(Object expected, Object actual){}" +
      "  static public void assertNotSame(Object expected, Object actual){}" +
      "}",

      "package org.junit.jupiter.api;\n" +
      "import java.util.function.Supplier;\n" +
      "public final class Assertions {\n" +
      "    public static void assertEquals(double expected, double actual) {}\n" +
      "    public static void assertEquals(double expected, double actual, String message) {}\n" +
      "    public static void assertEquals(double expected, double actual, Supplier<String> messageSupplier) {}\n" +
      "    public static void assertEquals(Object expected, Object actual) {}\n" +
      "    public static void assertEquals(Object expected, Object actual, String message) {}\n" +
      "    public static void assertEquals(Object expected, Object actual, Supplier<String> messageSupplier) {}\n" +"    " +
      "    public static void assertNotEquals(Object expected, Object actual) {}\n" +
      "    public static void assertNotEquals(Object expected, Object actual, String message) {}\n" +
      "    public static void assertNotEquals(Object expected, Object actual, Supplier<String> messageSupplier) {}\n" +

      "    public static void assertSame(Object expected, Object actual) {}\n" +
      "    public static void assertSame(Object expected, Object actual, String message) {}\n" +
      "    public static void assertSame(Object expected, Object actual, Supplier<String> messageSupplier) {}\n" +
      "    public static void assertNotSame(Object expected, Object actual) {}\n" +
      "    public static void assertNotSame(Object expected, Object actual, String message) {}\n" +
      "    public static void assertNotSame(Object expected, Object actual, Supplier<String> messageSupplier) {}\n" +
      "}",

      "package org.assertj.core.api;\n" +
      "public class Assertions {\n" +
      "  public static <T> ObjectAssert<T> assertThat(T actual);\n" +
      "}",

      "package org.assertj.core.api;\n" +
      "public class ObjectAssert<T> extends Assert<ObjectAssert<T>, T> {}",

      "package org.assertj.core.api;\n" +
      "public class Assert<SELF extends Assert<SELF, ACTUAL>, ACTUAL> extends Descriptable<SELF> {\n" +
      "  public SELF isEqualTo(Object expected);\n" +
      "  public SELF isNotEqualTo(Object expected);\n" +

      "  public SELF isSameAs(Object expected);\n" +
      "  public SELF isNotSameAs(Object expected);\n" +
      "}",

      "package org.assertj.core.api;\n" +
      "public interface Descriptable<SELF> {\n" +
      "  SELF describedAs(String description, Object... args);\n" +
      "  default SELF as(String description, Object... args);\n" +
      "  SELF isEqualTo(Object expected);\n" +
      "}",
    };
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/junit/assert_equals_between_inconvertible_types";
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new AssertBetweenInconvertibleTypesInspection();
  }
}