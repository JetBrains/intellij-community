// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.application

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.roots.impl.libraries.LibraryTableTracker
import com.intellij.openapi.vfs.impl.VirtualFilePointerTracker
import com.intellij.testFramework.SdkLeakTracker
import com.intellij.testFramework.common.cleanApplicationState
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.junit5.impl.testApplication
import org.jetbrains.plugins.gradle.testFramework.fixtures.tracker.ExternalSystemListenerLeakTracker
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Custom application initializer for the [org.jetbrains.plugins.gradle.testFramework.GradleProjectBaseTestCase].
 * This initializer has adjusted leak listeners to the [org.junit.jupiter.api.BeforeAll] and [org.junit.jupiter.api.AfterAll] bounds.
 * It is necessary because [org.jetbrains.plugins.gradle.testFramework.GradleProjectBaseTestCase]
 * orchestrate Gradle project with different configuration. These project instances are shared between tests.
 * They can be closed and opened at any time between the tests.
 *
 * @see GradleTestApplication
 * @see com.intellij.testFramework.junit5.TestApplication
 */
@Target(AnnotationTarget.CLASS)
@ExtendWith(
  GradleProjectTestApplicationExtension::class,
  GradleProjectTestApplicationLeakTrackerExtension::class,
)
annotation class GradleProjectTestApplication

private class GradleProjectTestApplicationExtension : BeforeAllCallback, AfterAllCallback {
  override fun beforeAll(context: ExtensionContext) {
    context.testApplication().getOrThrow()
  }

  override fun afterAll(context: ExtensionContext) {
    ApplicationManager.getApplication().cleanApplicationState()
  }
}

/**
 * @see com.intellij.testFramework.junit5.impl.TestApplicationLeakTrackerExtension
 */
private class GradleProjectTestApplicationLeakTrackerExtension : BeforeAllCallback, AfterAllCallback {

  companion object {
    private const val LEAK_TRACKERS_KEY = "Gradle application-level leak trackers"
  }

  override fun beforeAll(context: ExtensionContext) {
    context.getStore(ExtensionContext.Namespace.GLOBAL)
      .put(LEAK_TRACKERS_KEY, GradleLeakTrackers())
  }

  override fun afterAll(context: ExtensionContext) {
    context.getStore(ExtensionContext.Namespace.GLOBAL)
      .get(LEAK_TRACKERS_KEY, GradleLeakTrackers::class.java)
      .checkNothingLeaked()
  }

  private class GradleLeakTrackers {

    val sdkLeakTracker = SdkLeakTracker()
    val libraryLeakTracker = LibraryTableTracker()
    val virtualFilePointerTracker = VirtualFilePointerTracker()
    val externalSystemListenerLeakTracker = ExternalSystemListenerLeakTracker()

    fun checkNothingLeaked() {
      runAll(
        { externalSystemListenerLeakTracker.tearDown() },
        { invokeAndWaitIfNeeded { sdkLeakTracker.checkForJdkTableLeaks() } },
        { libraryLeakTracker.assertDisposed() },
        { virtualFilePointerTracker.assertPointersAreDisposed() },
      )
    }
  }
}