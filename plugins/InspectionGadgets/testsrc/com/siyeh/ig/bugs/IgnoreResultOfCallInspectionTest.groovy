/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.bugs

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.LightProjectDescriptor
import com.siyeh.ig.LightInspectionTestCase
import org.jetbrains.annotations.NotNull

@SuppressWarnings(["ResultOfMethodCallIgnored", "UnusedReturnValue"])
class IgnoreResultOfCallInspectionTest extends LightInspectionTestCase {

  @Override
  protected LocalInspectionTool getInspection() {
    return new IgnoreResultOfCallInspection()
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8
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

      "package a;\n" +
      " public @interface CheckReturnValue {}",

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

  void testCanIgnoreReturnValue() {
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
           "}")
  }

  void testCustomCheckReturnValue() {
    doTest("import a.CheckReturnValue;\n" +
           "\n" +
           "class Test {\n" +
           "  @CheckReturnValue\n" +
           "  int lookAtMe() { return 1; }\n" +
           "\n" +
           "  void run() {\n" +
           "    /*Result of 'Test.lookAtMe()' is ignored*/lookAtMe/**/();\n" +
           "  }\n" +
           "}")
  }

  void testObjectMethods() {
    doTest("class C {\n" +
           "  void foo(Object o, String s) {\n" +
           "    o./*Result of 'Object.equals()' is ignored*/equals/**/(s);\n" +
           "  }\n" +
           "}\n")
  }

  void testMatcher() {
    doTest("class C {\n" +
           "  void matcher() {\n" +
           "    final java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(\"baaaa\");\n" +
           "    final java.util.regex.Matcher matcher = pattern.matcher(\"babaaaaaaaa\");\n" +
           "    matcher./*Result of 'Matcher.find()' is ignored*/find/**/();\n" +
           "    matcher.notifyAll();\n" +
           "  }\n" +
           "}\n")
  }

  void testReader() {
    doTest("import java.io.Reader;" +
           "import java.io.IOException;" +
           "class U {" +
           "  void m(Reader r) throws IOException {" +
           "    r./*Result of 'Reader.read()' is ignored*/read/**/();" +
           "  }" +
           "}")
  }

  void testJSR305Annotation() {
    doTest("import javax.annotation.CheckReturnValue;" +
           "class A {" +
           "  @CheckReturnValue" +
           "  static Object a() {" +
           "    return null;" +
           "  }" +
           "  void b() {" +
           "    /*Result of 'A.a()' is ignored*/a/**/();" +
           "  }" +
           "}")
  }

  void testRandomGetter() {
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

  void testJSR305Annotation2() {
    doTest("import javax.annotation.CheckReturnValue;" +
           "@CheckReturnValue " +
           "class A {" +
           "  static Object a() {" +
           "    return null;" +
           "  }" +
           "  void b() {" +
           "    /*Result of 'A.a()' is ignored*/a/**/();" +
           "  }" +
           "}")
  }

  void testJSR305Annotation3() {
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
           "}")
  }

  void testInference() {
    Registry.get("ide.ignore.call.result.inspection.honor.inferred.pure").setValue(true, getTestRootDisposable())
    doTest("""class Test {
  private static <T> T checkNotNull(T reference) {
    if (reference == null) {
      throw new NullPointerException();
    }
    return reference;
  }
  
  private static String twice(String s) {
    return s+s;
  }
  
  void test(String string) {
    checkNotNull(string);
    /*Result of 'Test.twice()' is ignored*/twice/**/("foo");
    /*Result of 'Test.twice()' is ignored*/twice/**/("bar");
  }
}
""")
  }

  void testPureMethod() {
    doTest """
import org.jetbrains.annotations.Contract;

class Util {
  @Contract(pure=true)
  static Object util() { return null; }
}

class C {
  static {
    Util./*Result of 'Util.util()' is ignored*/util/**/();
  }
}
"""
  }

  void testPureMethodInVoidFunctionalExpression() {
    doTest """
import org.jetbrains.annotations.Contract;

class Util {
  @Contract(pure=true)
  static Object util() { return null; }
}

class C {
  static {
    Runnable r = () -> Util./*Result of 'Util.util()' is ignored*/util/**/();
    Runnable r1 = Util::/*Result of 'Util.util()' is ignored*/util/**/;
  }
}
"""
  }

  void testStream() {
    doTest """
import java.util.stream.*;
import java.util.*;

class Test {
  void test() {
    Stream.of("a", "b", "c")./*Result of 'Stream.collect()' is ignored*/collect/**/(Collectors.toSet());
    Stream.of("a", "b", "c")./*Result of 'Stream.collect()' is ignored*/collect/**/(Collectors.toCollection(() -> new ArrayList<>()));
    Set<String> result = new TreeSet<>();
    result.add("-");
    // violates stream principles, but quite widely used (IDEA-164501);
    // this inspection should not warn here; probably some other inspection should suggest better option 
    Stream.of("a", "b", "c").collect(Collectors.toCollection(() -> result));
  }
}"""
  }

  void testPattern() {
    doTest """
import java.util.regex.Pattern;
import java.util.Set;

class Test {
  void test(Set<String> names) {
    names.forEach(Pattern::/*Result of 'Pattern.compile()' is ignored*/compile/**/);
  }
}
"""
  }

  void testPatternCaught() {
    doTest """
import java.util.regex.*;
import java.util.Set;

class Test {
  void test(Set<String> names) {
    try {
      names.forEach(Pattern::compile);
    }
    catch (PatternSyntaxException e) {
      throw new RuntimeException("Pattern error", e);
    }
  }
}
"""
  }

  void testOptionalOrElseThrow() {
    doTest """
import java.util.Optional;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
class Test {
  void test(Optional<String> opt) {
    opt.orElseThrow(RuntimeException::new);
  }
}"""
  }
}