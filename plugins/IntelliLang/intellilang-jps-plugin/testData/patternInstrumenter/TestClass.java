// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import org.intellij.lang.annotations.Pattern;

public class TestClass {
  public TestClass() { }

  public TestClass(String s1, @Pattern("\\d+") String s2) { }

  @Pattern("\\d+")
  public String simpleReturn() {
    return "-";
  }

  @Pattern("\\d+")
  public String multiReturn(int i) {
    if (i == 1) return "+";
    switch (i) {
      case 2: return "-";
    }
    return "=";
  }

  public void simpleParam(String s1, @Pattern("\\d+") String s2) { }

  public static void staticParam(String s1, @Pattern("\\d+") String s2) { }

  public void longParam(long l, @Pattern("\\d+") String s) { }

  public void doubleParam(double d, @Pattern("\\d+") String s) { }

  public void createNested(String s1, String s2) {
    new Nested(s1, s2);
  }

  public void createInner(String s1, String s2) {
    new Inner(s1, s2);
  }

  public static void bridgeMethod() {
    A a = new B();
    a.get("-");
  }

  public static Object enclosingStatic() {
    return new Object() {
      public void foo(@Pattern("\\d+") String s) { }
    };
  }

  public Object enclosingInstance() {
    return new Object() {
      public boolean foo(@Pattern("\\d+") String s) {
        return s.contains(TestClass.this.toString());
      }
    };
  }

  public static void capturedParam(String s) {
    new TestClass().capturedParamHelper(s, "-", 0, "-");
  }

  private void capturedParamHelper(String s1, String s2, int i3, String s4) {
    class Local {
      final String f;

      Local(@Pattern("\\d+") String s) {
        f = s + s2 + i3 + s4;
      }
    }

    new Local(s1);
  }

  @Pattern("\\d+")
  private @interface Meta { }

  @Meta
  public String metaAnnotation() {
    return "-";
  }

  private static class Nested {
    Nested(String s1, @Pattern("\\d+") String s2) { }
  }

  private class Inner {
    Inner(String s1, @Pattern("\\d+") String s2) { }
  }

  private static class A {
    Object get(String s) { return s; }
  }

  private static class B extends A {
    @Override
    String get(@Pattern("\\d+") String s) { return s; }
  }
}