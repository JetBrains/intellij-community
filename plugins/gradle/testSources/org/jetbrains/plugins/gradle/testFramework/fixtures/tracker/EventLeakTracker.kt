// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.tracker

import com.intellij.testFramework.fixtures.IdeaTestFixture
import org.junit.jupiter.api.Assertions
import java.util.concurrent.atomic.AtomicBoolean

class EventLeakTracker(
  private val name: String
) : IdeaTestFixture {

  private lateinit var isAllowed: AtomicBoolean

  override fun setUp() {
    isAllowed = AtomicBoolean(false)
  }

  override fun tearDown() {
    Assertions.assertFalse(isAllowed.get()) {
      "Operation $name should be completed before tearDown()"
    }
  }

  fun assertEventIsAllowed(eventName: String) {
    Assertions.assertTrue(isAllowed.get()) {
      "Unexpected operation $name event $eventName"
    }
  }

  fun <R> withAllowedOperationEvents(action: () -> R): R {
    return withAllowedOperationEventsImpl { action() }
  }

  suspend fun <R> withAllowedOperationEventsAsync(action: suspend () -> R): R {
    return withAllowedOperationEventsImpl { action() }
  }

  private inline fun <R> withAllowedOperationEventsImpl(
    action: () -> R
  ): R {
    isAllowed.set(true)
    try {
      return action()
    }
    finally {
      isAllowed.set(false)
    }
  }
}