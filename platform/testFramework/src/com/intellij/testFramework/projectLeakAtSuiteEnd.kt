// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.common.assertDisposerEmpty
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly

/**
 * Runs the project leak check at the end of a test suite: `_LastInSuiteTest.testProjectLeak`.
 *
 * The checks of [TestApplicationManager.testProjectLeak] need an application. Without one, the check only asserts that the Disposer
 * tree is empty. A JUnit 5 test application is disposed before the end of the suite, and its own disposal checks the project leaks.
 * This path does not touch [TestApplicationManager], because the first use of that class prepares the test environment of an application.
 */
@TestOnly
@Internal
fun checkProjectLeakAtSuiteEnd() {
  if (ApplicationManager.getApplication() != null) {
    TestApplicationManager.testProjectLeak()
  }
  else {
    assertDisposerEmpty()
  }
}
