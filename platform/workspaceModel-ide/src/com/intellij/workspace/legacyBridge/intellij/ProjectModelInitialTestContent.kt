package com.intellij.workspace.legacyBridge.intellij

import com.intellij.openapi.application.ApplicationManager
import com.intellij.workspace.api.TypedEntityStorage
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicReference

object ProjectModelInitialTestContent {
  private val initialContent: AtomicReference<TypedEntityStorage?> = AtomicReference(null)

  internal fun pop(): TypedEntityStorage? = initialContent.getAndSet(null)

  @TestOnly
  fun withInitialContent(storage: TypedEntityStorage, block: () -> Unit) {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      error("For test purposes only")
    }

    if (initialContent.getAndSet(storage) != null) {
      error("Initial content was already registered")
    }

    try {
      block()
    } finally {
      if (initialContent.getAndSet(null) != null) {
        error("Initial content was not used")
      }
    }
  }
}