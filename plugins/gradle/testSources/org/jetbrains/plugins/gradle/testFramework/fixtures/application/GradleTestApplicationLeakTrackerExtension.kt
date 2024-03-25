// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.application

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.roots.impl.libraries.LibraryTableTracker
import com.intellij.openapi.vfs.impl.VirtualFilePointerTracker
import com.intellij.testFramework.SdkLeakTracker
import com.intellij.testFramework.common.runAll
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.gradle.testFramework.fixtures.tracker.ExternalSystemListenerLeakTracker
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * @see com.intellij.testFramework.junit5.impl.TestApplicationLeakTrackerExtension
 */
@TestOnly
class GradleTestApplicationLeakTrackerExtension : BeforeAllCallback, AfterAllCallback {

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

  @TestOnly
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
