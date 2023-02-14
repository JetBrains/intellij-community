// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.testFramework.common.ThreadLeakTracker
import com.intellij.util.ui.EDT
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

@TestOnly
internal class ThreadLeakTrackerExtension : BeforeEachCallback, AfterEachCallback {

  companion object {
    private const val threadsBeforeKey = "threads before test started"
  }

  override fun beforeEach(context: ExtensionContext) {
    context.getStore(ExtensionContext.Namespace.GLOBAL).put(threadsBeforeKey, ThreadLeakTracker.getThreads())
  }

  override fun afterEach(context: ExtensionContext) {
    val threadsBefore = context.getStore(ExtensionContext.Namespace.GLOBAL).typedGet<Map<String, Thread>>(threadsBeforeKey)
    Assertions.assertFalse(EDT.isCurrentThreadEdt())
    ThreadLeakTracker.awaitQuiescence()
    ThreadLeakTracker.checkLeak(threadsBefore)
  }
}
