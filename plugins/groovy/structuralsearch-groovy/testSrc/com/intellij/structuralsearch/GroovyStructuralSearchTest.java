// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.dupLocator.equivalence.EquivalenceDescriptorProvider;
import com.intellij.structuralsearch.groovy.GroovyStructuralSearchProfile;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * @author Eugene.Kudelevsky
 */
public class GroovyStructuralSearchTest extends StructuralSearchTestCase {

  public void test1() {
    String s = """
      def int x = 0;
      def y = 0;
      int z = 10;
      def int x1""";

    doTest(s, "def $x$ = $value$;", 3, 1);
    doTest(s, "def $x$", 4, 3);
    doTest(s, "int $x$", 3, 3);
    doTest(s, "def $x$ = $value$", 3, 1);
    doTest(s, "def $x$ = 0", 2, 1);
    doTest(s, "int $x$ = 0", 1, 1);
    doTest(s, "int $x$ = $value$", 2, 2);
  }

  public void test2() {
    String s = """
      def void f(int x) {}
      def f(int x) {
        System.out.println("hello");
      }
      def f(def x) {}
      void g(x) {}
      public def void f(def int y) {
        System.out.println("hello");
      }
      def int f() {}""";

    doTest(s, "def $f$($param$)", 5, 2);
    doTest(s, "def $f$($param$) {}", 3, 1);
    doTest(s, "void $f$($param$) {}", 2, 2);
    doTest(s, "void $f$(def x)", 2, 0);
    doTest(s, "def $f$(def x)", 4, 1);
    doTest(s, "void $f$(def $x$)", 3, 0);
    doTest(s, "void $f$(int $x$)", 2, 2);
    doTest(s, "def $f$(int $x$)", 3, 1);
    doTest(s, "def g($param$)", 1, 0);
    doTest(s, "def '_T1('_T2*)", 6, 2);

    // a problem with default eq is that ; is not part of statement
    doTest(s, "def '_T1('_T2*) {'_T3+}", 2, 0);
    doTest(s, "def '_T1('_T2*) {'_T3*}", 6, 1);
  }

  public void test3() {
    String s = """
      public class C implements I1, I2 {
        void f() {
          def a = 1;
          def int b = 2;
        }
      }""";

    doTest(s, "class $name$", 1, 1);
    doTest(s, "class $name$ implements I1, I2", 1, 1);
    doTest(s, "class $name$ implements $interface$", 1, 0);
    doTest(s, "class '_T1 implements '_T2*", 1, 1);
    doTest(s, "class '_T1 implements '_T2+", 1, 1);
    doTest(s, "class $name$ implements I2, I1", 1, 0);
    doTest(s, "class C implements I1, I2 {}", 1, 0);
    doTest(s, "def a = 1;\n def b = 2;", 1, 0);
    doTest(s, "def a = 1\n def b = 2", 1, 0);
  }

  public void test4() {
    String s = """
      for (a in list) {
        println("hello1");
        println("hello2");
      }""";
    doTest(s, """
      for ($a$ in $b$) {
        $st1$;
        $st2$
      }""", 1, 0);
    doTest(s, """
      for ($a$ in $b$) {
        $st1$;
        $st2$;
      }""", 1, 1);
    doTest(s, """
      for ($a$ in $b$) {
        $st1$
        $st2$
      }""", 1, 0);
    doTest(s, """
      for ($a$ in $b$) {
        $st$
      }""", 0, 0);
    doTest(s, """
      for ($a$ in $b$) {
        '_T*
      }""", 1, 0);
    doTest(s, """
      for ($a$ in $b$) {
        '_T+
      }""", 1, 0);
  }

  public void test5() {
    String s = """
      class A {
        def f = {
          println('Hello1')
          println('Hello2')
        }
        def f1 = {
          println('Hello')
        }
      }""";
    doTest(s, """
      def $name$ = {
        '_T+
      }""", 0, 0);
    final PatternContext old = options.getPatternContext();
    try {
      options.setPatternContext(GroovyStructuralSearchProfile.CLASS_CONTEXT);
      doTest(s, """
        def $name$ = {
          '_T+
        }""", 2, 2);
    }
    finally {
      options.setPatternContext(old);
    }
  }

  private void doTest(String source, String pattern, int expectedOccurrences, int expectedWithDefaultEquivalence) {
    findAndCheck(source, pattern, expectedOccurrences);
    try {
      EquivalenceDescriptorProvider.ourUseDefaultEquivalence = true;
      findAndCheck(source, pattern, expectedWithDefaultEquivalence);
    }
    finally {
      EquivalenceDescriptorProvider.ourUseDefaultEquivalence = false;
    }
  }

  private void findAndCheck(String source, String pattern, int expectedOccurrences) {
    assertEquals(expectedOccurrences, findMatchesCount(source, pattern, GroovyFileType.GROOVY_FILE_TYPE));
  }
}
