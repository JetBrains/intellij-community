package com.siyeh.ig.bugs;

import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightInspectionTestCase;

public class IgnoreResultOfCallInspectionTest extends LightInspectionTestCase {

  @Override
  protected LocalInspectionTool getInspection() {
    return new IgnoreResultOfCallInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return [
      "package java.util.regex; public class Pattern {" +
      "  public static Pattern compile(String regex) {return null;}" +
      "  public Matcher matcher(CharSequence input) {return null;}" +
      "}",
      "package java.util.regex; public class Matcher {" +
      "  public boolean find() {return true;}" +
      "}"
    ] as String[]
  }

  public void testObjectMethods() {
    doTest("class C {\n" +
           "  void foo(Object o, String s) {\n" +
           "    o./*Result of 'Object.equals()' is ignored*/equals/**/(s);\n" +
           "  }\n" +
           "}\n");
  }

  public void testMatcher() {
    doTest("class C {\n" +
           "  void matcher() {\n" +
           "    final java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(\"baaaa\");\n" +
           "    final java.util.regex.Matcher matcher = pattern.matcher(\"babaaaaaaaa\");\n" +
           "    matcher./*Result of 'Matcher.find()' is ignored*/find/**/();\n" +
           "    matcher.notify();\n" +
           "  }\n" +
           "}\n");
  }

  public void testPureMethod() {
    doTest """
import org.jetbrains.annotations.Contract;

class Util {
  @Contract(pure=true)
  static Object util() { return null; }
}

class C {
  {
    Util./*Result of 'Util.util()' is ignored*/util/**/();
  }
}
"""
  }
}