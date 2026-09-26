// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.util.ReadActionCache
import com.intellij.util.ProcessingContext
import com.intellij.util.concurrency.ThreadingAssertions
import java.util.Optional

internal class ReadActionCacheImpl : ReadActionCache {
  private val threadProcessingContext: ThreadLocal<Optional<ProcessingContext>> = ThreadLocal()

  override val processingContext: ProcessingContext?
    get() {
      threadProcessingContext.get()?.let { return it.orElse(null) }

      val application = ApplicationManager.getApplication()
      if (application.isWriteIntentLockAcquired) return writeActionProcessingContext
      if (!application.isReadAccessAllowed) return null

      val value = ProcessingContext()
      threadProcessingContext.set(Optional.of<ProcessingContext>(value))
      return value
    }

  fun clear() {
    threadProcessingContext.remove()
  }

  override fun disable() {
    ThreadingAssertions.assertReadAccess()

    threadProcessingContext.set(Optional.empty())
  }

  private var writeActionProcessingContext: ProcessingContext? = null

  override fun <T> allowInWriteAction(supplier: () -> T): T {
    return if (!ApplicationManager.getApplication().isWriteIntentLockAcquired || writeActionProcessingContext != null) {
      supplier.invoke()
    }
    else try {
      writeActionProcessingContext = ProcessingContext()
      supplier.invoke()
    }
    finally {
      writeActionProcessingContext = null
    }
  }

  override fun allowInWriteAction(runnable: Runnable) {
    allowInWriteAction(runnable::run)
  }
}