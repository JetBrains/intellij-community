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
      "}",

      "package javax.annotation;\n" +
      "\n" +
      "import java.lang.annotation.Documented;\n" +
      "import java.lang.annotation.ElementType;\n" +
      "import java.lang.annotation.Retention;\n" +
      "import java.lang.annotation.RetentionPolicy;\n" +
      "import java.lang.annotation.Target;\n" +
      "\n" +
      "import javax.annotation.meta.When;\n" +
      "\n" +
      "@Documented\n" +
      "@Target( { ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE,\n" +
      "        ElementType.PACKAGE })\n" +
      "@Retention(RetentionPolicy.RUNTIME)\n" +
      "public @interface CheckReturnValue {\n" +
      "    When when() default When.ALWAYS;\n" +
      "}",

      "package com.google.errorprone.annotations;" +
      "import java.lang.annotation.ElementType;\n" +
      "import java.lang.annotation.Retention;\n" +
      "import java.lang.annotation.RetentionPolicy;\n" +
      "import java.lang.annotation.Target;\n" +
      "@Target(value={ElementType.METHOD, ElementType.TYPE})\n" +
      "@Retention(value=RetentionPolicy.CLASS)\n" +
      "public @interface CanIgnoreReturnValue {}"
    ] as String[]
  }

  public void testCanIgnoreReturnValue() {
    doTest("import com.google.errorprone.annotations.CanIgnoreReturnValue;\n" +
           "import javax.annotation.CheckReturnValue;\n" +
           "\n" +
           "@CheckReturnValue\n" +
           "class Test {\n" +
           "  int lookAtMe() { return 1; }\n" +
           "\n" +
           "  @CanIgnoreReturnValue\n" +
           "  int ignoreMe() { return 2; }\n" +
           "\n" +
           "  void run() {\n" +
           "    /*Result of 'Test.lookAtMe()' is ignored*/lookAtMe/**/(); // Bad!  This line should produce a warning.\n" +
           "    ignoreMe(); // OK.  This line should *not* produce a warning.\n" +
           "  }\n" +
           "}");
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

  public void testReader() {
    doTest("import java.io.Reader;" +
           "import java.io.IOException;" +
           "class U {" +
           "  void m(Reader r) throws IOException {" +
           "    r./*Result of 'Reader.read()' is ignored*/read/**/();" +
           "  }" +
           "}")
  }

  public void testJSR305Annotation() {
    doTest("import javax.annotation.CheckReturnValue;" +
           "class A {" +
           "  @CheckReturnValue" +
           "  static Object a() {" +
           "    return null;" +
           "  }" +
           "  void b() {" +
           "    /*Result of 'A.a()' is ignored*/a/**/();" +
           "  }" +
           "}");
  }

  public void testRandomGetter() {
    doTest("class A {" +
           "  private String name;" +
           "  public String getName() {" +
           "    return name;" +
           "  }" +
           "  void m() {" +
           "    /*Result of 'A.getName()' is ignored*/getName/**/();" +
           "  }" +
           "}")
  }

  public void testJSR305Annotation2() {
    doTest("import javax.annotation.CheckReturnValue;" +
           "@CheckReturnValue " +
           "class A {" +
           "  static Object a() {" +
           "    return null;" +
           "  }" +
           "  void b() {" +
           "    /*Result of 'A.a()' is ignored*/a/**/();" +
           "  }" +
           "}");
  }

  public void testJSR305Annotation3() {
    doTest("import javax.annotation.CheckReturnValue;" +
           "@CheckReturnValue " +
           "class Parent {" +
           "  class A {" +
           "    Object a() {" +
           "      return null;" +
           "    }" +
           "    void b() {" +
           "      /*Result of 'A.a()' is ignored*/a/**/();" +
           "    }" +
           "  }" +
           "}");
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