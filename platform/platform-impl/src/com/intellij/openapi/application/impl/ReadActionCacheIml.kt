// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.util.ReadActionCache
import com.intellij.util.ProcessingContext

class ReadActionCacheIml: ReadActionCache {
  
  private val application: ApplicationImpl?
    get() = ApplicationManager.getApplication() as? ApplicationImpl

  override val processingContext: ProcessingContext?
    get() = application?.myLock?.processingContext

  override fun <T> allowInWriteAction(supplier: () -> T): T {
    val myLock = application?.myLock ?: return supplier.invoke()
    return myLock.allowProcessingContextInWriteAction(supplier)
  }
  
  override fun allowInWriteAction(runnable: Runnable) {
   allowInWriteAction { runnable.run() }
  }

}