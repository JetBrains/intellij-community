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
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.util.ArrayUtil;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class JUnit5ParameterizedReferencesTest extends JUnit5CodeInsightTest {
  @Test
  void resolveToSourceMethod() {
    doTest(() -> {
      myFixture.configureByText("ParameterizedTestsDemo.java",
                                "import org.junit.jupiter.params.ParameterizedTest;\n" +
                                "import org.junit.jupiter.params.provider.MethodSource;\n" +
                                "class ParameterizedTestsDemo {\n" +
                                "    @MethodSource(value = {\"cde\", \"ab<caret>c\"})\n" +
                                "    void testWithProvider(String abc) {}\n" +
                                "     private static void abc() {}\n" +
                                "    private static void cde() {}\n" +
                                "}\n");
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
    });
  }
}
