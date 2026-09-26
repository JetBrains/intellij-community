// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.roots.impl.libraries.LibraryTableTracker
import com.intellij.openapi.vfs.impl.VirtualFilePointerTracker
import com.intellij.testFramework.SdkLeakTracker
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.junit5.impl.TypedStoreKey.Companion.get
import com.intellij.testFramework.junit5.impl.TypedStoreKey.Companion.set
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.jvm.optionals.getOrNull

@TestOnly
internal class TestApplicationLeakTrackerExtension : BeforeEachCallback, AfterEachCallback, BeforeAllCallback, AfterAllCallback {

  companion object {
    private val leakTrackersKey = TypedStoreKey.createKey<LeakTrackers>()
  }

  override fun beforeEach(context: ExtensionContext) {
    if (context.testInstanceLifecycle.getOrNull() != TestInstance.Lifecycle.PER_CLASS) {
      context[leakTrackersKey] = LeakTrackers()
    }
  }

  override fun afterEach(context: ExtensionContext) {
    if (context.testInstanceLifecycle.getOrNull() != TestInstance.Lifecycle.PER_CLASS) {
      context[leakTrackersKey]?.checkNothingLeaked()
    }
  }

  override fun beforeAll(context: ExtensionContext) {
    if (context.testInstanceLifecycle.getOrNull() == TestInstance.Lifecycle.PER_CLASS) {
      context[leakTrackersKey] = LeakTrackers()
    }
  }

  override fun afterAll(context: ExtensionContext) {
    if (context.testInstanceLifecycle.getOrNull() == TestInstance.Lifecycle.PER_CLASS) {
      context[leakTrackersKey]?.checkNothingLeaked()
    }
  }

  @TestOnly
  private class LeakTrackers {

    val sdkLeakTracker = SdkLeakTracker()
    val libraryLeakTracker = LibraryTableTracker()
    val virtualFilePointerTracker = VirtualFilePointerTracker()

    fun checkNothingLeaked() {
      runAll(
        { invokeAndWaitIfNeeded { sdkLeakTracker.checkForJdkTableLeaks() } },
        { libraryLeakTracker.assertDisposed() },
        { virtualFilePointerTracker.assertPointersAreDisposed() },
      )
    }
  }
}
