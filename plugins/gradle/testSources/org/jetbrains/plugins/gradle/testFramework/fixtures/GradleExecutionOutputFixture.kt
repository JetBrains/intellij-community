// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures

import com.intellij.testFramework.fixtures.IdeaTestFixture

interface GradleExecutionOutputFixture : IdeaTestFixture {

  fun assertTestEventContain(className: String, methodName: String?)

  fun assertTestEventDoesNotContain(className: String, methodName: String?)

  fun assertTestEventsWasNotReceived()
}