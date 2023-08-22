// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.io

import org.jetbrains.annotations.Contract
import org.junit.AssumptionViolatedException

/**
 * This class should be used to report unavailability of external resources in tests. Such cases shouldn't cause tests to fail, otherwise
 * tests become flaky. When such cases are reported via this class, test runs are marked as ignored. There will be a separate build configuration
 * which will monitor tests ignored this way, and will signal an alert if some test becomes ignored too often. 
 * 
 * Note that in order to make this work in JUnit 3 tests, you need to add `@RunWith(JUnit38AssumeSupportRunner.class)` annotation
 * to the test class.
 */
object ExternalResourcesChecker {
  /**
   * Call this method if a test fails to load some external resource which is necessary to run the test.
   * @param resourceName name of requested resource, e.g. its URL or just a description if the exact URL is unknown
   */
  @JvmStatic
  @Contract("_, _ -> fail")
  fun <T> reportUnavailability(resourceName: String, cause: Throwable?): T {
    val message = "Resource '$resourceName' is not available"
    if (cause != null) {
      throw ExternalResourceNotAvailableException(message, cause)
    }
    else {
      throw ExternalResourceNotAvailableException(message)
    }
  }

  private class ExternalResourceNotAvailableException : AssumptionViolatedException {
    constructor(message: String) : super(message)
    constructor(message: String, t: Throwable) : super(message, t)
  }
}