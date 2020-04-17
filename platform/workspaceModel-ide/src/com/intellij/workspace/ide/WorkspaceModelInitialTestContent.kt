// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.workspace.api.TypedEntityStorage
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object WorkspaceModelInitialTestContent {
  private val initialContent: AtomicReference<TypedEntityStorage?> = AtomicReference(null)
  @Volatile
  internal var hasInitialContent = false
    private set

  internal fun peek(): TypedEntityStorage? = initialContent.get()
  internal fun pop(): TypedEntityStorage? = initialContent.getAndSet(null)

  @TestOnly
  fun <R> withInitialContent(storage: TypedEntityStorage, block: () -> R): R {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      error("For test purposes only")
    }

    if (initialContent.getAndSet(storage) != null) {
      error("Initial content was already registered")
    }

    hasInitialContent = true
    try {
      return block()
    } finally {
      hasInitialContent = false
      if (initialContent.getAndSet(null) != null) {
        error("Initial content was not used")
      }
    }
  }
}