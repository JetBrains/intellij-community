// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.navigation

import com.intellij.openapi.application.readAction
import com.intellij.psi.CommonClassNames
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
class KtLightClassCompatibilityTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun testEnumConstructor() {
    myFixture.addFileToProject("pkg/MyEnum.kt", """
      package pkg
      enum class MyEnum(str1: String, @java.lang.Deprecated str2: String) {
        VAL("+", "-")
      }""".trimMargin())
    checkConstructor("pkg.MyEnum")
  }

  @Test
  fun testInnerClassConstructor() {
    myFixture.addFileToProject("pkg/MyOuter.kt", """
      package pkg
      class MyOuter() {
        inner class MyInner(str1: String, @java.lang.Deprecated str2: String)
      }""".trimMargin())
    checkConstructor("pkg.MyOuter.MyInner")
  }

  @Test
  fun testNestedClassConstructor() {
    myFixture.addFileToProject("pkg/MyOuter.kt", """
      package pkg
      class MyOuter() {
        class MyNested(str1: String, @java.lang.Deprecated str2: String)
      }""".trimMargin())
    checkConstructor("pkg.MyOuter.MyNested")
  }

  private fun checkConstructor(className: String): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    readAction {
      val result = myFixture.findClass(className)
      val constructors = result.constructors
      assertEquals(1, constructors.size)
      val parameters = constructors[0].parameters
      assertEquals(2, parameters.size)
      assertEquals("str1", parameters[0].name)
      assertEquals("str2", parameters[1].name)
      assertFalse(parameters[0].hasAnnotation(CommonClassNames.JAVA_LANG_DEPRECATED))
      assertTrue(parameters[1].hasAnnotation(CommonClassNames.JAVA_LANG_DEPRECATED))
    }
  }
}