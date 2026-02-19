// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.inject

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.InjectionTestFixture
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.util.TestUtils

class GrLeftShiftOperatorInjectionTest : LightGroovyTestCase() {
  override fun setUp() {
    super.setUp()
    injectionTestFixture = InjectionTestFixture(myFixture)
    ModuleRootModificationUtil.updateModel(module, DefaultLightProjectDescriptor::addJetBrainsAnnotationsWithTypeUse)
  }

  private lateinit var injectionTestFixture: InjectionTestFixture

  override fun getBasePath(): String {
    return "${TestUtils.getTestDataPath()}/groovy/inject/"
  }

  fun testMethodCallWithLiteral() = doTest()

  fun testMethodCallWithVariable() = doTest()

  fun testOperatorCallWithLiteral() = doTest()

  fun testOperatorCallWithVariable() = doTest()

  private fun doTest() {
    val name = getTestName(false)
    myFixture.configureByFiles("${name}.groovy", "SomeClass.java")
    injectionTestFixture.assertInjectedLangAtCaret(JavaLanguage.INSTANCE.id)
    injectionTestFixture.assertInjectedContent("int x = 1;")
  }
}