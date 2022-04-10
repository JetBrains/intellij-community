// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.internationalization;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class SimpleDateFormatWithoutLocaleInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected InspectionProfileEntry getInspection() {
    return new SimpleDateFormatWithoutLocaleInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.text;" +
      "import java.util.Locale;" +
      "public class SimpleDateFormat {" +
      "  public SimpleDateFormat(String pattern) {}" +
      "  public SimpleDateFormat(String pattern, Locale locale) {}" +
      "}",

      "package java.time.format;" +
      "import java.util.Locale;" +
      "public final class DateTimeFormatter {" +
      "  public static DateTimeFormatter ofPattern(String pattern) {" +
      "    return null;" +
      "  }" +
      "  public static DateTimeFormatter ofPattern(String pattern, Locale locale) {" +
      "    return null;" +
      "  }" +
      "}"
    };
  }

  public void testSimpleDateFormatWithoutLocale() { doTest(); }
}
