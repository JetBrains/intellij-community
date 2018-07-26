// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.junit.rules.ExternalResource

class FixtureRule(descriptor: LightProjectDescriptor, path: String) : ExternalResource() {

  private val testCase = object : LightGroovyTestCase() {
    override fun getProjectDescriptor() = descriptor
    override fun getBasePath(): String = TestUtils.getTestDataPath() + path
  }

  val fixture: CodeInsightTestFixture get() = testCase.fixture

  override fun before(): Unit = testCase.setUp()

  override fun after(): Unit = testCase.tearDown()
}
