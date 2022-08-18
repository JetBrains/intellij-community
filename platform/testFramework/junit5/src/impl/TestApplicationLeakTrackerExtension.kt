// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.openapi.roots.impl.libraries.LibraryTableTracker
import com.intellij.openapi.vfs.impl.VirtualFilePointerTracker
import com.intellij.testFramework.SdkLeakTracker
import com.intellij.testFramework.common.runAll
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

@TestOnly
internal class TestApplicationLeakTrackerExtension : BeforeEachCallback, AfterEachCallback {

  companion object {
    private const val leakTrackersKey = "application-level leak trackers"
  }

  override fun beforeEach(context: ExtensionContext) {
    context.getStore(ExtensionContext.Namespace.GLOBAL).put(leakTrackersKey, LeakTrackers())
  }

  override fun afterEach(context: ExtensionContext) {
    context.getStore(ExtensionContext.Namespace.GLOBAL).typedGet<LeakTrackers>(leakTrackersKey).checkNothingLeaked()
  }

  @TestOnly
  private class LeakTrackers {

    val sdkLeakTracker = SdkLeakTracker()
    val libraryLeakTracker = LibraryTableTracker()
    val virtualFilePointerTracker = VirtualFilePointerTracker()

    fun checkNothingLeaked() {
      runAll(
        { sdkLeakTracker.checkForJdkTableLeaks() },
        { libraryLeakTracker.assertDisposed() },
        { virtualFilePointerTracker.assertPointersAreDisposed() },
      )
    }
  }
}
