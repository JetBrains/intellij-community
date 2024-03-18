// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.junit.testFramework.JUnitParameterizedSourceGoToRelatedTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
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
}