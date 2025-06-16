// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.application

import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.gradle.testFramework.fixtures.tracker.ExternalSystemListenerLeakTracker
import org.junit.jupiter.api.extension.*

/**
 * @see com.intellij.testFramework.junit5.TestApplication
 */
@ExtendWith(
  GradleTestApplicationLeakTrackerExtension::class
)
@TestApplication
annotation class GradleTestApplication

private class GradleTestApplicationLeakTrackerExtension : BeforeEachCallback, AfterEachCallback {

  companion object {
    private const val LEAK_TRACKERS_KEY = "Gradle application-level leak trackers"
  }

  override fun beforeEach(context: ExtensionContext) {
    context.getStore(ExtensionContext.Namespace.GLOBAL)
      .put(LEAK_TRACKERS_KEY, GradleLeakTrackers())
  }

  override fun afterEach(context: ExtensionContext) {
    context.getStore(ExtensionContext.Namespace.GLOBAL)
      .get(LEAK_TRACKERS_KEY, GradleLeakTrackers::class.java)
      .checkNothingLeaked()
  }

  private class GradleLeakTrackers {

    val externalSystemListenerLeakTracker = ExternalSystemListenerLeakTracker()

    fun checkNothingLeaked() {
      externalSystemListenerLeakTracker.tearDown()
    }
  }
}