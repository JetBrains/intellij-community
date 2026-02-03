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
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

@TestOnly
internal class TestApplicationLeakTrackerExtension : BeforeEachCallback, AfterEachCallback {

  companion object {
    private val leakTrackersKey = TypedStoreKey.createKey<LeakTrackers>()
  }

  override fun beforeEach(context: ExtensionContext) {
    context[leakTrackersKey] = LeakTrackers()
  }

  override fun afterEach(context: ExtensionContext) {
    context[leakTrackersKey]?.checkNothingLeaked()
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
