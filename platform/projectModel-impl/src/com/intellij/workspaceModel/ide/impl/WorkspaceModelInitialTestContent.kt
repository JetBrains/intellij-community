// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.PlatformUtils
import com.intellij.workspaceModel.storage.EntityStorageSnapshot
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicReference

object WorkspaceModelInitialTestContent {
  private val initialContent: AtomicReference<EntityStorageSnapshot?> = AtomicReference(null)

  @Volatile
  var hasInitialContent = false
    private set

  fun peek(): EntityStorageSnapshot? = initialContent.get()
  internal fun pop(): EntityStorageSnapshot? = initialContent.getAndSet(null)

  @TestOnly
  fun <R> withInitialContent(storage: EntityStorageSnapshot, block: () -> R): R {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      error("For test purposes only")
    }

    if (initialContent.getAndSet(storage) != null) {
      error("Initial content was already registered")
    }

    val previousPropertyValue = System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY)
    if (storage !== EntityStorageSnapshot.empty()) {
      System.setProperty(PlatformUtils.PLATFORM_PREFIX_KEY, PlatformUtils.IDEA_CE_PREFIX)
    }
    hasInitialContent = true
    try {
      return block()
    }
    finally {
      hasInitialContent = false
      if (storage !== EntityStorageSnapshot.empty()) {
        System.setProperty(PlatformUtils.PLATFORM_PREFIX_KEY, previousPropertyValue)
      }
      if (initialContent.getAndSet(null) != null) {
        error("Initial content was not used")
      }
    }
  }
}