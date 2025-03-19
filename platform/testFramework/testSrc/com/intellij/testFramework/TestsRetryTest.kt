// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import junit.framework.TestCase
import org.junit.AssumptionViolatedException
import org.junit.jupiter.api.Test
import java.io.File

/**
 * This test is used to test tests re-run in our builds.
 *
 * It intentionally mixes JUnit 5 and JUnit 3/4, so it will be run by both runners.
 *
 * @see com.intellij.RetriesImpl.getListenerForOutOfProcessRetry
 * @see com.intellij.TestCaseLoader.explicitTestsFilter
 * @see org.jetbrains.intellij.build.impl.TestingTasksImpl.runJUnit5Engine
 *
 */
@Suppress("JUnitMixedFramework")
class TestsRetryTest : TestCase() {
  @Test
  fun testArtificiallyFailingOnFirstTry() {
    val propertyName = "intellij.build.test.list.file"
    val property = System.getProperty(propertyName, "")
    if (property.isNullOrBlank()) {
      throw AssumptionViolatedException("No re-run property '$propertyName' defined, won't fail")
    }
    val file = File(property)
    if (!file.exists()) {
      fail("Re-run file ('$property') is empty, indicating first run, hence failing")
    }
    val lines = file.readLines()
    if (!lines.any { it.contains(this.javaClass.name) }) {
      fail("This class is not in the re-run list at '$property':\n${lines.joinToString("\n")}")
    }
  }
}
