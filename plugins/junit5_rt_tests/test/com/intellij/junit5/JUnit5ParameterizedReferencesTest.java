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

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.util.ArrayUtil;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class JUnit5ParameterizedReferencesTest extends JUnit5CodeInsightTest {
  @Test
  void resolveToSourceMethod() {
    myFixture.configureByText("ResolveToSourceMethod.java",
                              """
                                import org.junit.jupiter.params.ParameterizedTest;
                                import org.junit.jupiter.params.provider.MethodSource;
                                class ParameterizedTestsDemo {
                                    @MethodSource(value = {"cde", "ab<caret>c"})
                                    void testWithProvider(String abc) {}
                                     private static void abc() {}
                                    private static void cde() {}
                                }
                                """);
    PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
    assertNotNull(reference);
    PsiElement resolved = reference.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof PsiMethod);
    assertEquals("abc", ((PsiMethod)resolved).getName());
    String[] variants = Arrays.stream(reference.getVariants())
      .map(o -> o instanceof LookupElement ? ((LookupElement)o).getLookupString() : null).toArray(String[]::new);
    assertTrue(ArrayUtil.contains("abc", variants) && ArrayUtil.contains("cde", variants),
               "Completion variants: " + Arrays.toString(variants));
  }

  @Test
  void resolveEnumSource() {
    addEnumSourceClass();
    addFooEnum();

    myFixture.configureByText("ResolveEnumSource.java",
                              "import org.junit.jupiter.params.ParameterizedTest; " +
                              "import org.junit.jupiter.params.provider.EnumSource; " +
                              "class ResolveEnumSource { " +
                              "    @ParameterizedTest " +
                              "    @EnumSource(value = Foo.class, names = \"AA<caret>A\", mode = EnumSource.Mode.EXCLUDE) " +
                              "    void single() {} " +
                              "}");
    PsiReference reference = myFixture.getReferenceAtCaretPosition();
    assertReference(reference);
  }

  @Test
  void resolveEnumSourceWithUnsupportedMode() {
    addEnumSourceClass();
    addFooEnum();

    myFixture.configureByText("ResolveEnumSource.java",
                              "import org.junit.jupiter.params.ParameterizedTest; " +
                              "import org.junit.jupiter.params.provider.EnumSource; " +
                              "class ResolveEnumSource { " +
                              "    @ParameterizedTest " +
                              "    @EnumSource(value = Foo.class, names = \"AA<caret>A\", mode = EnumSource.Mode.MATCH_ALL) " +
                              "    void single() {} " +
                              "}");
    PsiReference reference = myFixture.getReferenceAtCaretPosition();
    assertNotNull(reference);
    PsiElement resolved = reference.resolve();
    assertNull(resolved);
    assertVariants(reference, false);
  }

  @Test
  void resolveEnumSourceWithDefaultMode() {
    addEnumSourceClass();
    addFooEnum();

    myFixture.configureByText("ResolveEnumSource.java",
                              "import org.junit.jupiter.params.ParameterizedTest; " +
                              "import org.junit.jupiter.params.provider.EnumSource; " +
                              "class ResolveEnumSource { " +
                              "    @ParameterizedTest " +
                              "    @EnumSource(value = Foo.class, names = \"AA<caret>A\") " +
                              "    void single() {} " +
                              "}");
    PsiReference reference = myFixture.getReferenceAtCaretPosition();
    assertReference(reference);
  }

  @Test
  void resolveEnumSourceWithoutMode() {
    addEnumSourceClassWithoutMode();
    addFooEnum();

    myFixture.configureByText("ResolveEnumSourceWithoutMode.java",
                              "import org.junit.jupiter.params.ParameterizedTest; " +
                              "import org.junit.jupiter.params.provider.EnumSource; " +
                              "class ResolveEnumSource { " +
                              "    @ParameterizedTest " +
                              "    @EnumSource(value = Foo.class, names = \"AA<caret>A\") " +
                              "    void single() {} " +
                              "}");
    PsiReference reference = myFixture.getReferenceAtCaretPosition();
    assertReference(reference);
  }

  @Test
  void resolveEnumSourceInvalidValue() {
    addEnumSourceClass();
    addFooEnum();

    myFixture.configureByText("ResolveEnumSourceInvalidValue.java",
                              "import org.junit.jupiter.params.ParameterizedTest; " +
                              "import org.junit.jupiter.params.provider.EnumSource; " +
                              "class ResolveEnumSourceInvalidValue { " +
                              "    @ParameterizedTest " +
                              "    @EnumSource(value = Foo.class, names = \"invalid<caret>value\") " +
                              "    void single() {} " +
                              "}");
    PsiReference reference = myFixture.getReferenceAtCaretPosition();
    assertNotNull(reference);
    PsiElement resolved = reference.resolve();
    assertNull(resolved);
    assertVariants(reference, true);
  }

  private void assertReference(PsiReference reference) {
    assertNotNull(reference);
    PsiElement resolved = reference.resolve();
    assertNotNull(resolved);
    assertTrue(resolved instanceof PsiEnumConstant);
    assertEquals("AAA", ((PsiEnumConstant)resolved).getName());
    assertEquals("Foo", ((PsiEnumConstant)resolved).getContainingClass().getName());
    assertVariants(reference, true);
  }

  private void assertVariants(PsiReference reference, boolean expectEnumValues) {
    final String[] variants = Arrays.stream(reference.getVariants())
      .filter(LookupElement.class::isInstance)
      .map(LookupElement.class::cast)
      .map(LookupElement::getLookupString)
      .toArray(String[]::new);
    final boolean containsEnumValues = ArrayUtil.contains("AAA", variants)
                                       && ArrayUtil.contains("AAX", variants)
                                       && ArrayUtil.contains("BBB", variants);
    if (expectEnumValues) {
      assertTrue(containsEnumValues, "Completion variants: " + Arrays.toString(variants));
    }
    else {
      assertFalse(containsEnumValues, "Completion variants: " + Arrays.toString(variants));
    }
  }

  private void addEnumSourceClass() {
    myFixture.addClass("package org.junit.jupiter.params.provider;" +
                       "public @interface EnumSource {" +
                       " Class<? extends Enum<?>> value();" +
                       " String[] names() default {};" +
                       " Mode mode() default Mode.INCLUDE;" +
                       " enum Mode {" +
                       "  INCLUDE," +
                       "  EXCLUDE," +
                       "  MATCH_ALL," +
                       "  MATCH_ANY }" +
                       "}");
  }

  private void addEnumSourceClassWithoutMode() {
    myFixture.addClass("package org.junit.jupiter.params.provider;" +
                       "public @interface EnumSource {" +
                       " Class<? extends Enum<?>> value();" +
                       " String[] names() default {};" +
                       "}");
  }

  private void addFooEnum() {
    myFixture.addClass("enum Foo { AAA, AAX, BBB }");
  }
}
