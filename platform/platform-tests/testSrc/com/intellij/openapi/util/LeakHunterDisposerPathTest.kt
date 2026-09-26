// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.function.Supplier
import kotlin.test.assertTrue

@TestApplication
class LeakHunterDisposerPathTest {
  @Test
  fun `leak via ObjectNode myObject prints parent chain to ROOT and disappears after dispose`() {
    val rootSupplier: Supplier<Map<Any, String>> = Supplier {
      mapOf(Disposer.getTree() to "Disposer.getTree()")
    }

    val parent = LeakParentDisposable()
    Disposer.register(ApplicationManager.getApplication(), parent)
    try {
      val holder = LeakHolderDisposable(LeakVictim())
      Disposer.register(parent, holder)

      val error = assertThrows<AssertionError> {
        LeakHunter.checkLeak(rootSupplier, LeakVictim::class.java) { true }
      }
      val message = error.message ?: ""

      assertTrue(message.contains("Existing strong reference path to the instance:"),
                 "Original backLink block missing from message:\n$message")
      assertTrue(message.contains("reachable via a Disposable in the Disposer tree"),
                 "Disposer anchor explanation missing from message:\n$message")
      assertTrue(message.contains("Path from that Disposable up to the Disposer ROOT:"),
                 "Disposer path header missing from message:\n$message")
      assertTrue(message.contains(LeakHolderDisposable::class.java.name),
                 "Anchor Disposable class missing from Disposer path:\n$message")
      assertTrue(message.contains(LeakParentDisposable::class.java.name),
                 "Parent Disposable class missing from Disposer path:\n$message")
      assertTrue(message.contains("<- ROOT"),
                 "ROOT terminator missing from Disposer path:\n$message")
    }
    finally {
      Disposer.dispose(parent)
    }

    assertDoesNotThrow {
      LeakHunter.checkLeak(rootSupplier, LeakVictim::class.java) { true }
    }
  }
}

private class LeakVictim

private class LeakHolderDisposable(@Suppress("unused") val victim: LeakVictim) : Disposable {
  override fun dispose() {}
}

private class LeakParentDisposable : Disposable {
  override fun dispose() {}
}
