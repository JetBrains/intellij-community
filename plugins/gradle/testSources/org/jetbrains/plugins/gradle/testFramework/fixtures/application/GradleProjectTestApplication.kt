// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.application

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.externalSystem.service.remote.ExternalSystemProgressNotificationManagerImpl
import com.intellij.openapi.roots.impl.libraries.LibraryTableTracker
import com.intellij.openapi.vfs.impl.VirtualFilePointerTracker
import com.intellij.testFramework.SdkLeakTracker
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.junit5.impl.TestApplicationExtension
import com.intellij.testFramework.junit5.impl.TypedStoreKey
import com.intellij.testFramework.junit5.impl.TypedStoreKey.Companion.get
import com.intellij.testFramework.junit5.impl.TypedStoreKey.Companion.set
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
@ExtendWith(TestApplicationExtension::class)
@ExtendWith(GradleProjectTestApplicationLeakTracker::class)
annotation class GradleProjectTestApplication

/**
 * @see com.intellij.testFramework.junit5.impl.TestApplicationLeakTrackerExtension
 */
private class GradleProjectTestApplicationLeakTracker : BeforeAllCallback, AfterAllCallback {

  companion object {
    private val LEAK_TRACKERS_KEY = TypedStoreKey.createKey<GradleLeakTrackers>()
  }

  override fun beforeAll(context: ExtensionContext) {
    context[LEAK_TRACKERS_KEY] = GradleLeakTrackers()
  }

  override fun afterAll(context: ExtensionContext) {
    context[LEAK_TRACKERS_KEY]?.checkNothingLeaked()
  }

  private class GradleLeakTrackers {

    val sdkLeakTracker = SdkLeakTracker()
    val libraryLeakTracker = LibraryTableTracker()
    val virtualFilePointerTracker = VirtualFilePointerTracker()

    fun checkNothingLeaked() {
      runAll(
        { ExternalSystemProgressNotificationManagerImpl.assertListenersReleased() },
        { ExternalSystemProgressNotificationManagerImpl.cleanupListeners() },
        { invokeAndWaitIfNeeded { sdkLeakTracker.checkForJdkTableLeaks() } },
        { libraryLeakTracker.assertDisposed() },
        { virtualFilePointerTracker.assertPointersAreDisposed() },
      )
    }
  }
}