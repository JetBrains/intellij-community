// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests

import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.reporting.FileEntry
import org.junit.platform.engine.reporting.ReportEntry
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan

/**
 * A [TestExecutionListener] that intercepts calls to [executionFinished] and replaces the original result with another.
 */
internal class InterceptingTestExecutionListener(
  private val delegate: TestExecutionListener,
  private val interceptor: TestExecutionResultInterceptor,
) : TestExecutionListener {
  override fun executionFinished(testIdentifier: TestIdentifier, testExecutionResult: TestExecutionResult) {
    delegate.executionFinished(testIdentifier, interceptor.intercept(testIdentifier, testExecutionResult))
  }

  override fun testPlanExecutionStarted(testPlan: TestPlan) {
    delegate.testPlanExecutionStarted(testPlan)
  }

  override fun testPlanExecutionFinished(testPlan: TestPlan) {
    delegate.testPlanExecutionFinished(testPlan)
  }

  override fun dynamicTestRegistered(testIdentifier: TestIdentifier) {
    delegate.dynamicTestRegistered(testIdentifier)
  }

  override fun executionSkipped(testIdentifier: TestIdentifier, reason: String) {
    delegate.executionSkipped(testIdentifier, reason)
  }

  override fun executionStarted(testIdentifier: TestIdentifier) {
    delegate.executionStarted(testIdentifier)
  }

  override fun reportingEntryPublished(testIdentifier: TestIdentifier, entry: ReportEntry) {
    delegate.reportingEntryPublished(testIdentifier, entry)
  }

  override fun fileEntryPublished(testIdentifier: TestIdentifier, file: FileEntry) {
    delegate.fileEntryPublished(testIdentifier, file)
  }
}
