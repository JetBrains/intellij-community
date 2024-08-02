// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.codeWithMe.ClientId
import com.intellij.concurrency.client.ClientIdStringContextElement
import com.intellij.concurrency.client.currentThreadClientIdString
import com.intellij.concurrency.installThreadContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.client.ClientAppSession
import com.intellij.openapi.client.ClientAppSessionImpl
import com.intellij.openapi.client.ClientSessionsManager
import com.intellij.openapi.client.ClientType
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.prepareThreadContext
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.Alarm
import com.intellij.util.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

private const val TEST_CLIENT_ID = "OurTestClientId"

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
    prepareThreadContext { ctx ->
      runBlocking(ctx) {
        launch(Dispatchers.EDT) {
          doAction()
        }
      }
    }
  }


  private val resultClientId = CompletableFuture<String?>()

  private fun doAction() {
    resultClientId.complete(currentThreadClientIdString)
  }

  private fun doTest(testRunnable: Runnable) {
    service<ClientSessionsManager<ClientAppSession>>().registerSession(testRootDisposable,
                                                                       TestClientAppSession(application as ApplicationImpl))
    val oldPropagate = ClientId.propagateAcrossThreads
    ClientId.propagateAcrossThreads = true
    installThreadContext(ClientIdStringContextElement(TEST_CLIENT_ID)).use {
      testRunnable.run()
    }
    ClientId.propagateAcrossThreads = oldPropagate

    val clientId = resultClientId.get(10, TimeUnit.SECONDS)
    check(clientId == TEST_CLIENT_ID) { "Unexpected clientId value: $clientId" }
  }

  override fun runInDispatchThread() = false

  class TestClientAppSession(application: ApplicationImpl)
    : ClientAppSessionImpl(ClientId(TEST_CLIENT_ID), ClientType.CONTROLLER, application) {
    override val name: String
      get() = "TestAppSession"
  }
}
