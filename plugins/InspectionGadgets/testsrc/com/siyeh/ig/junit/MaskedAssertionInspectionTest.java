// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import com.siyeh.ig.testFrameworks.MaskedAssertionInspection;
import org.jetbrains.annotations.Nullable;

public class MaskedAssertionInspectionTest extends LightJavaInspectionTestCase {

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[]{
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
      "  static public void assertEquals(Object expected, Object actual){}" +
      "  static public void assertNotEquals(Object expected, Object actual){}" +

      "  static public void assertSame(Object expected, Object actual){}" +
      "  static public void assertNotSame(Object expected, Object actual){}" +

      " static public void fail() {}" +
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

      "package junit.framework;\n" +
      "public class AssertionFailedError extends AssertionError {\n" +
      "}"
    };
  }

  public void testMaskedAssertion() {
    doTest();
  }

  public void testMaskedAssertionAssertJ() {
    doTest();
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new MaskedAssertionInspection();
  }

  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/junit/masked_assertion";
  }
}
