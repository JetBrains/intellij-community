// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.ClientIdContextElement
import com.intellij.codeWithMe.currentThreadClientId
import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.installThreadContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.client.ClientAppSession
import com.intellij.openapi.client.ClientAppSessionImpl
import com.intellij.openapi.client.ClientSessionsManager
import com.intellij.openapi.client.ClientType
import com.intellij.openapi.components.service
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.Alarm
import com.intellij.util.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

private val TEST_CLIENT_ID = ClientId("OurTestClientId")

class ClientIdPropagationTest : LightPlatformTestCase() {

  fun testSwingInvokeLater() = doTest {
    SwingUtilities.invokeLater(::doAction)
  }

  fun testApplicationInvokeLater() = doTest {
    application.invokeLater(::doAction)
  }

  fun testAlarm() = doTest {
    Alarm(testRootDisposable).addRequest(::doAction, 10)
  }

  fun testAppExecutorService() = doTest {
    AppExecutorUtil.getAppExecutorService().submit(::doAction)
  }

  fun testChildCoroutine() = doTest {
    runBlocking(ClientId.coroutineContext()) {
      launch(Dispatchers.EDT) {
        doAction()
      }
    }
  }

  fun testChildCoroutineViaThreadContext() = doTest {
    runBlocking(currentThreadContext()) {
      launch(Dispatchers.EDT) {
        doAction()
      }
    }
  }


  private val resultClientId = CompletableFuture< ClientId?>()

  private fun doAction() {
    resultClientId.complete(currentThreadClientId)
  }

  private fun doTest(testRunnable: Runnable) {
    service<ClientSessionsManager<ClientAppSession>>().registerSession(testRootDisposable,
                                                                       TestClientAppSession(application as ApplicationImpl))
    installThreadContext(ClientIdContextElement(TEST_CLIENT_ID)).use {
      testRunnable.run()
    }

    val clientId = resultClientId.get(10, TimeUnit.SECONDS)
    check(clientId == TEST_CLIENT_ID) { "Unexpected clientId value: $clientId" }
  }

  override fun runInDispatchThread() = false

  class TestClientAppSession(application: ApplicationImpl)
    : ClientAppSessionImpl(TEST_CLIENT_ID, ClientType.CONTROLLER, application) {
    override val name: String
      get() = "TestAppSession"
  }
}
