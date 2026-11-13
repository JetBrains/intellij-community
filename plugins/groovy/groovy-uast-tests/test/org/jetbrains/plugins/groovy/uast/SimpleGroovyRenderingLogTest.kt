// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.uast

import com.intellij.platform.uast.testFramework.common.RenderLogTestBase
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test


@TestApplication
class SimpleGroovyRenderingLogTest : RenderLogTestBase {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val uast by groovyUastFixture(projectFixture) { testName, file -> check(testName, file) }

  override fun getTestDataPath(): String = uast.fixture.testDataPath

  @Test
  fun testSimple(): Unit = uast.doTest("SimpleClass.groovy")

  @Test
  fun testClassWithInners(): Unit = uast.doTest("ClassWithInners.groovy")

  @Test
  fun testAnnotations(): Unit = uast.doTest("Annotations.groovy")

}