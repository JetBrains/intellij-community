// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.control

import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.highlighting.grHighlightingFixture
import org.jetbrains.plugins.groovy.util.TestUtils
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class GroovyMultipleMainMethodsInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_5_0)
  }

  private val highlighting by grHighlightingFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "inspections/multipleMainMethods/")
  private val myFixture get() = highlighting.fixture

  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(GroovyMultipleMainMethodsInspection::class.java)
  }

  @Test
  fun testNoMethodInClass(): Unit = highlighting.doTest()

  @Test
  fun testSingleMethodInClass(): Unit = highlighting.doTest()

  @Test
  fun testMultipleMethodsInClass(): Unit = highlighting.doTest()

  @Test
  fun testNoMethodInScript(): Unit = highlighting.doTest()

  @Test
  fun testSingleMethodInScript(): Unit = highlighting.doTest()

  @Test
  fun testMultipleMethodsInScript(): Unit = highlighting.doTest()

  @Test
  fun testNoMethodInNestedClass(): Unit = highlighting.doTest()

  @Test
  fun testSingleMethodInNestedClass(): Unit = highlighting.doTest()

  @Test
  fun testMultipleMethodsInNestedClass(): Unit = highlighting.doTest()

  @Test
  fun testMethodInNestedAndOuterClass(): Unit = highlighting.doTest()

  @Test
  fun testMethodInNestedAndScriptClass(): Unit = highlighting.doTest()
}