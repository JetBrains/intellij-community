// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.inject

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.InjectionTestFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lightGroovyFixture
import org.jetbrains.plugins.groovy.util.TestUtils
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class GrLeftShiftOperatorInjectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1)
  }

  private val myFixture by lightGroovyFixture(projectFixture, "${TestUtils.getAbsoluteTestDataPath()}/groovy/inject/")

  @BeforeEach
  fun setUp() {
    injectionTestFixture = InjectionTestFixture(myFixture)
    ModuleRootModificationUtil.updateModel(myFixture.module, DefaultLightProjectDescriptor::addJetBrainsAnnotationsWithTypeUse)
  }

  private lateinit var injectionTestFixture: InjectionTestFixture

  @Test
  fun testMethodCallWithLiteral(): Unit = doTest()

  @Test
  fun testMethodCallWithVariable(): Unit = doTest()

  @Test
  fun testOperatorCallWithLiteral(): Unit = doTest()

  @Test
  fun testOperatorCallWithVariable(): Unit = doTest()

  private fun doTest() {
    val name = myFixture.getTestName(false)
    myFixture.configureByFiles("${name}.groovy", "SomeClass.java")
    runReadActionBlocking {
      injectionTestFixture.assertInjectedLangAtCaret(JavaLanguage.INSTANCE.id)
      injectionTestFixture.assertInjectedContent("int x = 1;")
    }
  }
}