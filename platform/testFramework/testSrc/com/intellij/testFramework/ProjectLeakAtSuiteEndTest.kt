// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.testFramework.common.IsolatedClassLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Runs [checkProjectLeakAtSuiteEnd] in a class loader of its own, which has no application.
 */
class ProjectLeakAtSuiteEndTest {
  @Test
  fun `the check does not prepare the test application when no application exists`() {
    val loader = IsolatedClassLoader()

    loader.callStatic("com.intellij.testFramework.ProjectLeakAtSuiteEndKt", "checkProjectLeakAtSuiteEnd")

    assertThat(loader.hasLoadedClass(TestApplicationManager::class.java.name)).isFalse()
    assertThat(loader.hasLoadedClass("com.intellij.testFramework.TestLoggerFactory")).isFalse()
  }
}
