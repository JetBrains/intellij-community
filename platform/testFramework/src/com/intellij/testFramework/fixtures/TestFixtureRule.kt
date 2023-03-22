// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    ApplicationManagerEx.setInStressTest(TestFrameworkUtil.isStressTest(description.methodName, description.className))
    return super.apply(base, description)
  }

  override fun before() {
    val headless = TestFrameworkUtil.SKIP_HEADLESS && javaClass.getAnnotation(SkipInHeadlessEnvironment::class.java) != null
    assumeFalse("Class '${javaClass.name}' is skipped because it requires working UI environment", headless)

    val slow = TestFrameworkUtil.SKIP_SLOW && javaClass.getAnnotation(SkipSlowTestLocally::class.java) != null
    assumeFalse("Class '${javaClass.name}' is skipped because it is dog slow", slow)

    fixture = IdeaTestFixtureFactory.getFixtureFactory().createBareFixture().also { it.setUp() }
  }

  override fun after() {
    try {
      fixture.tearDown()
    }
    finally {
      ApplicationManagerEx.setInStressTest(false)
    }
  }
}
