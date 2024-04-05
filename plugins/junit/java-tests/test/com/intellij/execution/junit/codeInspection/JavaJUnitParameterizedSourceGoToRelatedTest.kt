// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  fun `test go to enum source value`() {
    myFixture.testGoToPsiElement(JvmLanguage.JAVA, """
      enum Foo { AAA, BBB }
      
      public class ExampleTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(
          value = F<caret>oo.class
        )
        void valid() {}
      }
    """.trimIndent()) { item ->
      assertEquals("enum Foo { AAA, BBB }", item.text)
    }
  }

  fun `test go to enum source single name`() {
    myFixture.testGoToPsiElement(JvmLanguage.JAVA, """
      enum Foo { AAA, BBB }
      
      public class ExampleTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(
          value = Foo.class, 
          names = "B<caret>BB"
        )
        void valid() {}
      }
    """.trimIndent()) { item ->
      assertEquals("enum Foo { AAA, BBB }", item.parent.text)
      assertEquals("BBB", item.text)
    }
  }

  fun `test go to enum source multiple names`() {
    myFixture.testGoToPsiElement(JvmLanguage.JAVA, """
      enum Foo { AAA, BBB }
      
      public class ExampleTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(
          value = Foo.class, 
          names = {"AAA", "B<caret>BB"}
        )
        void valid() {}
      }
    """.trimIndent()) { item ->
      assertEquals("enum Foo { AAA, BBB }", item.parent.text)
      assertEquals("BBB", item.text)
    }
  }
}