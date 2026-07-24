// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.testFramework.common.ThreadLeakTracker
import com.intellij.testFramework.junit5.impl.TypedStoreKey.Companion.get
import com.intellij.testFramework.junit5.impl.TypedStoreKey.Companion.set
import com.intellij.util.ui.EDT
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.jvm.optionals.getOrNull

@TestOnly
internal class ThreadLeakTrackerExtension : BeforeEachCallback, AfterEachCallback, BeforeAllCallback, AfterAllCallback {

  companion object {
    private val threadsBeforeKey = TypedStoreKey.createKey<Map<String, Thread>>()

    /**
     * Re-capture the PER_CLASS baseline after a class-level fixture has started long-lived shared
     * threads (e.g. the shared test application and its app-level services). Because this extension
     * is ServiceLoader-autodetected, its beforeAll runs before declarative fixtures create the app,
     * so the initial baseline misses app-owned threads. The fixture that created them calls this so
     * those threads are part of the baseline and not reported as leaks.
     */
    @TestOnly
    internal fun refreshPerClassThreadLeakBaseline(context: ExtensionContext) {
      if (context.testInstanceLifecycle.getOrNull() != TestInstance.Lifecycle.PER_CLASS) return
      if (context[threadsBeforeKey] == null) return
      val before = ThreadLeakTracker.getThreads()
      context[threadsBeforeKey] = before
    }
  }

  override fun beforeEach(context: ExtensionContext) {
    if (context.testInstanceLifecycle.getOrNull() != TestInstance.Lifecycle.PER_CLASS) {
      context[threadsBeforeKey] = ThreadLeakTracker.getThreads()
    }
  }

  override fun afterEach(context: ExtensionContext) {
    if (context.testInstanceLifecycle.getOrNull() != TestInstance.Lifecycle.PER_CLASS) {
      val threadsBefore = context[threadsBeforeKey] ?: return
      Assertions.assertFalse(EDT.isCurrentThreadEdt())
      ThreadLeakTracker.awaitQuiescence()
      ThreadLeakTracker.checkLeak(threadsBefore)
    }
  }

  override fun beforeAll(context: ExtensionContext) {
    if (context.testInstanceLifecycle.getOrNull() == TestInstance.Lifecycle.PER_CLASS) {
      // Provisional baseline: app-owned threads created by later declarative fixtures aren't here yet.
      // TestApplicationExtension re-captures via refreshPerClassThreadLeakBaseline once the app is up.
      context[threadsBeforeKey] = ThreadLeakTracker.getThreads()
    }
  }

  override fun afterAll(context: ExtensionContext) {
    if (context.testInstanceLifecycle.getOrNull() == TestInstance.Lifecycle.PER_CLASS) {
      val threadsBefore = context[threadsBeforeKey] ?: return
      Assertions.assertFalse(EDT.isCurrentThreadEdt())
      ThreadLeakTracker.awaitQuiescence()
      ThreadLeakTracker.checkLeak(threadsBefore)
    }
  }
}
