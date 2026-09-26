// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures

import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.testFramework.SkipInHeadlessEnvironment
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.TestFrameworkUtil
import org.junit.Assume.assumeFalse
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement

class TestFixtureRule : ExternalResource() {
  lateinit var fixture: BareTestFixture
    private set

  override fun apply(base: Statement, description: Description): Statement {
    val superStatement = super.apply(base, description)
    return object : Statement() {
      override fun evaluate() {
        ApplicationManagerEx.runInStressTest<Exception>(TestFrameworkUtil.isStressTest(description.methodName, description.testClass)) {
          superStatement.evaluate()
        }
      }
    }
  }

  override fun before() {
    val headless = TestFrameworkUtil.shouldSkipHeadless() && javaClass.getAnnotation(SkipInHeadlessEnvironment::class.java) != null
    assumeFalse("Class '${javaClass.name}' is skipped because it requires working UI environment", headless)

    val slow = TestFrameworkUtil.SKIP_SLOW && javaClass.getAnnotation(SkipSlowTestLocally::class.java) != null
    assumeFalse("Class '${javaClass.name}' is skipped because it is dog slow", slow)

    fixture = IdeaTestFixtureFactory.getFixtureFactory().createBareFixture().apply { setUp() }
  }

  override fun after() {
    fixture.tearDown()
  }
}
