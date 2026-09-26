// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests

import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.launcher.TestIdentifier

/**
 * Replaces a [TestExecutionResult] with a different one.
 */
fun interface TestExecutionResultInterceptor {
  fun intercept(identifier: TestIdentifier, result : TestExecutionResult) : TestExecutionResult
}
