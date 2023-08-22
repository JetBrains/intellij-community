// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.util.ReadActionCache
import com.intellij.util.ProcessingContext

internal class ReadActionCacheImpl : ReadActionCache {
  private val threadProcessingContext: ThreadLocal<ProcessingContext> = ThreadLocal()

  override val processingContext: ProcessingContext?
    get() {
      threadProcessingContext.get()?.let { return it }
      if (ApplicationManager.getApplication().isWriteIntentLockAcquired) return writeActionProcessingContext
      if (!ApplicationManager.getApplication().isReadAccessAllowed) return null
      threadProcessingContext.set(ProcessingContext())
      return threadProcessingContext.get()
    }

  fun clear() {
    threadProcessingContext.remove()
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