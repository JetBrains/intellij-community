// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

/** `Dispatchers.EDT` and not `Dispatchers.UI`, because [UIUtil.dispatchAllInvocationEvents] takes the write-intent lock. */
@TestApplication
internal class RemoteDesktopServiceTest {

  @Test
  fun `subscribe calls the listener at once on the notification thread`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val parent = Disposer.newDisposable()
      try {
        val seen = subscribe(parent)
        assertEquals(listOf(RemoteDesktopService.isRemoteSession()), seen)
      } finally {
        Disposer.dispose(parent)
      }
    }
  }

  @Test
  fun `subscribe posts the first call for a background caller`(): Unit = timeoutRunBlocking {
    val parent = Disposer.newDisposable()
    try {
      val seen = subscribe(parent)
      withContext(Dispatchers.EDT) {
        UIUtil.dispatchAllInvocationEvents()
      }
      assertEquals(listOf(RemoteDesktopService.isRemoteSession()), seen)
    } finally {
      Disposer.dispose(parent)
    }
  }

  @Test
  fun `a subscriber runs once per change`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val parent = Disposer.newDisposable()
      try {
        val seen = subscribe(parent)
        seen.clear() // drop the first call
        RemoteDesktopService.fireRemoteSessionChanged()
        RemoteDesktopService.fireRemoteSessionChanged()
        UIUtil.dispatchAllInvocationEvents()
        val current = RemoteDesktopService.isRemoteSession()
        assertEquals(listOf(current, current), seen)
      } finally {
        Disposer.dispose(parent)
      }
    }
  }

  @Test
  fun `disposing the parent ends the subscription`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val parent = Disposer.newDisposable()
      val seen = subscribe(parent)
      seen.clear() // drop the first call
      Disposer.dispose(parent)
      RemoteDesktopService.fireRemoteSessionChanged()
      UIUtil.dispatchAllInvocationEvents()
      assertEquals(emptyList(), seen)
    }
  }

  @Test
  fun `disposing the parent suppresses a posted first call`() {
    val parent = Disposer.newDisposable()
    val seen = mutableListOf<Boolean>()
    // blocks the notification thread, so the posted first call cannot run before the dispose
    runInEdtAndWait {
      ApplicationManager.getApplication()
        .executeOnPooledThread {
          RemoteDesktopService.subscribe(parent) {
            seen.add(RemoteDesktopService.isRemoteSession())
          }
        }.get(1, TimeUnit.MINUTES)
      Disposer.dispose(parent)
      UIUtil.dispatchAllInvocationEvents()
    }
    assertEquals(emptyList(), seen)
  }

  /** @return what the listener read on each call, in order */
  private fun subscribe(parent: Disposable): MutableList<Boolean> {
    val seen = mutableListOf<Boolean>()
    RemoteDesktopService.subscribe(parent) {
      seen.add(RemoteDesktopService.isRemoteSession())
    }
    return seen
  }
}
