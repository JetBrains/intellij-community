// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures

import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.SkipInHeadlessEnvironment
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.TestFrameworkUtil
import org.junit.Assume
import org.junit.rules.ExternalResource

class TestFixtureRule : ExternalResource() {

  lateinit var fixture: BareTestFixture
    private set

  override fun before() {
    ApplicationManagerEx.setInStressTest(TestFrameworkUtil.isPerformanceTest(null, javaClass.name))
    val headless = TestFrameworkUtil.SKIP_HEADLESS && javaClass.getAnnotation(SkipInHeadlessEnvironment::class.java) != null
    Assume.assumeFalse("Class '${javaClass.name}' is skipped because it requires working UI environment", headless)
    val slow = TestFrameworkUtil.SKIP_SLOW && javaClass.getAnnotation(SkipSlowTestLocally::class.java) != null
    Assume.assumeFalse("Class '${javaClass.name}' is skipped because it is dog slow", slow)
    fixture = IdeaTestFixtureFactory.getFixtureFactory().createBareFixture().also { it.setUp() }
    Disposer.register(fixture.testRootDisposable) { ApplicationManagerEx.setInStressTest(false) }
  }

  override fun after() {
    fixture.tearDown()
  }
}