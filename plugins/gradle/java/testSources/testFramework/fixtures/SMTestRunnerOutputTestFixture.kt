// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures

import com.intellij.testFramework.fixtures.IdeaTestFixture

interface SMTestRunnerOutputTestFixture : IdeaTestFixture {

  fun assertTestEventCount(
    name: String,
    suiteStart: Int,
    suiteFinish: Int,
    testStart: Int,
    testFinish: Int,
    testFailure: Int,
    testIgnore: Int,
  )
}