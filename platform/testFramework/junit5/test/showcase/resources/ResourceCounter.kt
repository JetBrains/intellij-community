// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase.resources

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals

/**
 * Class that helps to make sure all resources are freed at the end.
 * Most resources are checked by [com.intellij.testFramework.junit5.impl.TestApplicationLeakTrackerExtension]
 * but not all
 */
internal class ResourceCounter {
  private var resourcesAcquired = 0

  @Synchronized
  fun acquire(disposable: Disposable? = null) {
    resourcesAcquired += 1

    if (disposable != null) {
      Disposer.register(disposable, Disposable {
        thisLogger().info("It must be disposed. Otherwise, leak would be reported")
        release()
      })
    }
  }


  @Synchronized
  fun release() {
    assert(resourcesAcquired > 0) {
      "Underflow"
    }
    resourcesAcquired -= 1
  }

  @Synchronized
  fun ensureEmpty() {
    assertEquals(0, resourcesAcquired, "No dispose called after tests")
  }

  @Synchronized
  fun ensureNotEmpty() {
    assertNotEquals(0, resourcesAcquired, "Dispose called too early")
  }
}