// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin.codeInspection

import com.intellij.junit.testFramework.JUnitParameterizedSourceGoToRelatedTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.PathUtil
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import java.io.File

abstract class KotlinJUnitParameterizedSourceGoToRelatedTest : JUnitParameterizedSourceGoToRelatedTestBase(), ExpectedPluginModeProvider {
  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = object : JUnitProjectDescriptor(LanguageLevel.HIGHEST) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      val jar = File(PathUtil.getJarPathForClass(JvmStatic::class.java))
      PsiTestUtil.addLibrary(model, "kotlin-stdlib", jar.parent, jar.name)
    }
  }

  fun `test go to method source with explicit name`() {
    myFixture.testGoToRelatedAction(
      JvmLanguage.KOTLIN, """
      class Test {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("foo")
        fun a<caret>bc(i: Int) { }
      
        companion object {
          @JvmStatic
          fun foo() = listOf(1, 2, 3)
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
    myFixture.testGoToRelatedAction(
      JvmLanguage.KOTLIN, """
      class Test {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource
        fun a<caret>bc(i: Int) { }
      
        companion object {
          @JvmStatic
          fun abc() = listOf(1, 2, 3)
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
    myFixture.testGoToRelatedAction(
      JvmLanguage.KOTLIN, """
      import org.junit.jupiter.params.ParameterizedTest
      import org.junit.jupiter.params.provider.FieldSource

      class Test {
        @ParameterizedTest
        @FieldSource("foo")
        fun ab<caret>c(i: Int) { }

        companion object {
          @JvmField
          val foo = listOf(1, 2, 3)
        }
      }
    """.trimIndent()) { item ->
      val element = item.element as? PsiField
      assertNotNull(element)
      assertEquals("foo", element?.name)
      assertTrue(element?.hasModifierProperty(PsiModifier.STATIC) == true)
    }
  }

  fun `test go to field source without explicit name`() {
    myFixture.testGoToRelatedAction(
      JvmLanguage.KOTLIN, """
      import org.junit.jupiter.params.ParameterizedTest
      import org.junit.jupiter.params.provider.FieldSource

      class Test {
        @ParameterizedTest
        @FieldSource
        fun a<caret>bc(i: Int) { }

        companion object {
          @JvmField
          val abc = listOf(1, 2, 3)
        }
      }
    """.trimIndent()) { item ->
      val element = item.element as? PsiField
      assertNotNull(element)
      assertEquals("abc", element?.name)
      assertTrue(element?.hasModifierProperty(PsiModifier.STATIC) == true)
    }
  }
}