// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.workspaceModel.storage.EntityStorage
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicReference

object WorkspaceModelInitialTestContent {
  private val initialContent: AtomicReference<EntityStorage?> = AtomicReference(null)

  @Volatile
  var hasInitialContent = false
    private set

  fun peek(): EntityStorage? = initialContent.get()
  internal fun pop(): EntityStorage? = initialContent.getAndSet(null)

  @TestOnly
  fun <R> withInitialContent(storage: EntityStorage, block: () -> R): R {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      error("For test purposes only")
    }

    if (initialContent.getAndSet(storage) != null) {
      error("Initial content was already registered")
    }

    hasInitialContent = true
    try {
      return block()
    }
    finally {
      hasInitialContent = false
      if (initialContent.getAndSet(null) != null) {
        error("Initial content was not used")
      }
    }
  }
}