// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.LightProjectDescriptor
import com.siyeh.ig.LightJavaInspectionTestCase
import org.jetbrains.annotations.NotNull

@SuppressWarnings(["ResultOfMethodCallIgnored", "UnusedReturnValue"])
class IgnoreResultOfCallInspectionTest extends LightJavaInspectionTestCase {

  @Override
  protected LocalInspectionTool getInspection() {
    return new IgnoreResultOfCallInspection()
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_14
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return [
      """package java.util.regex;

public class Pattern {
  public static Pattern compile(String regex) {return null;}

  public Matcher matcher(CharSequence input) {return null;}
}""",

      """package java.util.regex;

public class Matcher {
  public boolean find() {return true;}
}""",

      """package javax.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.annotation.meta.When;

@Documented
@Target( { ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE,
        ElementType.PACKAGE })
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckReturnValue {
    When when() default When.ALWAYS;
}""",

      """package com.google.errorprone.annotations;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
@Documented
 @Target(value={METHOD,CONSTRUCTOR,TYPE,PACKAGE})
 @Retention(value=RUNTIME)
public @interface CheckReturnValue {}
""",

      """package org.assertj.core.util;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
@Target(value={CONSTRUCTOR,METHOD,PACKAGE,TYPE})
@Retention(value=CLASS)
public @interface CheckReturnValue {}
""",

      """package a;
 public @interface CheckReturnValue {}""",

      """package com.google.errorprone.annotations;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
@Target(value={ElementType.METHOD, ElementType.TYPE})
@Retention(value=RetentionPolicy.CLASS)
public @interface CanIgnoreReturnValue {}""",

      """package org.assertj.core.util;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
@Target(value={CONSTRUCTOR,METHOD,PACKAGE,TYPE})
@Retention(value=CLASS)
public @interface CanIgnoreReturnValue{} """,
      
      """package org.apache.commons.lang3;
public class Validate {
  public native static <T> T notNull(T object);
}"""
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

  void testCanIgnoreReturnValue2() {
    doTest("""
class TestClass {

    public void m() {
        var javax = new Javax();
        javax./*Result of 'Javax.unannotated()' is ignored*/unannotated/**/();
        javax.assertJ();
        javax.errorProne();

        var errorProne = new ErrorProne();
        errorProne./*Result of 'ErrorProne.unannotated()' is ignored*/unannotated/**/();
        errorProne.assertJ();
        errorProne.errorProne();

        var assertJ = new AssertJ();
        assertJ./*Result of 'AssertJ.unannotated()' is ignored*/unannotated/**/();
        assertJ.assertJ();
        assertJ.errorProne();

    }

    @javax.annotation.CheckReturnValue
    public static class Javax {
        int unannotated() {
            return 3;
        }

        @org.assertj.core.util.CanIgnoreReturnValue
        int assertJ() {
            return 3;
        }

        @com.google.errorprone.annotations.CanIgnoreReturnValue
        int errorProne() {
            return 3;
        }
    }

    @com.google.errorprone.annotations.CheckReturnValue
    public static class ErrorProne {
        int unannotated() {
            return 3;
        }

        @org.assertj.core.util.CanIgnoreReturnValue
        int assertJ() {
            return 3;
        }

        @com.google.errorprone.annotations.CanIgnoreReturnValue
        int errorProne() {
            return 3;
        }
    }

    @org.assertj.core.util.CheckReturnValue
    public static class AssertJ {
        int unannotated() {
            return 3;
        }

        @org.assertj.core.util.CanIgnoreReturnValue
        int assertJ() {
            return 3;
        }

        @com.google.errorprone.annotations.CanIgnoreReturnValue
        int errorProne() {
            return 3;
        }
    }
}
""")
  }

  void testCanIgnoreReturnValue3() {
    doTest("""
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
@CheckReturnValue
class Test {
    static int lookAtMe() { return 1; }
    @CanIgnoreReturnValue
    static int ignoreMe() { return 2; }
    void run() {
        /*Result of 'Test.lookAtMe()' is ignored*/lookAtMe/**/(); // <- inspection 
        ignoreMe(); // <- also inspection
    }
}""")
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

  void testPureMethodReturningThis() {
    doTest """
import org.jetbrains.annotations.Contract;

class Test {
  boolean closed;
  
  @Contract(pure=true, value="->this")
  Test validate() {
    if(closed) throw new IllegalStateException();
    return this;
  }
  
  void test() {
    validate();
    System.out.println("ok");
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

  void testParamContract() {
    doTest """class X{
public static int atLeast(int min, int actual, String varName) {
    if (actual < min) throw new IllegalArgumentException('\\'' + varName + " must be at least " + min + ": " + actual);
    return actual;
  }

  public byte[] getMemory(int address, int length) {
    atLeast(0, address, "address");
    atLeast(1, length, "length");

    return new byte[length];
  }
}"""
  }
  
  void testInForExpressionList() {
    //noinspection StatementWithEmptyBody
    doTest """class X {
  void test(String s) {
    for(int i=0; i<10; i++, s./*Result of 'String.trim()' is ignored*/trim/**/()) {}
  }
}"""
  }

  void testInSwitchExpression() {
    //noinspection SwitchStatementWithTooFewBranches
    doTest """class X {
  String test(String s) {
    return switch(s) {
      default -> s.trim();
    };
  }
}"""
  }
  
  void testOptionalGet() {
    //noinspection ALL
    doTest """class X {
  void test(java.util.Optional<String> opt) {
    opt.get();
    if (opt.isPresent()) opt./*Result of 'Optional.get()' is ignored*/get/**/();
  }
}"""
  }
  
  void testCommonsLang3NotNull() {
    doTest """import org.apache.commons.lang3.Validate;
class X{
  void test(String foo) {
    if (foo == null) return;
    Validate.notNull(foo);
  }
}"""
  }
  
  void testVoidType() {
    doTest """class X {
  void a() {
    b();
  }
  
  static Void b() {
    return null;
  }
}"""
  }

  void testIgnoreMethodDefinedInSubclasses() {
    doTest("""
import java.util.stream.Stream;
import java.util.*;
abstract class StreamEx<T> implements Stream<T> {
  public <C extends Collection<? super T>> C into(C collection) {
    return null;
  }
  
  public static<T> StreamEx<T> of(T... values) {
    return null; 
  }
  
  public static void main(String[] args) {
    List<Integer> list = new ArrayList<>();
    StreamEx.of(1, 2, 3).into(list);
  }
}""")
  }
}