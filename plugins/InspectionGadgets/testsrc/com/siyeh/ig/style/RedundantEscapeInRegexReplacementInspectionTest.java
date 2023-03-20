// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class RedundantEscapeInRegexReplacementInspectionTest extends LightJavaInspectionTestCase {

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new RedundantEscapeInRegexReplacementInspection();
  }

  public void testRedundantEscapeInRegexReplacement() {
    doTest();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      """
      package java.util.regex;
      public final class Pattern {
          public static Pattern compile(String regex) {
            return null;
          }
          public Matcher matcher(String s) {
            return null;
          }
      }
      """,
      """
      package java.util.regex;
      public final class Matcher {
          public String replaceAll(String r) {
            return null;
          }
          public String replaceFirst(String r) {
            return null;
          }
          public Matcher appendReplacement(StringBuilder sb, String r) {
            return this;
          }
          public Matcher appendReplacement(StringBuffer sb, String r) {
            return this;
          }
      }
      """
    };
  }
}