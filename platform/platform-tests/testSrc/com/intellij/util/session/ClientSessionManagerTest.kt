// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.session

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.client.*
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.application
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

@TestApplication
@RunInEdt(writeIntent = true)
class ClientSessionManagerTest {
  private class TestSession(override val name: String, clientId: ClientId, application: ApplicationImpl) : ClientAppSessionImpl(clientId, ClientType.CONTROLLER, application) {
  }

  private fun createNewSession(name: String, clientId: ClientId) = TestSession(name, clientId, application as ApplicationImpl)

  @Test
  fun `during registration of a new session, the previous one is disposed`(@TestDisposable disposable: Disposable) {
    val manager = service<ClientSessionsManager<ClientAppSession>>()
    val clientId = ClientId("testClientId")
    val session = createNewSession("first", clientId)
    manager.registerSession(disposable, session)

    assertContains(application.sessions(ClientKind.CONTROLLER), session, "First session is registered")
    assertTrue("Session is not disposed after registration") { !session.isDisposed }

    val newSession = createNewSession("second", clientId)
    manager.registerSession(disposable, newSession)

    assertContains(application.sessions(ClientKind.CONTROLLER), newSession, "Second session is registered")
    assertTrue("Old session is not available through `application.sessions`") { application.sessions(ClientKind.CONTROLLER).all { it != session } }
    assertTrue("New session is not disposed after registration") { !newSession.isDisposed }
    assertTrue("Old session is disposed after registration") { session.isDisposed }

    Disposer.dispose(newSession)
  }
}