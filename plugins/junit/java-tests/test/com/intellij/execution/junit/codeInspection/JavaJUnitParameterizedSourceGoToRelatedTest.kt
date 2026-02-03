// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.junit.testFramework.JUnitParameterizedSourceGoToRelatedTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod

class JavaJUnitParameterizedSourceGoToRelatedTest : JUnitParameterizedSourceGoToRelatedTestBase() {
  fun `test go to method source with explicit name`() {
    myFixture.testGoToRelatedAction(JvmLanguage.JAVA, """
      class Test {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("foo")
        public void a<caret>bc(Integer i) { }
      
        public static List<Integer> foo() {
          return new ArrayList<String>();
        }
      }
    """.trimIndent()) { item ->
      val element = item.element as? PsiMethod
      assertNotNull(element)
      assertEquals("foo", element?.name)
      assertEquals(0, element?.parameters?.size)
    }
  }

  fun `test go to method source without explicit name`() {
    myFixture.testGoToRelatedAction(JvmLanguage.JAVA, """
      class Test {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource
        public void a<caret>bc(Integer i) { }
      
        public static List<Integer> abc() {
          return new ArrayList<String>();
        }
      }
    """.trimIndent()) { item ->
      val element = item.element as? PsiMethod
      assertNotNull(element)
      assertEquals("abc", element?.name)
      assertEquals(0, element?.parameters?.size)
    }
  }

  fun `test go to field source with explicit name`() {
    myFixture.testGoToRelatedAction(JvmLanguage.JAVA, """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.FieldSource;
      import java.util.List;
      import java.util.ArrayList;

      class Test {
        @ParameterizedTest
        @FieldSource("foo")
        public void ab<caret>c(Integer i) { }

        public static List<Integer> foo = new ArrayList<>();
      }
    """.trimIndent()) { item ->
      val element = item.element as? PsiField
      assertNotNull(element)
      assertEquals("foo", element?.name)
      assertTrue(element?.hasModifierProperty("static") == true)
    }
  }

  fun `test go to field source without explicit name`() {
    myFixture.testGoToRelatedAction(JvmLanguage.JAVA, """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.FieldSource;
      import java.util.List;
      import java.util.ArrayList;

      class Test {
        @ParameterizedTest
        @FieldSource
        public void a<caret>bc(Integer i) { }

        public static List<Integer> abc = new ArrayList<>();
      }
    """.trimIndent()) { item ->
      val element = item.element as? PsiField
      assertNotNull(element)
      assertEquals("abc", element?.name)
      assertTrue(element?.hasModifierProperty("static") == true)
    }
  }
}