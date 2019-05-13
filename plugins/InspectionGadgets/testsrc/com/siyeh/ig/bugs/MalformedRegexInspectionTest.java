package com.siyeh.ig.bugs;

import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class MalformedRegexInspectionTest extends LightInspectionTestCase {

  public void testMalformedRegex() {
    doTest();
  }

  @Override
  protected LocalInspectionTool getInspection() {
    return new MalformedRegexInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.util.regex;" +
      "public class Pattern {" +
      "  public static final int LITERAL = 0x10;" +
      "  public static Pattern compile(String regex) {" +
      "    return null;" +
      "  }" +
      "  public static Pattern compile(String regex, int flags) {" +
      "    return null;" +
      "  }" +
      "}"
    };
  }
}
