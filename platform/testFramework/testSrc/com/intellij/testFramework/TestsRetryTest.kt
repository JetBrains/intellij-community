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
 * @see org.jetbrains.intellij.build.impl.TestingTasksImpl.runJUnit5Engine
 *
 */
@Suppress("JUnitMixedFramework")
class TestsRetryTest : TestCase() {
  @Test
  fun testArtificiallyFailingOnFirstTry() {
    val propertyName = "intellij.build.test.retries.failedClasses.file"
    val property = System.getProperty(propertyName, "")
    if (property.isNullOrBlank()) {
      throw AssumptionViolatedException("No re-run property '$propertyName' defined, won't fail")
    }
    val patterns = System.getProperty("intellij.build.test.patterns", "")
    if (!patterns.split(";").contains(this.javaClass.name)) {
      fail("This test is not explicitly listed in 'intellij.build.test.patterns' property, indicating first run, hence failing")
    }
    // don't fail on the second run
  }
}
