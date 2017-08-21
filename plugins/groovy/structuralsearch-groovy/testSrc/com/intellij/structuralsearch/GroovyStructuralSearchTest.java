/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.structuralsearch;

import com.intellij.dupLocator.equivalence.EquivalenceDescriptorProvider;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * @author Eugene.Kudelevsky
 */
public class GroovyStructuralSearchTest extends StructuralSearchTestCase {

  public void test1() {
    String s = "def int x = 0;\n" +
               "def y = 0;\n" +
               "int z = 10;\n" +
               "def int x1";

    doTest(s, "def $x$ = $value$;", 3, 1);
    doTest(s, "def $x$", 4, 3);
    doTest(s, "int $x$", 3, 3);
    doTest(s, "def $x$ = $value$", 3, 1);
    doTest(s, "def $x$ = 0", 2, 1);
    doTest(s, "int $x$ = 0", 1, 1);
    doTest(s, "int $x$ = $value$", 2, 2);
  }

  public void test2() {
    String s = "def void f(int x) {}\n" +
               "def f(int x) {\n" +
               "  System.out.println(\"hello\");\n" +
               "}\n" +
               "def f(def x) {}\n" +
               "void g(x) {}\n" +
               "public def void f(def int y) {\n" +
               "  System.out.println(\"hello\");\n" +
               "}\n" +
               "def int f() {}";

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
    String s = "public class C implements I1, I2 {\n" +
               "  void f() {\n" +
               "    def a = 1;\n" +
               "    def int b = 2;\n" +
               "  }\n" +
               "}";

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
    String s = "for (a in list) {\n" +
               "  println(\"hello1\");\n" +
               "  println(\"hello2\");\n" +
               "}";
    doTest(s, "for ($a$ in $b$) {\n" +
              "  $st1$;\n" +
              "  $st2$\n" +
              "}", 1, 0);
    doTest(s, "for ($a$ in $b$) {\n" +
              "  $st1$;\n" +
              "  $st2$;\n" +
              "}", 1, 1);
    doTest(s, "for ($a$ in $b$) {\n" +
              "  $st1$\n" +
              "  $st2$\n" +
              "}", 1, 0);
    doTest(s, "for ($a$ in $b$) {\n" +
              "  $st$\n" +
              "}", 0, 0);
    doTest(s, "for ($a$ in $b$) {\n" +
              "  '_T*\n" +
              "}", 1, 0);
    doTest(s, "for ($a$ in $b$) {\n" +
              "  '_T+\n" +
              "}", 1, 0);
  }

  public void test5() {
    String s = "class A {\n" +
               "  def f = {\n" +
               "    println('Hello1')\n" +
               "    println('Hello2')\n" +
               "  }\n" +
               "  def f1 = {\n" +
               "    println('Hello')\n" +
               "  }\n" +
               "}";
    doTest(s, "def $name$ = {\n" +
              "  '_T+\n" +
              "}", 0, 0);
    final String old = options.getPatternContext();
    try {
      options.setPatternContext(GroovyStructuralSearchProfile.CLASS_CONTEXT);
      doTest(s, "def $name$ = {\n" +
                    "  '_T+\n" +
                    "}", 2, 2);
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

  private void findAndCheck(String source, String pattern, int expectedOccurences) {
    testMatcher.clearContext();
    assertEquals(expectedOccurences, findMatchesCount(source, pattern, GroovyFileType.GROOVY_FILE_TYPE));
  }
}
