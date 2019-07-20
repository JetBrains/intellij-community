// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.kotlin.navigation

import com.intellij.psi.CommonClassNames
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class KtLightClassCompatibilityTest : LightJavaCodeInsightFixtureTestCase() {
  fun testEnumConstructor() {
    myFixture.addFileToProject("pkg/MyEnum.kt", """
      package pkg
      enum class MyEnum(str1: String, @java.lang.Deprecated str2: String) {
        VAL("+", "-")
      }""".trimMargin())
    checkConstructor("pkg.MyEnum")
  }

  fun testInnerClassConstructor() {
    myFixture.addFileToProject("pkg/MyOuter.kt", """
      package pkg
      class MyOuter() {
        inner class MyInner(str1: String, @java.lang.Deprecated str2: String)
      }""".trimMargin())
    checkConstructor("pkg.MyOuter.MyInner")
  }

  fun testNestedClassConstructor() {
    myFixture.addFileToProject("pkg/MyOuter.kt", """
      package pkg
      class MyOuter() {
        class MyNested(str1: String, @java.lang.Deprecated str2: String)
      }""".trimMargin())
    checkConstructor("pkg.MyOuter.MyNested")
  }

  private fun checkConstructor(className: String) {
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