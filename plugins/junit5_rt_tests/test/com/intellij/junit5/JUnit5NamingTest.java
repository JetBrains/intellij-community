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
package com.intellij.junit5;

import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.psi.PsiJavaFile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

public class JUnit5NamingTest extends JUnit5CodeInsightTest {
  @Test
  void arrayParameters() {
    doTest(() -> {
      PsiJavaFile file = (PsiJavaFile)myFixture.configureByText("MyTest.java", "import org.junit.jupiter.api.*;" +
                                                                               "class MyTest {" +
                                                                               "  @Test void foo(int[] i); \n" +
                                                                               "  @Test void foo(int i); \n" +
                                                                               "  @Test void foo(String[] i); \n" +
                                                                               "  @Test void foo(String i); \n" +
                                                                               "  @Test void foo(Foo[] i); \n" +
                                                                               "  @Test void foo(Foo i); \n" +
                                                                               "  static class Foo {}" +
                                                                               "}");
      String[] methodPresentations =
        Arrays.stream(file.getClasses()[0].getMethods())
          .map(method -> JUnitConfiguration.Data.getMethodPresentation(method))
          .toArray(String[]::new);
      Assertions.assertArrayEquals(new String[] {"foo(int[])",
                                     "foo(int)", 
                                     "foo([Ljava.lang.String;)", 
                                     "foo(java.lang.String)", 
                                     "foo([LMyTest$Foo;)", 
                                     "foo(MyTest$Foo)"}, 
                                   methodPresentations, 
                                   Arrays.toString(methodPresentations));
    });
  }
}
