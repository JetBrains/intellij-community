// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5;

import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.psi.PsiJavaFile;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class JUnitNamingTest extends JUnitCodeInsightTestBase {
  @Test
  void arrayParameters() {
    PsiJavaFile file = (PsiJavaFile)myFixture.configureByText("MyTest.java", """
      import org.junit.jupiter.api.*;class MyTest {  @Test void foo(int[] i);\s
        @Test void foo(int i);\s
        @Test void foo(String[] i);\s
        @Test void foo(String i);\s
        @Test void foo(Foo[] i);\s
        @Test void foo(Foo i);\s
        static class Foo {}}""");
    String[] methodPresentations =
      Arrays.stream(file.getClasses()[0].getMethods())
        .map(method -> JUnitConfiguration.Data.getMethodPresentation(method))
        .toArray(String[]::new);
    assertArrayEquals(new String[]{"foo(int[])",
                        "foo(int)",
                        "foo([Ljava.lang.String;)",
                        "foo(java.lang.String)",
                        "foo([LMyTest$Foo;)",
                        "foo(MyTest$Foo)"},
                      methodPresentations,
                      Arrays.toString(methodPresentations));
  }
}
