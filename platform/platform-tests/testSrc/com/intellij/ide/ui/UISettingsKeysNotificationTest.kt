// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.ui.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds

/** Checks that a change of a key of `UISettingsKeys` reaches each [UISettingsListener]. */
@TestApplication
class UISettingsKeysNotificationTest {
  @BeforeEach
  @AfterEach
  fun dropStoredSettings(): Unit = timeoutRunBlocking(60.seconds) {
    dropStoredUISettings()
  }

  @Test
  fun `a change of a key fires the UI settings listeners once on the EDT`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking(60.seconds) {
    val eventsOnEdt = eventsOf(disposable) { writeUISettingsKey(UISettingsKeyIdentities.SINGLE_STRIPE, true) }

    assertEquals(listOf(true), eventsOnEdt)
  }

  @Test
  fun `a second initialized instance of the UI settings adds no event`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking(60.seconds) {
    UISettings().initializeComponent()

    val eventsOnEdt = eventsOf(disposable) { writeUISettingsKey(UISettingsKeyIdentities.SINGLE_STRIPE, true) }

    assertEquals(listOf(true), eventsOnEdt)
  }

  /** Runs [change] and returns, for each UI settings event that follows it, whether the event came on the EDT. */
  private suspend fun eventsOf(disposable: Disposable, change: suspend () -> Unit): List<Boolean> {
    UISettings.getInstance()
    // Lets the events of the reload of the setup pass before the count starts.
    withContext(Dispatchers.EDT) {}
    val eventsOnEdt = CopyOnWriteArrayList<Boolean>()
    ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(UISettingsListener.TOPIC, UISettingsListener {
      eventsOnEdt.add(EDT.isCurrentThreadEdt())
    })

    change()

    waitUntil("a listener gets the change") { eventsOnEdt.isNotEmpty() }
    withContext(Dispatchers.EDT) {}
    return eventsOnEdt.toList()
  }
}
