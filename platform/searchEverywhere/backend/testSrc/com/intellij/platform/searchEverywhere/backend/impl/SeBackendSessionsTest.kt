// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.platform.searchEverywhere.SeSession
import com.intellij.platform.searchEverywhere.SeSessionImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SeBackendSessionsTest {
  private val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val closedSessions = Channel<SeSession>(Channel.UNLIMITED)
  private val sessions = SeBackendSessions { closedSessions.trySend(it) }

  private val session = SeSessionImpl("session")
  private val otherSession = SeSessionImpl("other session")

  @AfterEach
  fun tearDown() {
    parentScope.cancel()
  }

  @Test
  fun secondOpenKeepsTheSameSession(): Unit = runBlocking {
    assertTrue(sessions.openIfNotClosed(session, parentScope))
    assertTrue(sessions.openIfNotClosed(session, parentScope))
    assertTrue(sessions.isOpen(session))

    sessions.close(session)

    assertEquals(session, receiveClosed())
    assertNoMoreClosed()
  }

  @Test
  fun closeRunsTheCallbackOnce(): Unit = runBlocking {
    sessions.openIfNotClosed(session, parentScope)

    sessions.close(session)
    sessions.close(session)

    assertFalse(sessions.isOpen(session))
    assertTrue(sessions.isClosed(session))
    assertEquals(session, receiveClosed())
    assertNoMoreClosed()
  }

  @Test
  fun closedSessionDoesNotOpenAgain(): Unit = runBlocking {
    sessions.openIfNotClosed(session, parentScope)
    sessions.close(session)
    // Open again before the cleanup of the closed session runs.
    assertFalse(sessions.openIfNotClosed(session, parentScope))
    assertEquals(session, receiveClosed())

    assertFalse(sessions.openIfNotClosed(session, parentScope))
    assertFalse(sessions.isOpen(session))
    assertNoMoreClosed()
  }

  @Test
  fun closeBeforeTheOpenForbidsTheOpen(): Unit = runBlocking {
    sessions.close(session)

    assertTrue(sessions.isClosed(session))
    assertFalse(sessions.openIfNotClosed(session, parentScope))
    assertFalse(sessions.isOpen(session))
    assertNoMoreClosed()
  }

  @Test
  fun clientScopeEndForbidsTheOpen(): Unit = runBlocking {
    val scope = clientScope()
    sessions.openIfNotClosed(session, scope)

    scope.cancel()

    assertEquals(session, receiveClosed())
    assertTrue(sessions.isClosed(session))
    assertFalse(sessions.openIfNotClosed(session, parentScope))
  }

  @Test
  fun oldestClosedSessionCanOpenAgain(): Unit = runBlocking {
    val sessions = SeBackendSessions(closedSessionLimit = 1) { closedSessions.trySend(it) }

    sessions.close(session)
    sessions.close(otherSession)

    assertFalse(sessions.isClosed(session))
    assertTrue(sessions.isClosed(otherSession))
    assertTrue(sessions.openIfNotClosed(session, parentScope))
  }

  @Test
  fun parentScopeEndClosesAllSessions(): Unit = runBlocking {
    sessions.openIfNotClosed(session, parentScope)
    sessions.openIfNotClosed(otherSession, parentScope)

    parentScope.cancel()

    assertEquals(setOf(session, otherSession), setOf(receiveClosed(), receiveClosed()))
    assertFalse(sessions.isOpen(session))
    assertFalse(sessions.isOpen(otherSession))
    assertNoMoreClosed()
  }

  @Test
  fun clientScopeEndClosesOnlyItsSessions(): Unit = runBlocking {
    val firstScope = clientScope()
    val secondScope = clientScope()
    sessions.openIfNotClosed(session, firstScope)
    sessions.openIfNotClosed(otherSession, secondScope)

    firstScope.cancel()

    assertEquals(session, receiveClosed())
    assertNoMoreClosed()
    assertFalse(sessions.isOpen(session))
    assertTrue(sessions.isOpen(otherSession))
  }

  @Test
  fun openInEndedClientScopeClosesTheSession(): Unit = runBlocking {
    val scope = clientScope()
    scope.cancel()

    sessions.openIfNotClosed(session, scope)

    assertEquals(session, receiveClosed())
    assertFalse(sessions.isOpen(session))
    assertTrue(sessions.isClosed(session))
  }

  /** Returns a stand-in for the scope of the client session service. */
  private fun clientScope(): CoroutineScope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext.job))

  private suspend fun receiveClosed(): SeSession = withTimeout(10.seconds) { closedSessions.receive() }

  private suspend fun assertNoMoreClosed() {
    assertNull(withTimeoutOrNull(200.milliseconds) { closedSessions.receive() })
  }
}
